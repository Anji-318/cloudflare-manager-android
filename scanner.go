package scanner

import (
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"math/rand"
	"net"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/net/idna"
	"golang.org/x/time/rate"
)

// coloRegex 复用正则，避免每次验证都重新编译
var coloRegex = regexp.MustCompile(`colo=([A-Z0-9]+)`)

// Target 目标 - 导出给 gomobile
type Target struct {
	IP       string
	Port     int64
	IsDomain bool
	IsIPv6   bool
}

// Result 结果
type Result struct {
	IP            string
	Port          int64
	DataCenter    string
	Latency       int64
	DownloadSpeed float64
	Region        string
	CountryCode   string
	CountryName   string
	City          string
	TLS           bool
}

// SpeedTestResult 下载测速结果（速度 + 连接延迟）
// 导出给 gomobile，确保下载阶段的延迟数据可被 Java/Kotlin 层读取
type SpeedTestResult struct {
	Speed   float64
	Latency int64
}

// ProgressCallback 回调接口
type ProgressCallback interface {
	OnProgress(progress float64)
	OnLog(message string)
	OnValidIPFound(ip string, port int64, dataCenter string, latency int64, region, countryCode, countryName, city string, tls bool)
	OnSpeedTestComplete(ip string, port int64, speed float64, latency int64)
}

// Scanner 扫描器 - 必须大写导出
type Scanner struct {
	speedTestURL string
	tcpUrl       string
	timeout      int64
	delayWorkers int64
	speedWorkers int64
	speedLimit   float64
	enableTLS    bool
	callback     ProgressCallback
	// 【关键修复】用 context.Context 替代裸 channel，避免并发 close 竞态
	ctxAtomic atomic.Value // stores context.Context
	cancel    context.CancelFunc
	targets   []Target
	randomize bool

	// [新增] 扫描速率限制(请求/秒)，0表示不限速
	rateLimit int64
	// 速率限制器：采用 golang.org/x/time/rate 令牌桶，与 cantest_v3.0.go 一致
	rateLimiter *rate.Limiter

	// 日志批处理相关字段
	logBuffer        []string
	logMutex         sync.Mutex
	logBatchSize     int
	logFlushInterval time.Duration
	logTicker        *time.Ticker
	logStopChan      chan struct{}

	// 关键修复：添加IP级别去重机制
	reportedIPs    map[string]bool
	reportedMutex  sync.RWMutex

	// 测速阶段去重
	speedTestedIPs map[string]bool
	speedTestMutex sync.RWMutex

	// 【新增】stage1 验证稳定性配置：多次验证，减少瞬态有效导致的数量差异
	verifyAttempts        int64
	verifySuccessRequired int64
	verifyRetryIntervalMs int64

	// 预下载校验：stage1 延迟验证通过后，下载一个小文件确认节点真正能下载，
	// 避免只能响应 /cdn-cgi/trace 但不能实际下载的节点进入 stage2。
	preDownloadVerify bool
	preDownloadBytes  int64
	// 预下载并发数（用于限制 stage1 中真正测延迟的 10KB 下载并发，降低网络竞争）
	preDownloadWorkers int64

	// 预下载校验统计（用于排查“突然全部无效”）
	preDLTotal      int64
	preDLFail       int64
	preDLFailDial   int64
	preDLFailHTTP   int64
	preDLFailStatus int64
	preDLFailRead   int64

	// 调试日志开关：false 时过滤掉预下载失败原因、测速错误等内部详情，普通日志更干净
	debugLog bool

	// 【关键修复】完成信号通道，用于等待 Go 层 goroutine 完全退出
	done chan struct{}
	
	// ========== 【关键修复】进度上报相关字段 ==========
	// 进度节流：50ms（原来是100ms）
	lastProgressTime int64
	progressMutex    sync.Mutex
	// 分块扫描时的整体进度缩放
	progressOffset int64
	progressScale  int64
	
	// 【新增】阶段2实时字节进度（用于更平滑的进度上报）
	speedTestTotalBytes   int64
	speedTestCurrentBytes int64
	speedBytesMutex       sync.Mutex
	
	// 【新增】阶段2预估总字节数（每个目标30MB）
	speedTestBytesPerTarget int64
}

// NewScanner 创建扫描器 - 必须大写导出
// [修改] 添加 rateLimit 参数，兼容前端新接口
func NewScanner(speedTestURL string, tcpUrl string, timeout int64, delayWorkers int64, speedWorkers int64, speedLimit float64, enableTLS bool, rateLimit int64) *Scanner {
	ctx, cancel := context.WithCancel(context.Background())
	s := &Scanner{
		speedTestURL: speedTestURL,
		tcpUrl:       tcpUrl,
		timeout:      timeout,
		delayWorkers: delayWorkers,
		speedWorkers: speedWorkers,
		speedLimit:   speedLimit,
		enableTLS:    enableTLS,
		cancel:       cancel,
		targets:      make([]Target, 0),
		randomize:    false,
		rateLimit:          rateLimit,
		rateLimiter:        rate.NewLimiter(rate.Inf, 1),
		logBuffer:          make([]string, 0, 100),
		logBatchSize:       10,
		logFlushInterval:   100 * time.Millisecond,
		logStopChan:        make(chan struct{}),
		reportedIPs:        make(map[string]bool),
		speedTestedIPs:     make(map[string]bool),
		// 【新增】初始化字节进度字段
		speedTestTotalBytes:     0,
		speedTestCurrentBytes:   0,
		speedTestBytesPerTarget: 30 * 1024 * 1024, // 30MB预估
		// 【新增】默认 stage1 验证 2 次，2 次都通过才算有效，降低瞬态有效导致的二次扫描数量差异
		verifyAttempts:        2,
		verifySuccessRequired: 2,
		verifyRetryIntervalMs: 100,
		// 默认关闭预下载校验，由 Kotlin 层在测速模式时开启
		preDownloadVerify: false,
		preDownloadBytes:  10 * 1024,
	}

	// 预下载并发默认与测速并发一致（测速并发为 0 时默认 10），让 stage1 预检延迟更接近 stage2
	if speedWorkers > 0 {
		s.preDownloadWorkers = speedWorkers
	} else {
		s.preDownloadWorkers = 10
	}

	s.ctxAtomic.Store(ctx)

	// 初始化令牌桶限速器：burst=rateLimit，与 cantest_v3.0.go 行为一致
	s.resetRateLimiter()

	s.startLogFlusher()
	return s
}

// resetRateLimiter 重置/初始化令牌桶限速器
func (s *Scanner) resetRateLimiter() {
	if s.rateLimit > 0 {
		burst := int(s.rateLimit)
		if burst < 1 {
			burst = 1
		}
		s.rateLimiter = rate.NewLimiter(rate.Limit(s.rateLimit), burst)
	} else {
		s.rateLimiter = rate.NewLimiter(rate.Inf, 1)
	}
}

// 获取速率限制许可（令牌桶，与 cantest_v3.0.go 一致）
// 返回 false 表示 context 已取消，调用方应直接放弃本次目标
func (s *Scanner) acquireRatePermit() bool {
	if s.rateLimit <= 0 || s.rateLimiter == nil {
		return true // 不限速
	}
	// 使用 context 支持取消；Wait 内部使用令牌桶算法，burst=rateLimit
	if err := s.rateLimiter.Wait(s.getCtx()); err != nil {
		return false
	}
	return true
}

// 检查IP是否已报告（线程安全）
func (s *Scanner) isIPReported(key string) bool {
	s.reportedMutex.RLock()
	defer s.reportedMutex.RUnlock()
	return s.reportedIPs[key]
}

// 标记IP已报告（线程安全）
func (s *Scanner) markIPReported(key string) {
	s.reportedMutex.Lock()
	defer s.reportedMutex.Unlock()
	s.reportedIPs[key] = true
}

// 检查测速是否已报告（线程安全）
func (s *Scanner) isSpeedTested(key string) bool {
	s.speedTestMutex.RLock()
	defer s.speedTestMutex.RUnlock()
	return s.speedTestedIPs[key]
}

// 标记测速已报告（线程安全）
func (s *Scanner) markSpeedTested(key string) {
	s.speedTestMutex.Lock()
	defer s.speedTestMutex.Unlock()
	s.speedTestedIPs[key] = true
}

// 重置去重状态（用于新一轮扫描）
func (s *Scanner) resetDeduplication() {
	s.reportedMutex.Lock()
	s.reportedIPs = make(map[string]bool)
	s.reportedMutex.Unlock()

	s.speedTestMutex.Lock()
	s.speedTestedIPs = make(map[string]bool)
	s.speedTestMutex.Unlock()

	// 重置令牌桶限速器，确保新一轮扫描从满桶开始
	s.resetRateLimiter()
	
	// 【新增】重置字节进度
	s.speedBytesMutex.Lock()
	s.speedTestTotalBytes = 0
	s.speedTestCurrentBytes = 0
	s.speedBytesMutex.Unlock()

	// 重置预下载校验统计
	atomic.StoreInt64(&s.preDLTotal, 0)
	atomic.StoreInt64(&s.preDLFail, 0)
	atomic.StoreInt64(&s.preDLFailDial, 0)
	atomic.StoreInt64(&s.preDLFailHTTP, 0)
	atomic.StoreInt64(&s.preDLFailStatus, 0)
	atomic.StoreInt64(&s.preDLFailRead, 0)
}

// ========== 【关键修复】reportProgress：50ms节流 + 阶段2按字节进度 ==========
func (s *Scanner) reportProgress(completed, total int64) {
	if s.callback == nil || total == 0 {
		return
	}

	now := time.Now().UnixMilli()

	s.progressMutex.Lock()
	// 节流放宽到50ms，但强制每3秒至少上报一次（防止长时间卡住）
	if now-s.lastProgressTime < 50 && (now-s.lastProgressTime < 3000 || completed < total) {
		s.progressMutex.Unlock()
		return
	}
	s.lastProgressTime = now
	s.progressMutex.Unlock()

	var rawProgress float64
	if s.delayWorkers > 0 && s.speedWorkers == 0 {
		// 阶段1：延迟测试，按目标数
		rawProgress = float64(completed) / float64(total)
	} else if s.speedWorkers > 0 && s.delayWorkers == 0 {
		// 阶段2：下载测速，优先按字节数（更平滑）
		s.speedBytesMutex.Lock()
		if s.speedTestTotalBytes > 0 {
			rawProgress = float64(s.speedTestCurrentBytes) / float64(s.speedTestTotalBytes)
			// 保底：至少按完成数算，防止字节数没更新时卡住
			countProgress := float64(completed) / float64(total)
			if countProgress > rawProgress {
				rawProgress = countProgress
			}
		} else {
			rawProgress = float64(completed) / float64(total)
		}
		s.speedBytesMutex.Unlock()
	} else {
		rawProgress = float64(completed) / float64(total)
	}

	// 分块扫描时缩放到整体进度
	var progress float64
	if s.progressScale > 0 {
		progress = (float64(s.progressOffset) + rawProgress*float64(total)) / float64(s.progressScale)
	} else {
		progress = rawProgress
	}

	// 限制进度不超过0.999，最后由完成时强制设为1.0
	if progress > 0.999 {
		progress = 0.999
	}

	s.callback.OnProgress(progress)
}

// 【新增】阶段2专用：上报下载字节数
func (s *Scanner) reportSpeedBytes(downloadedBytes, totalBytes int64) {
	s.speedBytesMutex.Lock()
	s.speedTestCurrentBytes = downloadedBytes
	if totalBytes > s.speedTestTotalBytes {
		s.speedTestTotalBytes = totalBytes
	}
	s.speedBytesMutex.Unlock()
}

// 启动日志刷新定时器
func (s *Scanner) startLogFlusher() {
	s.logTicker = time.NewTicker(s.logFlushInterval)
	go func() {
		for {
			select {
			case <-s.logTicker.C:
				s.flushLogs()
			case <-s.logStopChan:
				s.logTicker.Stop()
				return
			}
		}
	}()
}

// 停止日志刷新
func (s *Scanner) stopLogFlusher() {
	select {
	case <-s.logStopChan:
		return
	default:
		close(s.logStopChan)
	}
	s.flushLogs()
}

// 批量刷新日志到UI
func (s *Scanner) flushLogs() {
	s.logMutex.Lock()
	if len(s.logBuffer) == 0 {
		s.logMutex.Unlock()
		return
	}

	logsToSend := make([]string, len(s.logBuffer))
	copy(logsToSend, s.logBuffer)
	s.logBuffer = s.logBuffer[:0]
	s.logMutex.Unlock()

	if s.callback != nil && len(logsToSend) > 0 {
		for _, msg := range logsToSend {
			s.callback.OnLog(msg)
		}
	}
}

// 添加单条日志到缓冲区
func (s *Scanner) addLogToBuffer(message string) {
	s.logMutex.Lock()
	s.logBuffer = append(s.logBuffer, message)
	shouldFlush := len(s.logBuffer) >= s.logBatchSize
	s.logMutex.Unlock()

	if shouldFlush {
		s.flushLogs()
	}
}

// SetCallback 设置回调
func (s *Scanner) SetCallback(cb ProgressCallback) {
	s.callback = cb
}

// Cancel 取消扫描
func (s *Scanner) Cancel() {
	s.cancelOnce()

	modeText := "任务"
	if s.delayWorkers > 0 && s.speedWorkers == 0 {
		modeText = "延迟测试"
	} else if s.delayWorkers == 0 && s.speedWorkers > 0 {
		modeText = "下载测速"
	} else if s.delayWorkers > 0 && s.speedWorkers > 0 {
		modeText = "完整测速"
	}
	s.log("正在停止%s...", modeText)
	s.flushLogs()
}

// getCtx 安全读取当前 context
func (s *Scanner) getCtx() context.Context {
	v := s.ctxAtomic.Load()
	if v == nil {
		return context.Background()
	}
	return v.(context.Context)
}

// cancelOnce 安全地触发取消
func (s *Scanner) cancelOnce() {
	if s.cancel != nil {
		s.cancel()
	}
}

// AddTarget 添加单个目标
func (s *Scanner) AddTarget(ip string, port int64, isDomain bool, isIPv6 bool) {
	s.targets = append(s.targets, Target{
		IP:       ip,
		Port:     port,
		IsDomain: isDomain,
		IsIPv6:   isIPv6,
	})
}

// ClearTargets 清空目标列表
func (s *Scanner) ClearTargets() {
	s.stopLogFlusher()
	s.cancelOnce()
	time.Sleep(100 * time.Millisecond)

	s.targets = make([]Target, 0)
	// 【关键修复】重建 context，避免旧 context 已取消后无法启动新一轮扫描
	ctx, cancel := context.WithCancel(context.Background())
	s.ctxAtomic.Store(ctx)
	s.cancel = cancel
	s.logBuffer = make([]string, 0, 100)
	s.logStopChan = make(chan struct{})
	s.startLogFlusher()
	s.resetDeduplication()
	s.lastProgressTime = 0
}

// SetRandomize 设置是否随机打乱
func (s *Scanner) SetRandomize(randomize bool) {
	s.randomize = randomize
}

// SetVerifyAttempts 设置 stage1 验证尝试次数
func (s *Scanner) SetVerifyAttempts(attempts int64) {
	if attempts < 1 {
		attempts = 1
	}
	s.verifyAttempts = attempts
}

// SetVerifySuccessRequired 设置 stage1 验证需要成功次数
func (s *Scanner) SetVerifySuccessRequired(required int64) {
	if required < 1 {
		required = 1
	}
	s.verifySuccessRequired = required
}

// SetVerifyRetryIntervalMs 设置 stage1 多次验证之间的重试间隔（毫秒）
func (s *Scanner) SetVerifyRetryIntervalMs(intervalMs int64) {
	if intervalMs < 0 {
		intervalMs = 0
	}
	s.verifyRetryIntervalMs = intervalMs
}

// SetDebugLog 设置是否输出调试级日志（如预下载失败原因、测速错误详情）
func (s *Scanner) SetDebugLog(enabled bool) {
	s.debugLog = enabled
}

// SetPreDownloadVerify 设置是否在 stage1 延迟验证通过后进行小文件预下载校验
func (s *Scanner) SetPreDownloadVerify(enabled bool) {
	s.preDownloadVerify = enabled
}

// SetPreDownloadBytes 设置预下载校验的字节数（默认 10240 = 10KB）
func (s *Scanner) SetPreDownloadBytes(bytes int64) {
	if bytes < 1024 {
		bytes = 1024
	}
	if bytes > 1024*1024 {
		bytes = 1024 * 1024
	}
	s.preDownloadBytes = bytes
}

// SetPreDownloadWorkers 设置 stage1 中 10KB 预下载校验的并发数。
// 低并发能降低网络竞争，使预检延迟更接近 stage2 实测延迟；
// 0 表示使用默认值（speedWorkers 或 10）。
func (s *Scanner) SetPreDownloadWorkers(workers int64) {
	if workers < 0 {
		workers = 0
	}
	s.preDownloadWorkers = workers
}

// GetTargetCount 获取目标数量
func (s *Scanner) GetTargetCount() int {
	return len(s.targets)
}

// IsRunning 检查扫描是否仍在运行
func (s *Scanner) IsRunning() bool {
	select {
	case <-s.getCtx().Done():
		return false
	default:
		return true
	}
}

// WaitForStop 等待扫描完全停止，超时返回 false（单位：毫秒）
func (s *Scanner) WaitForStop(timeoutMs int64) bool {
	if s.done == nil {
		return true
	}
	select {
	case <-s.done:
		return true
	case <-time.After(time.Duration(timeoutMs) * time.Millisecond):
		return false
	}
}

// StartBatchTest 开始批量测试
func (s *Scanner) StartBatchTest() {
	s.resetDeduplication()
	// 【关键修复】创建完成信号通道，用于外部等待 Go 层 goroutine 退出
	s.done = make(chan struct{})

	go func() {
		defer close(s.done)
		if len(s.targets) == 0 {
			s.log("批量测试：目标列表为空")
			return
		}

		targets := make([]Target, len(s.targets))
		copy(targets, s.targets)

		if s.randomize {
			modeText := "测速"
			if s.speedWorkers == 0 && s.delayWorkers > 0 {
				modeText = "延迟测试"
			} else if s.speedWorkers > 0 && s.delayWorkers == 0 {
				modeText = "下载测速"
			} else if s.speedWorkers > 0 && s.delayWorkers > 0 {
				modeText = "完整测速"
			}
			rand.Shuffle(len(targets), func(i, j int) {
				targets[i], targets[j] = targets[j], targets[i]
			})
			s.log("已随机打乱 %d 个目标的%s顺序", len(targets), modeText)
		}

		if s.speedWorkers == 0 {
			if s.rateLimit > 0 {
				s.log("开始批量扫描：%d 个目标，延迟并发 %d，速率限制 %d/秒",
					len(targets), s.delayWorkers, s.rateLimit)
			} else {
				s.log("开始批量扫描：%d 个目标，延迟并发 %d",
					len(targets), s.delayWorkers)
			}
		} else {
			if s.rateLimit > 0 {
				s.log("开始批量测速：%d 个目标，延迟并发 %d，下载测速并发 %d，速率限制 %d/秒",
					len(targets), s.delayWorkers, s.speedWorkers, s.rateLimit)
			} else {
				s.log("开始批量测速：%d 个目标，延迟并发 %d，下载测速并发 %d",
					len(targets), s.delayWorkers, s.speedWorkers)
			}
		}

		s.testSpeedInternal(targets)
	}()
}

// ProcessBatch 同步处理已添加的目标并返回有效结果数，用于千万级目标分块扫描
// offset/total 用于把本批进度 0.0~1.0 缩放到整体进度
// 目标需事先通过 AddTarget 添加；调用后内部目标列表会被清空
func (s *Scanner) ProcessBatch(offset, total int64) int64 {
	if len(s.targets) == 0 {
		return 0
	}

	targets := make([]Target, len(s.targets))
	copy(targets, s.targets)
	s.targets = s.targets[:0]

	s.progressMutex.Lock()
	s.progressOffset = offset
	s.progressScale = total
	s.progressMutex.Unlock()
	defer func() {
		s.progressMutex.Lock()
		s.progressOffset = 0
		s.progressScale = 0
		s.progressMutex.Unlock()
	}()

	if s.randomize {
		rand.Shuffle(len(targets), func(i, j int) {
			targets[i], targets[j] = targets[j], targets[i]
		})
	}

	if s.speedWorkers == 0 {
		if s.rateLimit > 0 {
			s.log("开始批量扫描：%d 个目标，延迟并发 %d，速率限制 %d/秒",
				len(targets), s.delayWorkers, s.rateLimit)
		} else {
			s.log("开始批量扫描：%d 个目标，延迟并发 %d",
				len(targets), s.delayWorkers)
		}
	} else {
		if s.rateLimit > 0 {
			s.log("开始批量测速：%d 个目标，延迟并发 %d，下载测速并发 %d，速率限制 %d/秒",
				len(targets), s.delayWorkers, s.speedWorkers, s.rateLimit)
		} else {
			s.log("开始批量测速：%d 个目标，延迟并发 %d，下载测速并发 %d",
				len(targets), s.delayWorkers, s.speedWorkers)
		}
	}

	results := s.testSpeedInternal(targets)
	return int64(len(results))
}

// TriggerTest 触发单个目标测速
func (s *Scanner) TriggerTest(ip string, port int64, isDomain bool, isIPv6 bool) {
	go func() {
		target := Target{
			IP:       ip,
			Port:     port,
			IsDomain: isDomain,
			IsIPv6:   isIPv6,
		}
		s.testSpeedInternal([]Target{target})
	}()
}

// 内部方法
func (s *Scanner) testSpeedInternal(targets []Target) []Result {
	if len(targets) == 0 {
		s.log("批量测试：目标列表为空")
		return nil
	}

	total := int64(len(targets))

	if s.delayWorkers == 0 && s.speedWorkers > 0 {
		s.log("【测速阶段】跳过延迟测试，直接开始下载测速，共 %d 个目标，并发数 %d", total, s.speedWorkers)
		s.log("测速地址: %s", s.speedTestURL)

		validResults := make([]Result, 0, len(targets))
		for _, t := range targets {
			validResults = append(validResults, Result{
				IP:         t.IP,
				Port:       t.Port,
				DataCenter: "",
				Latency:    0,
				TLS:        s.enableTLS,
			})
		}

		validResults = s.stage2DownloadTest(validResults)
		s.log("测速完成，有效结果 %d 个", len(validResults))
		return validResults
	}

	if s.speedWorkers == 0 {
		if s.rateLimit > 0 {
			s.log("【扫描阶段】开始延迟测试，共 %d 个目标，并发数 %d，速率限制 %d/秒", total, s.delayWorkers, s.rateLimit)
		} else {
			s.log("【扫描阶段】开始延迟测试，共 %d 个目标，并发数 %d", total, s.delayWorkers)
		}
	} else {
		if s.rateLimit > 0 {
			s.log("【测速-延迟阶段】开始延迟测试，共 %d 个目标，并发数 %d，速率限制 %d/秒", total, s.delayWorkers, s.rateLimit)
		} else {
			s.log("【测速-延迟阶段】开始延迟测试，共 %d 个目标，并发数 %d", total, s.delayWorkers)
		}
	}

	validResults := s.stage1DelayTest(targets)

	select {
	case <-s.getCtx().Done():
		s.log("扫描已取消")
		return nil
	default:
	}

	if len(validResults) == 0 {
		s.log("没有发现有效 IP")
		return nil
	}

	if s.speedWorkers > 0 {
		s.log("═══════════════════════════════════════")
		s.log("开始下载测速，共 %d 个目标，并发数 %d", len(validResults), s.speedWorkers)
		s.log("测速地址: %s", s.speedTestURL)
		validResults = s.stage2DownloadTest(validResults)
		s.log("测速完成，有效结果 %d 个", len(validResults))
	} else {
		if s.delayWorkers > 0 {
			s.log("【延迟测试完成】当前模式仅测延迟，跳过下载测速")
		} else {
			s.log("【直接测速模式】跳过延迟测试阶段")
		}
	}

	return validResults
}

// testDelayAndVerify 统一测试延迟并验证Cloudflare（支持多次验证）
// 采用不同验证方式交替验证：
//   - 第1次：访问 /cdn-cgi/trace 解析 colo
//   - 第2次：访问 / 根路径并校验 Server/CF-RAY 头
// 不同端点/路径可以降低同一 transient 路径导致的 false positive。
func (s *Scanner) testDelayAndVerify(t Target) (int64, string, bool) {
	// 速率限制按目标计费（与 cantest_v3.0.go 一致），而不是按验证尝试次数
	if !s.acquireRatePermit() {
		return -1, "", false
	}

	attempts := s.verifyAttempts
	if attempts < 1 {
		attempts = 1
	}
	required := s.verifySuccessRequired
	if required < 1 {
		required = 1
	}
	if required > attempts {
		required = attempts
	}

	var lastLatency int64
	var lastDataCenter string
	var successCount int64 = 0

	for i := int64(0); i < attempts; i++ {
		var latency int64
		var dataCenter string
		var ok bool

		// 交替使用不同验证方式：偶数次用 trace，奇数次用根路径
		if i%2 == 0 {
			latency, dataCenter, ok = s.testDelayAndVerifyByTrace(t)
		} else {
			latency, dataCenter, ok = s.testDelayAndVerifyByRoot(t)
		}

		if ok && latency >= 0 && dataCenter != "" {
			successCount++
			lastLatency = latency
			lastDataCenter = dataCenter
			if successCount >= required {
				return lastLatency, lastDataCenter, s.enableTLS
			}
		}
		// 非最后一次失败时，短暂等待后重试
		if i < attempts-1 && s.verifyRetryIntervalMs > 0 {
			time.Sleep(time.Duration(s.verifyRetryIntervalMs) * time.Millisecond)
		}
	}

	return -1, "", false
}

// verifySmallDownload 预下载校验：下载一个极小的文件确认节点能真正提供下载，
// 同时返回本次 TCP 连接延迟。用于 stage1 过滤假有效节点，并给 UI 提供更贴近真实下载路径的延迟。
func (s *Scanner) verifySmallDownload(t Target) (int64, bool) {
	if s.preDownloadBytes <= 0 {
		return 0, true
	}

	atomic.AddInt64(&s.preDLTotal, 1)

	connectTimeout := time.Duration(s.timeout) * time.Millisecond
	if connectTimeout > 3*time.Second {
		connectTimeout = 3 * time.Second
	}

	addr := fmt.Sprintf("%s:%d", t.IP, t.Port)
	if t.IsIPv6 {
		addr = fmt.Sprintf("[%s]:%d", t.IP, t.Port)
	}

	var conn net.Conn
	var err error
	isDomain := net.ParseIP(t.IP) == nil
	dialStart := time.Now()

	if isDomain {
		// 【关键修复】域名直接连接，不解析为IP
		dialer := &net.Dialer{
			Timeout:   connectTimeout,
			KeepAlive: 0,
			Resolver: &net.Resolver{
				PreferGo: true,
				Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
					return nil, fmt.Errorf("DNS lookup disabled for domain testing")
				},
			},
		}
		conn, err = dialer.Dial("tcp", addr)
	} else {
		dialer := &net.Dialer{
			Timeout:   connectTimeout,
			KeepAlive: 0,
		}
		conn, err = dialer.Dial("tcp", addr)
	}

	if err != nil {
		atomic.AddInt64(&s.preDLFail, 1)
		atomic.AddInt64(&s.preDLFailDial, 1)
		s.logPreDownloadFail(t, "dial", err)
		return -1, false
	}
	defer conn.Close()
	latency := time.Since(dialStart).Milliseconds()
	conn.SetDeadline(time.Now().Add(connectTimeout))

	downloadURL := toPunycodeURL(s.speedTestURL)
	if !strings.HasPrefix(downloadURL, "http") {
		if s.enableTLS {
			downloadURL = "https://" + downloadURL
		} else {
			downloadURL = "http://" + downloadURL
		}
	}

	tlsConfig := &tls.Config{
		InsecureSkipVerify: true,
	}
	if net.ParseIP(t.IP) == nil {
		tlsConfig.ServerName = t.IP
	} else {
		if u, err := url.Parse(downloadURL); err == nil && u.Hostname() != "" {
			tlsConfig.ServerName = u.Hostname()
		}
	}

	client := http.Client{
		Transport: &http.Transport{
			Dial: func(network, addr string) (net.Conn, error) {
				return conn, nil
			},
			TLSClientConfig:    tlsConfig,
			DisableCompression: true,
			DisableKeepAlives:  true,
			ForceAttemptHTTP2:  false,
		},
		Timeout: connectTimeout,
	}

	// 对 Cloudflare 测速地址追加 bytes 参数以控制下载大小
	if strings.Contains(downloadURL, "speed.cloudflare.com") && !strings.Contains(downloadURL, "bytes=") {
		if strings.Contains(downloadURL, "?") {
			downloadURL = downloadURL + fmt.Sprintf("&bytes=%d", s.preDownloadBytes)
		} else {
			downloadURL = downloadURL + fmt.Sprintf("?bytes=%d", s.preDownloadBytes)
		}
	}

	req, err := http.NewRequest("GET", downloadURL, nil)
	if err != nil {
		atomic.AddInt64(&s.preDLFail, 1)
		atomic.AddInt64(&s.preDLFailHTTP, 1)
		s.logPreDownloadFail(t, "newRequest", err)
		return latency, false
	}
	req.Header.Set("User-Agent", "Mozilla/5.0")
	req.Close = true

	resp, err := client.Do(req)
	if err != nil {
		atomic.AddInt64(&s.preDLFail, 1)
		atomic.AddInt64(&s.preDLFailHTTP, 1)
		s.logPreDownloadFail(t, "doRequest", err)
		return latency, false
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		atomic.AddInt64(&s.preDLFail, 1)
		atomic.AddInt64(&s.preDLFailStatus, 1)
		s.logPreDownloadFail(t, fmt.Sprintf("status:%d", resp.StatusCode), nil)
		return latency, false
	}

	var totalRead int64
	buf := make([]byte, 4096)
	limit := s.preDownloadBytes
	for totalRead < limit {
		toRead := limit - totalRead
		if toRead > int64(len(buf)) {
			toRead = int64(len(buf))
		}
		n, err := resp.Body.Read(buf[:toRead])
		if n > 0 {
			totalRead += int64(n)
		}
		if err != nil {
			break
		}
	}

	if totalRead <= 0 {
		atomic.AddInt64(&s.preDLFail, 1)
		atomic.AddInt64(&s.preDLFailRead, 1)
		s.logPreDownloadFail(t, "zeroRead", nil)
		return latency, false
	}

	return latency, true
}

// logPreDownloadFail 记录预下载失败详情（前 20 条），便于排查 scheme/SNI/超时问题。
// 仅在 debugLog=true 时输出，避免普通日志刷屏。
func (s *Scanner) logPreDownloadFail(t Target, reason string, err error) {
	if !s.debugLog {
		return
	}
	failCount := atomic.LoadInt64(&s.preDLFail)
	if failCount > 20 {
		return
	}
	msg := fmt.Sprintf("预下载校验失败 %s:%d 原因=%s", t.IP, t.Port, reason)
	if err != nil {
		msg = fmt.Sprintf("%s err=%v", msg, err)
	}
	s.log(msg)
}

// testDelayAndVerifyByTrace 方式一：通过 /cdn-cgi/trace 验证 Cloudflare
func (s *Scanner) testDelayAndVerifyByTrace(t Target) (int64, string, bool) {
	// 连接超时上限 3 秒，避免长时间挂起
	connectTimeout := time.Duration(s.timeout) * time.Millisecond
	if connectTimeout > 3*time.Second {
		connectTimeout = 3 * time.Second
	}

	isDomain := net.ParseIP(t.IP) == nil

	addr := fmt.Sprintf("%s:%d", t.IP, t.Port)
	if t.IsIPv6 {
		addr = fmt.Sprintf("[%s]:%d", t.IP, t.Port)
	}

	var conn net.Conn
	var err error
	start := time.Now()

	if isDomain {
		// 【关键修复】域名直接连接，不解析为IP
		dialer := &net.Dialer{
			Timeout:   connectTimeout,
			KeepAlive: 0,
			Resolver: &net.Resolver{
				PreferGo: true,
				Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
					return nil, fmt.Errorf("DNS lookup disabled for domain testing")
				},
			},
		}
		conn, err = dialer.Dial("tcp", addr)
	} else {
		dialer := &net.Dialer{
			Timeout:   connectTimeout,
			KeepAlive: 0,
		}
		conn, err = dialer.Dial("tcp", addr)
	}

	if err != nil {
		return -1, "", false
	}
	defer conn.Close()

	// 设置整体操作超时
	conn.SetDeadline(time.Now().Add(connectTimeout))

	latency := time.Since(start).Milliseconds()

	tlsConfig := &tls.Config{
		InsecureSkipVerify: true,
	}

	if isDomain {
		tlsConfig.ServerName = t.IP
	} else {
		tlsConfig.ServerName = s.tcpUrl
	}

	var protocol, reqURL string
	if s.enableTLS {
		protocol = "https://"
	} else {
		protocol = "http://"
	}

	if isDomain {
		reqURL = protocol + t.IP
	} else {
		reqURL = protocol + s.tcpUrl
	}

	// 禁用 HTTP/2 和 Keep-Alive
	client := http.Client{
		Transport: &http.Transport{
			DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
				return conn, nil
			},
			TLSClientConfig:   tlsConfig,
			ForceAttemptHTTP2: false,
			DisableKeepAlives: true,
		},
		Timeout: connectTimeout,
	}

	req, err := http.NewRequest("GET", reqURL+"/cdn-cgi/trace", nil)
	if err != nil {
		return -1, "", false
	}
	req.Header.Set("User-Agent", "Mozilla/5.0")
	req.Close = true

	resp, err := client.Do(req)
	if err != nil {
		return -1, "", false
	}
	defer resp.Body.Close()

	server := resp.Header.Get("Server")
	if !strings.Contains(strings.ToLower(server), "cloudflare") {
		return -1, "", false
	}

	// 限制读取 4KB
	body, err := io.ReadAll(io.LimitReader(resp.Body, 4096))
	if err != nil {
		return -1, "", false
	}

	dataCenter := ""
	re := regexp.MustCompile(`colo=([A-Z0-9]+)`)
	matches := re.FindStringSubmatch(string(body))
	if len(matches) > 1 {
		dataCenter = matches[1]
	} else {
		cfRay := resp.Header.Get("CF-RAY")
		if cfRay != "" && strings.Contains(cfRay, "-") {
			parts := strings.Split(cfRay, "-")
			dataCenter = parts[len(parts)-1]
		}
	}
	return latency, dataCenter, s.enableTLS
}

// testDelayAndVerifyByRoot 方式二：通过访问根路径 / 验证 Cloudflare
// 与 trace 端点使用不同 HTTP 路径，降低同一 transient 路径误判概率。
func (s *Scanner) testDelayAndVerifyByRoot(t Target) (int64, string, bool) {
	connectTimeout := time.Duration(s.timeout) * time.Millisecond
	if connectTimeout > 3*time.Second {
		connectTimeout = 3 * time.Second
	}

	isDomain := net.ParseIP(t.IP) == nil

	addr := fmt.Sprintf("%s:%d", t.IP, t.Port)
	if t.IsIPv6 {
		addr = fmt.Sprintf("[%s]:%d", t.IP, t.Port)
	}

	var conn net.Conn
	var err error
	start := time.Now()

	if isDomain {
		// 【关键修复】域名直接连接，不解析为IP
		dialer := &net.Dialer{
			Timeout:   connectTimeout,
			KeepAlive: 0,
			Resolver: &net.Resolver{
				PreferGo: true,
				Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
					return nil, fmt.Errorf("DNS lookup disabled for domain testing")
				},
			},
		}
		conn, err = dialer.Dial("tcp", addr)
	} else {
		dialer := &net.Dialer{
			Timeout:   connectTimeout,
			KeepAlive: 0,
		}
		conn, err = dialer.Dial("tcp", addr)
	}

	if err != nil {
		return -1, "", false
	}
	defer conn.Close()
	conn.SetDeadline(time.Now().Add(connectTimeout))

	latency := time.Since(start).Milliseconds()

	tlsConfig := &tls.Config{
		InsecureSkipVerify: true,
	}
	if isDomain {
		tlsConfig.ServerName = t.IP
	} else {
		tlsConfig.ServerName = s.tcpUrl
	}

	var protocol, reqURL string
	if s.enableTLS {
		protocol = "https://"
	} else {
		protocol = "http://"
	}
	if isDomain {
		reqURL = protocol + t.IP + "/"
	} else {
		reqURL = protocol + s.tcpUrl + "/"
	}

	client := http.Client{
		Transport: &http.Transport{
			DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
				return conn, nil
			},
			TLSClientConfig:   tlsConfig,
			ForceAttemptHTTP2: false,
			DisableKeepAlives: true,
		},
		Timeout: connectTimeout,
	}

	req, err := http.NewRequest("GET", reqURL, nil)
	if err != nil {
		return -1, "", false
	}
	req.Header.Set("User-Agent", "Mozilla/5.0")
	req.Close = true

	resp, err := client.Do(req)
	if err != nil {
		return -1, "", false
	}
	defer resp.Body.Close()

	server := resp.Header.Get("Server")
	if !strings.Contains(strings.ToLower(server), "cloudflare") {
		return -1, "", false
	}

	// 丢弃 body，不读取
	_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 4096))

	dataCenter := ""
	cfRay := resp.Header.Get("CF-RAY")
	if cfRay != "" && strings.Contains(cfRay, "-") {
		parts := strings.Split(cfRay, "-")
		dataCenter = parts[len(parts)-1]
	}
	return latency, dataCenter, s.enableTLS
}

// stage1DelayTest 第一阶段：延迟测试（分批次工作池，每批10万/20万/50万）
func (s *Scanner) stage1DelayTest(targets []Target) []Result {
	// [关键修复] 按批次处理，防止百万级目标一次性压入内存；
	// 千万级目标使用更大批次，减少批次切换开销，进度更顺滑
	batchSize := 100000
	switch {
	case len(targets) > 5000000:
		batchSize = 500000
	case len(targets) > 1000000:
		batchSize = 250000
	}

	var allResults []Result
	total := int64(len(targets))

	for start := 0; start < len(targets); start += batchSize {
		// 检查取消
		select {
		case <-s.getCtx().Done():
			return allResults
		default:
		}

		end := start + batchSize
		if end > len(targets) {
			end = len(targets)
		}

		batch := targets[start:end]
		//s.log("【延迟批次】%d/%d，本批 %d 个目标", start, len(targets), len(batch))

		batchResults := s.stage1DelayTestBatch(batch, int64(start), total)
		allResults = append(allResults, batchResults...)

		// 批次间短暂喘息，保证 GC 与进度条顺滑；20ms 对人眼无感知
		if end < len(targets) {
			time.Sleep(20 * time.Millisecond)
		}
	}

	// 输出预下载校验统计，便于排查“全部无效”（仅调试模式）
	if s.preDownloadVerify && s.debugLog {
		total := atomic.LoadInt64(&s.preDLTotal)
		fail := atomic.LoadInt64(&s.preDLFail)
		failDial := atomic.LoadInt64(&s.preDLFailDial)
		failHTTP := atomic.LoadInt64(&s.preDLFailHTTP)
		failStatus := atomic.LoadInt64(&s.preDLFailStatus)
		failRead := atomic.LoadInt64(&s.preDLFailRead)
		if total > 0 {
			s.log("预下载校验统计: 总计=%d, 失败=%d, 失败[dial=%d http=%d status=%d read=%d]",
				total, fail, failDial, failHTTP, failStatus, failRead)
		}
	}

	// 分块扫描时进度由调用方控制，避免每块结束都跳到 100%
	if s.callback != nil && s.progressScale == 0 {
		if s.speedWorkers > 0 {
			s.callback.OnProgress(0.5)
		} else {
			s.callback.OnProgress(1.0)
		}
	}

	return allResults
}

// stage1DelayTestBatch 单批次延迟测试（工作池模式）
func (s *Scanner) stage1DelayTestBatch(batch []Target, offset, total int64) []Result {
	var wg sync.WaitGroup
	bufSize := int(s.delayWorkers) * 2
	if bufSize < 64 {
		bufSize = 64
	}
	jobs := make(chan Target, bufSize)
	results := make(chan Result, bufSize)

	var completed int64

	workerCount := int(s.delayWorkers)
	if workerCount < 1 {
		workerCount = 1
	}

	// 预下载并发信号量：trace 验证保持高并发，但真正测延迟的 10KB 下载限制在低并发，
	// 以降低网络竞争，使 stage1 预检延迟更接近 stage2 实测延迟。
	var preDLSem chan struct{}
	if s.preDownloadVerify {
		pdWorkers := int(s.preDownloadWorkers)
		if pdWorkers < 1 {
			if s.speedWorkers > 0 {
				pdWorkers = int(s.speedWorkers)
			} else {
				pdWorkers = 10
			}
		}
		preDLSem = make(chan struct{}, pdWorkers)
	}

	for i := 0; i < workerCount; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for t := range jobs {
				select {
				case <-s.getCtx().Done():
					atomic.AddInt64(&completed, 1)
					s.reportProgress(offset+atomic.LoadInt64(&completed), total)
					continue
				default:
				}

				key := fmt.Sprintf("%s:%d", t.IP, t.Port)
				if s.isIPReported(key) {
					atomic.AddInt64(&completed, 1)
					s.reportProgress(offset+atomic.LoadInt64(&completed), total)
					continue
				}

				latency, dc, tlsEnabled := s.testDelayAndVerify(t)
				if latency < 0 || dc == "" {
					atomic.AddInt64(&completed, 1)
					s.reportProgress(offset+atomic.LoadInt64(&completed), total)
					continue
				}

				// 若开启预下载校验，进一步确认节点能真正下载，过滤假有效节点；
				// 并用预下载的 TCP 连接延迟作为最终延迟，比 /cdn-cgi/trace 更准确。
				if s.preDownloadVerify {
					select {
					case preDLSem <- struct{}{}:
					case <-s.getCtx().Done():
						atomic.AddInt64(&completed, 1)
						s.reportProgress(offset+atomic.LoadInt64(&completed), total)
						continue
					}
					dlLatency, ok := s.verifySmallDownload(t)
					<-preDLSem
					if !ok {
						atomic.AddInt64(&completed, 1)
						s.reportProgress(offset+atomic.LoadInt64(&completed), total)
						continue
					}
					if dlLatency >= 0 {
						latency = dlLatency
					}
				}

				result := Result{
					IP:         t.IP,
					Port:       t.Port,
					DataCenter: dc,
					Latency:    latency,
					TLS:        tlsEnabled,
				}

				if !s.isIPReported(key) {
					s.markIPReported(key)
					if s.callback != nil {
						s.callback.OnValidIPFound(
							result.IP, result.Port, result.DataCenter, result.Latency,
							"", "", "", "", result.TLS,
						)
					}
					select {
					case results <- result:
					case <-s.getCtx().Done():
					}
				}

				atomic.AddInt64(&completed, 1)
				s.reportProgress(offset+atomic.LoadInt64(&completed), total)
			}
		}()
	}

	// 分发本批任务
	go func() {
		for _, target := range batch {
			select {
			case jobs <- target:
			case <-s.getCtx().Done():
				break
			}
		}
		close(jobs)
	}()

	// 等待 worker 结束
	go func() {
		wg.Wait()
		close(results)
	}()

	var batchResults []Result
	for r := range results {
		batchResults = append(batchResults, r)
	}

	return batchResults
}

// toPunycodeURL 将 URL 中的中文域名转换为 Punycode
func toPunycodeURL(rawURL string) string {
	protocol := ""
	rest := rawURL
	if strings.HasPrefix(rawURL, "https://") {
		protocol = "https://"
		rest = rawURL[8:]
	} else if strings.HasPrefix(rawURL, "http://") {
		protocol = "http://"
		rest = rawURL[7:]
	}

	slashIdx := strings.Index(rest, "/")
	domainPart := rest
	pathPart := ""
	if slashIdx > 0 {
		domainPart = rest[:slashIdx]
		pathPart = rest[slashIdx:]
	}

	hostPart := domainPart
	portPart := ""
	if colonIdx := strings.LastIndex(domainPart, ":"); colonIdx > strings.LastIndex(domainPart, "]") {
		hostPart = domainPart[:colonIdx]
		portPart = domainPart[colonIdx:]
	}

	asciiHost, err := idna.ToASCII(hostPart)
	if err != nil {
		return rawURL
	}

	return protocol + asciiHost + portPart + pathPart
}

// ========== 【关键修复】downloadSpeedWithConn：实时上报字节进度 + 同步测量连接延迟 ==========
// 在下载测速阶段复用与延迟测试完全一致的 TCP 连接耗时测量方式，
// 使最终结果的 Latency 与“下载测试时的真实延迟”保持一致。
// 注意：本阶段不调用 acquireRatePermit，因此不会占用扫描速率限制配额。
func (s *Scanner) downloadSpeedWithConn(result Result) SpeedTestResult {
	timeout := time.Duration(s.timeout) * time.Millisecond
	const minSpeedTimeout = 15 * time.Second
	if timeout < minSpeedTimeout {
		timeout = minSpeedTimeout
	}

	isDomain := net.ParseIP(result.IP) == nil

	punycodeURL := toPunycodeURL(s.speedTestURL)

	speedURLStr := punycodeURL
	if !strings.HasPrefix(speedURLStr, "http") {
		var protocol string
		if s.enableTLS {
			protocol = "https://"
		} else {
			protocol = "http://"
		}
		speedURLStr = protocol + speedURLStr
	}

	addr := fmt.Sprintf("%s:%d", result.IP, result.Port)
	if strings.Contains(result.IP, ":") {
		addr = fmt.Sprintf("[%s]:%d", result.IP, result.Port)
	}

	var conn net.Conn
	var err error
	dialStart := time.Now()

	if isDomain {
		// 【关键修复】域名直接连接，不解析为IP
		dialer := &net.Dialer{
			Timeout:   timeout,
			KeepAlive: 0,
			Resolver: &net.Resolver{
				PreferGo: true,
				Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
					return nil, fmt.Errorf("DNS lookup disabled for domain testing")
				},
			},
		}
		conn, err = dialer.Dial("tcp", addr)
	} else {
		dialer := &net.Dialer{
			Timeout:   timeout,
			KeepAlive: 0,
		}
		conn, err = dialer.Dial("tcp", addr)
	}

	if err != nil {
		return SpeedTestResult{Speed: 0, Latency: -1}
	}
	defer conn.Close()
	latency := time.Since(dialStart).Milliseconds()

	tlsConfig := &tls.Config{
		InsecureSkipVerify: true,
	}
	if isDomain {
		tlsConfig.ServerName = result.IP
	} else {
		if u, err := url.Parse(speedURLStr); err == nil && u.Hostname() != "" {
			tlsConfig.ServerName = u.Hostname()
		}
	}

	client := http.Client{
		Transport: &http.Transport{
			Dial: func(network, addr string) (net.Conn, error) {
				return conn, nil
			},
			TLSClientConfig:    tlsConfig,
			DisableCompression: true,
			DisableKeepAlives:  true,
		},
		Timeout: timeout,
	}

	req, err := http.NewRequest("GET", speedURLStr, nil)
	if err != nil {
		return SpeedTestResult{Speed: 0, Latency: latency}
	}

	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
	req.Header.Set("Accept", "*/*")

	resp, err := client.Do(req)
	if err != nil {
		return SpeedTestResult{Speed: 0, Latency: latency}
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return SpeedTestResult{Speed: 0, Latency: latency}
	}

	// 【关键修复】32KB缓冲 + 实时字节进度上报
	startTime := time.Now()
	var written int64
	buf := make([]byte, 32*1024) // 32KB缓冲，减少系统调用
	
	for {
		n, err := resp.Body.Read(buf)
		if n > 0 {
			written += int64(n)
			// 【关键】每下载1MB上报一次字节进度
			if written%(1024*1024) == 0 {
				s.reportSpeedBytes(written, s.speedTestBytesPerTarget)
			}
		}
		if err != nil {
			break
		}
	}
	
	duration := time.Since(startTime)

	if written < 1024 && duration.Seconds() < 0.5 {
		return SpeedTestResult{Speed: 0, Latency: latency}
	}

	// 最终上报实际字节
	s.reportSpeedBytes(written, s.speedTestBytesPerTarget)

	speed := float64(written) / duration.Seconds() / 1024 / 1024
	return SpeedTestResult{Speed: speed, Latency: latency}
}

// stage2DownloadTest 第二阶段：下载速度测试（分批次工作池）
func (s *Scanner) stage2DownloadTest(results []Result) []Result {
	if len(results) == 0 {
		if s.debugLog {
			s.log("⚠️ 测速阶段：没有有效的目标")
		}
		if s.callback != nil {
			s.callback.OnProgress(1.0)
		}
		return []Result{}
	}

	// 【关键修复】阶段2开始前初始化总字节数预估
	s.speedBytesMutex.Lock()
	s.speedTestTotalBytes = int64(len(results)) * s.speedTestBytesPerTarget
	s.speedTestCurrentBytes = 0
	s.speedBytesMutex.Unlock()

	// 阶段2开始前确认cancel状态
	select {
	case <-s.getCtx().Done():
		if s.debugLog {
			s.log("【Go】警告：阶段2开始时cancel已关闭，重建通道")
		}
		ctx, cancel := context.WithCancel(context.Background())
		s.ctxAtomic.Store(ctx)
		s.cancel = cancel
	default:
	}

	rand.Shuffle(len(results), func(i, j int) {
		results[i], results[j] = results[j], results[i]
	})

	// [关键修复] 按批次处理；千万级目标使用更大批次
	batchSize := 100000
	switch {
	case len(results) > 5000000:
		batchSize = 500000
	case len(results) > 1000000:
		batchSize = 250000
	}

	var allFinal []Result
	total := int64(len(results))

	for start := 0; start < len(results); start += batchSize {
		select {
		case <-s.getCtx().Done():
			return allFinal
		default:
		}

		end := start + batchSize
		if end > len(results) {
			end = len(results)
		}

		batch := results[start:end]
		//s.log("【测速批次】%d/%d，本批 %d 个目标", start, len(results), len(batch))

		batchFinal := s.stage2DownloadTestBatch(batch, int64(start), total)
		allFinal = append(allFinal, batchFinal...)

		// 批次间短暂喘息，保证 GC 与进度条顺滑
		if end < len(results) {
			time.Sleep(20 * time.Millisecond)
		}
	}

	// 【关键修复】所有worker完成后，强制上报100%；分块扫描时由调用方控制最终进度
	s.progressMutex.Lock()
	s.lastProgressTime = 0 // 清除节流，允许立即上报
	s.progressMutex.Unlock()
	if s.callback != nil && s.progressScale == 0 {
		s.callback.OnProgress(1.0)
	}

	return allFinal
}

// stage2DownloadTestBatch 单批次下载测速（工作池模式）
func (s *Scanner) stage2DownloadTestBatch(batch []Result, offset, total int64) []Result {
	var wg sync.WaitGroup
	bufSize := int(s.speedWorkers) * 2
	if bufSize < 64 {
		bufSize = 64
	}
	jobs := make(chan Result, bufSize)
	output := make(chan Result, bufSize)

	var completed int64

	workerCount := int(s.speedWorkers)
	if workerCount < 1 {
		workerCount = 1
	}

	for i := 0; i < workerCount; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for r := range jobs {
				select {
				case <-s.getCtx().Done():
					atomic.AddInt64(&completed, 1)
					s.reportProgress(offset+atomic.LoadInt64(&completed), total)
					continue
				default:
				}

				key := fmt.Sprintf("%s:%d", r.IP, r.Port)
				s.speedTestMutex.Lock()
				if s.speedTestedIPs[key] {
					s.speedTestMutex.Unlock()
					atomic.AddInt64(&completed, 1)
					s.reportProgress(offset+atomic.LoadInt64(&completed), total)
					continue
				}
				s.speedTestedIPs[key] = true
				s.speedTestMutex.Unlock()

				speedResult := s.downloadSpeedWithConn(r)

				updated := r
				updated.DownloadSpeed = speedResult.Speed
				// 【关键修复】用下载阶段实测的连接延迟覆盖/补充 stage1 的延迟，
				// 保证最终 Latency 与“下载测试时的延迟”保持一致。
				updated.Latency = speedResult.Latency

				if s.callback != nil {
					s.callback.OnSpeedTestComplete(r.IP, r.Port, speedResult.Speed, speedResult.Latency)
				}

				select {
				case output <- updated:
				case <-s.getCtx().Done():
				}

				atomic.AddInt64(&completed, 1)
				s.reportProgress(offset+atomic.LoadInt64(&completed), total)
			}
		}()
	}

	go func() {
		for _, result := range batch {
			select {
			case jobs <- result:
			case <-s.getCtx().Done():
				break
			}
		}
		close(jobs)
	}()

	go func() {
		wg.Wait()
		close(output)
	}()

	var batchFinal []Result
	for r := range output {
		if s.speedLimit > 0 && r.DownloadSpeed < s.speedLimit {
			continue
		}
		batchFinal = append(batchFinal, r)
	}

	return batchFinal
}

// reportSpeedProgress 测速阶段专用进度报告（带节流）
// 【关键修复】统一使用 reportProgress，删除重复代码
// 保留此函数名是为了兼容，实际逻辑已合并到 reportProgress

func (s *Scanner) log(format string, args ...interface{}) {
	s.addLogToBuffer(fmt.Sprintf(format, args...))
}