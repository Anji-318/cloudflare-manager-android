# Cloudflare 多账户管理系统 - Android 原生版

这是 Cloudflare 多账户管理系统的 **原生 Android 重构版本**，采用 Kotlin + Jetpack Compose + Material3，专门为移动设备设计，不再依赖 Tauri WebView。应用图标沿用桌面端图标，版本号与桌面端保持一致。


## 技术栈

- **语言**：Kotlin 2.0
- **UI**：Jetpack Compose + Material3
- **导航**：Navigation Compose
- **架构**：MVVM + Repository
- **依赖注入**：Hilt
- **网络**：Retrofit + OkHttp + Kotlinx Serialization
- **本地存储**：Room（账户）+ DataStore（设置）+ EncryptedSharedPreferences（Token）

## 已完成功能

- [x] 仪表盘：账户数、域名数、DNS 记录数、请求数、域名列表点击切换
- [x] 账户管理：添加、删除、切换账户，Token 加密存储
- [x] 域名列表：搜索、刷新、点击进入详情
- [x] DNS 管理：列表、添加、编辑、删除记录，代理开关
- [x] Workers 管理：脚本列表、代码查看与编辑（支持大文件虚拟化滚动）
- [x] KV 管理：命名空间列表、键值查看/编辑/删除/新增
- [x] D1 管理：数据库列表、表结构浏览、SQL 查询、数据浏览与行编辑
- [x] 设置：深色/浅色主题切换（跟随系统/强制深色）、清除所有数据
- [x] 底部导航 + 更多菜单：Workers / Pages / R2 / KV / D1 / Tunnels / Firewall / 缓存 / 分析入口

## 待完善功能

以下模块已具备页面骨架与导航，业务逻辑可逐步补充：

- [ ] Pages 项目管理（列表/创建/删除/变量管理）
- [ ] R2 Bucket 列表
- [x] KV / D1 数据管理：KV 键值查看/编辑/删除/新增，D1 表结构浏览、SQL 查询、数据浏览与行编辑
- [ ] Tunnels 列表
- [ ] Firewall 规则与统计
- [ ] 缓存清除与性能开关
- [ ] 分析报表图表

## 版本历史

### v0.3.1
- KV 详情页：支持键列表、查看/编辑/删除键值、新增键值
- D1 详情页：3 个标签页（表结构、SQL 查询、数据浏览），支持行编辑/删除/添加
- 仪表盘底部导航栏遮挡修复
- 禁用 KSP 增量编译，解决 Android Studio 与命令行同时编译冲突

### v0.2.0
- Workers 代码编辑器（大文件虚拟化滚动）
- 修复 Zones 页面加载问题
- 统一 LoadingIndicator 样式


## 构建环境要求

- Android Gradle Plugin 8.7.0
- Gradle 8.9（wrapper 已配置）
- JDK 17 或更高
- Android SDK 35（compileSdk）/ 最低 Android 8.0（minSdk 26）

- Android Gradle Plugin 8.7.0
- Gradle 8.9（wrapper 已配置）
- JDK 17 或更高
- Android SDK 35（compileSdk）/ 最低 Android 8.0（minSdk 26）

## 已知问题

- **KSP 编译冲突**：不要同时运行 Android Studio 编译和命令行 `./gradlew`。如发生冲突，关闭 Android Studio 后执行 `./gradlew clean` 并删除 `app/build`、`.gradle`、`.kotlin` 目录后重建。

## 项目结构

```
cloudflare-manager-android/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/cloudflare/manager/
│       │   │   ├── MainActivity.kt
│       │   │   ├── MainActivityViewModel.kt
│       │   │   ├── CloudflareManagerApp.kt
│       │   │   ├── CloudflareManagerAppContent.kt
│       │   │   ├── di/
│       │   │   │   ├── AppModule.kt
│       │   │   │   └── NetworkModule.kt
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   │   ├── AccountDao.kt
│       │   │   │   │   ├── AccountDatabase.kt
│       │   │   │   │   ├── AccountEntity.kt
│       │   │   │   │   ├── SecureTokenManager.kt
│       │   │   │   │   └── SettingsDataStore.kt
│       │   │   │   ├── remote/
│       │   │   │   │   ├── CloudflareApiService.kt
│       │   │   │   │   ├── CloudflareResponse.kt
│       │   │   │   │   └── dto/
│       │   │   │   │       ├── AccountDto.kt
│       │   │   │   │       ├── DnsRecordDto.kt
│       │   │   │   │       └── ZoneDto.kt
│       │   │   │   └── repository/
│       │   │   │       ├── AccountRepository.kt
│       │   │   │       ├── CloudflareRepository.kt
│       │   │   │       ├── DnsRepository.kt
│       │   │   │       ├── SessionManager.kt
│       │   │   │       └── ZoneRepository.kt
│       │   │   ├── domain/model/
│       │   │   │   ├── Account.kt
│       │   │   │   ├── Analytics.kt
│       │   │   │   ├── D1Database.kt
│       │   │   │   ├── DnsRecord.kt
│       │   │   │   ├── FirewallRule.kt
│       │   │   │   ├── KvNamespace.kt
│       │   │   │   ├── PageProject.kt
│       │   │   │   ├── R2Bucket.kt
│       │   │   │   ├── Tunnel.kt
│       │   │   │   ├── UiState.kt
│       │   │   │   ├── Worker.kt
│       │   │   │   └── Zone.kt
│       │   │   └── ui/
│       │   │       ├── components/          # 复用组件（卡片、顶部栏、底部导航等）
│       │   │       ├── navigation/          # NavHost + 路由定义
│       │   │       ├── theme/               # Color / Theme / Typography
│       │   │       ├── accounts/            # 账户列表 / 添加账户
│       │   │       ├── analytics/           # 分析报表（骨架）
│       │   │       ├── cache/               # 缓存管理（骨架）
│       │   │       ├── dashboard/           # 仪表盘
│       │   │       ├── dns/                 # DNS 管理 / 编辑
│       │   │       ├── firewall/            # 防火墙（骨架）
│       │   │       ├── help/                # 帮助页面
│       │   │       ├── kvd1/                # KV / D1 管理（骨架）
│       │   │       ├── more/                # "更多"菜单
│       │   │       ├── pages/               # Pages 项目（骨架）
│       │   │       ├── r2/                  # R2 存储（骨架）
│       │   │       ├── settings/            # 设置
│       │   │       ├── tunnels/             # Tunnels（骨架）
│       │   │       ├── workers/             # Workers 脚本列表 / 详情 / 代码编辑器
│       │   │       └── zones/               # 域名列表 / 详情
│       │   └── res/
│       │       ├── mipmap-mdpi ~ mipmap-xxxhdpi   # 启动图标
│       │       └── values/
│       │           ├── strings.xml
│       │           └── themes.xml
│       └── test/java/com/cloudflare/manager/
│           └── ExampleUnitTest.kt
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── Android智能编译脚本_v2.0.bat
├── README.md
└── .gitignore
```

## 注意事项

- 首次添加账户时需要联网验证 Token。
- 请确保 API Token 至少包含：账户设置读取、区域读取、DNS 编辑权限。
- 如需管理 Workers/Pages 等高级功能，请在 Token 中勾选对应权限。

## 推荐权限配置

根据你要管理的资源，在 **Permissions** 区域添加以下权限：

| 范围 | 资源 | 权限 |
|------|------|------|
| 账户 | D1 | 编辑 |
| 账户 | Cloudflare Pages | 编辑 |
| 账户 | Workers R2 存储 | 编辑 |
| 账户 | Workers KV 存储 | 编辑 |
| 账户 | Workers 脚本 | 编辑 |
| 账户 | 账户 | 读取 |
| 账户 | 账户设置 | 读取 |
| 区域 | 区域设置 | 编辑 |
| 区域 | 区域 | 读取 |
| 区域 | 防火墙服务 | 编辑 |
| 区域 | Workers 路由 | 编辑 |
| 区域 | DNS | 编辑 |
| 区域 | Analytics | 读取 |
| 账户 | Cloudflare Tunnel | 编辑 |
