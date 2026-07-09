package com.cloudflare.manager.data.repository

import com.cloudflare.manager.data.remote.CloudflareApiService
import com.cloudflare.manager.data.remote.CloudflareResponse
import com.cloudflare.manager.data.remote.errorMessage
import com.cloudflare.manager.domain.model.AnalyticsSummary
import com.cloudflare.manager.domain.model.CountryStat
import com.cloudflare.manager.domain.model.D1Database
import com.cloudflare.manager.domain.model.FirewallRule
import com.cloudflare.manager.domain.model.KvKey
import com.cloudflare.manager.domain.model.KvNamespace
import com.cloudflare.manager.domain.model.PageDeployment
import com.cloudflare.manager.domain.model.PageDomain
import com.cloudflare.manager.domain.model.PageProject
import com.cloudflare.manager.domain.model.R2Bucket
import com.cloudflare.manager.domain.model.Tunnel
import com.cloudflare.manager.domain.model.WorkerDomain
import com.cloudflare.manager.domain.model.WorkerRoute
import com.cloudflare.manager.domain.model.WorkerScript
import com.cloudflare.manager.domain.model.WorkerSubdomainInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudflareRepository @Inject constructor(
    private val apiService: CloudflareApiService,
    private val json: Json,
    private val okHttpClient: OkHttpClient
) {

    // Analytics
    suspend fun getAnalytics(
        zoneId: String,
        since: String,
        until: String
    ): Result<Pair<AnalyticsSummary, List<CountryStat>>> {
        return apiCall {
            apiService.getAnalytics(zoneId, since, until)
        }.map { response ->
            val result = response.result ?: return@map AnalyticsSummary() to emptyList()
            val timeseries = result.jsonObject["timeseries"]?.jsonArray ?: emptyList()
            var totalRequests = 0L
            var threatRequests = 0L
            var cachedRequests = 0L
            var uncachedRequests = 0L
            timeseries.forEach { point ->
                val requests = point.jsonObject["requests"]?.jsonObject
                totalRequests += requests?.get("all")?.jsonPrimitive?.longOrNull ?: 0L
                threatRequests += requests?.get("threat")?.jsonPrimitive?.longOrNull ?: 0L
                cachedRequests += requests?.get("cached")?.jsonPrimitive?.longOrNull ?: 0L
                uncachedRequests += requests?.get("uncached")?.jsonPrimitive?.longOrNull ?: 0L
            }
            val cacheRate = if (totalRequests > 0) {
                ((cachedRequests.toDouble() / totalRequests) * 100).toInt()
            } else 0
            val summary = AnalyticsSummary(
                totalRequests = totalRequests,
                threatRequests = threatRequests,
                cachedRequests = cachedRequests,
                cacheRate = cacheRate
            )
            val countries = result.jsonObject["totals"]?.jsonObject?.get("country")?.jsonObject
            val countryStats = countries?.map { (name, value) ->
                CountryStat(name, value.jsonPrimitive.longOrNull ?: 0L)
            }?.sortedByDescending { it.value } ?: emptyList()
            summary to countryStats
        }
    }

    // Cache
    suspend fun purgeCache(zoneId: String): Result<Unit> {
        return apiCall {
            apiService.purgeCache(
                zoneId,
                json.parseToJsonElement("{\"purge_everything\":true}")
            )
        }.map { }
    }

    suspend fun getZoneSetting(zoneId: String, settingId: String): Result<Boolean> {
        return apiCall {
            apiService.getZoneSetting(zoneId, settingId)
        }.map { response ->
            parseSettingValue(response.result, settingId)
        }
    }

    suspend fun updateZoneSetting(
        zoneId: String,
        settingId: String,
        value: JsonElement
    ): Result<Unit> {
        return apiCall {
            apiService.updateZoneSetting(zoneId, settingId, json.parseToJsonElement("{\"value\":$value}"))
        }.map { }
    }

    // Firewall
    suspend fun listFirewallRules(zoneId: String): Result<List<FirewallRule>> {
        return apiCall {
            apiService.listFirewallRules(zoneId)
        }.map { response ->
            response.result?.jsonArray?.map { rule ->
                val obj = rule.jsonObject
                FirewallRule(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    description = obj["description"]?.jsonPrimitive?.contentOrNull ?: "",
                    action = obj["action"]?.jsonPrimitive?.contentOrNull ?: "",
                    paused = obj["paused"]?.jsonPrimitive?.booleanOrNull ?: false,
                    mode = obj["mode"]?.jsonPrimitive?.contentOrNull
                )
            } ?: emptyList()
        }
    }

    suspend fun countAccessRules(zoneId: String): Result<Int> {
        return apiCall {
            apiService.listAccessRules(zoneId, perPage = 1)
        }.map { response ->
            response.result_info?.jsonObject?.get("total_count")?.jsonPrimitive?.intOrNull
                ?: response.result?.jsonArray?.size ?: 0
        }
    }

    // Tunnels
    suspend fun listTunnels(accountId: String): Result<List<Tunnel>> {
        return apiCall {
            apiService.listTunnels(accountId)
        }.map { response ->
            response.result?.jsonArray?.map { tunnel ->
                val obj = tunnel.jsonObject
                Tunnel(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    status = if (obj["status"]?.jsonPrimitive?.contentOrNull == "healthy") "健康" else "离线",
                    connections = obj["connections"]?.jsonArray?.size ?: 0,
                    createdAt = obj["created_at"]?.jsonPrimitive?.contentOrNull
                )
            } ?: emptyList()
        }
    }

    // Workers
    suspend fun listWorkers(accountId: String): Result<List<WorkerScript>> {
        return apiCall {
            apiService.listWorkers(accountId)
        }.map { response ->
            response.result?.jsonArray?.map { worker ->
                val obj = worker.jsonObject
                WorkerScript(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    name = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    createdOn = obj["created_on"]?.jsonPrimitive?.contentOrNull,
                    modifiedOn = obj["modified_on"]?.jsonPrimitive?.contentOrNull
                )
            } ?: emptyList()
        }
    }

    suspend fun deleteWorker(accountId: String, scriptName: String): Result<Unit> {
        return apiCall {
            apiService.deleteWorker(accountId, scriptName)
        }.map { }
    }

    suspend fun getWorkerScript(accountId: String, scriptName: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(
                        "https://api.cloudflare.com/client/v4/accounts/${accountId}/workers/scripts/${scriptName}"
                    )
                    .header("Accept", "multipart/form-data, application/javascript, application/javascript+module")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(response.body?.string() ?: response.message)
                    )
                }
                val bodyString = response.body?.string() ?: ""
                val contentType = response.header("Content-Type") ?: ""
                val script = if (contentType.startsWith("multipart/", ignoreCase = true)) {
                    extractScriptFromMultipart(bodyString, contentType)
                } else {
                    bodyString
                }
                Result.success(script)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun uploadWorkerScript(
        accountId: String,
        scriptName: String,
        script: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val boundary = UUID.randomUUID().toString()
            val metadataJson = if (
                script.contains("export default") ||
                script.contains("import ", ignoreCase = true)
            ) {
                "{\"main_module\":\"worker.js\"}"
            } else {
                "{\"body_part\":\"worker.js\"}"
            }
            val metadataBody = metadataJson.toRequestBody("application/json".toMediaTypeOrNull())
            val scriptBody = script.toRequestBody("application/javascript".toMediaTypeOrNull())
            val multipartBody = MultipartBody.Builder(boundary)
                .setType(MultipartBody.FORM)
                .addPart(
                    okhttp3.Headers.headersOf(
                        "Content-Disposition", "form-data; name=\"metadata\"",
                        "Content-Type", "application/json"
                    ),
                    metadataBody
                )
                .addPart(
                    okhttp3.Headers.headersOf(
                        "Content-Disposition",
                        "form-data; name=\"worker.js\"; filename=\"worker.js\"",
                        "Content-Type", "application/javascript"
                    ),
                    scriptBody
                )
                .build()
            val request = Request.Builder()
                .url(
                    "https://api.cloudflare.com/client/v4/accounts/${accountId}/workers/scripts/${scriptName}"
                )
                .put(multipartBody)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body?.string() ?: response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWorkersSubdomain(accountId: String): Result<WorkerSubdomainInfo> {
        return apiCall {
            apiService.getWorkersSubdomain(accountId)
        }.map { response ->
            val obj = response.result?.jsonObject
            WorkerSubdomainInfo(
                subdomain = obj?.get("subdomain")?.jsonPrimitive?.contentOrNull,
                enabled = obj?.get("enabled")?.jsonPrimitive?.booleanOrNull ?: false
            )
        }
    }

    suspend fun setWorkersSubdomain(
        accountId: String,
        scriptName: String,
        enabled: Boolean
    ): Result<Unit> {
        return apiCall {
            apiService.setWorkersSubdomain(
                accountId,
                scriptName,
                json.parseToJsonElement("{\"enabled\":$enabled}")
            )
        }.map { }
    }

    suspend fun listWorkerDomains(
        accountId: String,
        scriptName: String
    ): Result<List<WorkerDomain>> {
        return apiCall {
            apiService.listWorkerDomains(accountId, scriptName)
        }.map { response ->
            response.result?.jsonArray?.map { domain ->
                val obj = domain.jsonObject
                WorkerDomain(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    hostname = obj["hostname"]?.jsonPrimitive?.contentOrNull ?: "",
                    zoneName = obj["zone_name"]?.jsonPrimitive?.contentOrNull,
                    status = obj["status"]?.jsonPrimitive?.contentOrNull
                )
            } ?: emptyList()
        }
    }

    suspend fun createWorkerDomain(
        accountId: String,
        scriptName: String,
        zoneId: String,
        hostname: String
    ): Result<Unit> {
        return apiCall {
            apiService.createWorkerDomain(
                accountId,
                json.parseToJsonElement(
                    "{\"hostname\":\"${escapeJson(hostname)}\"," +
                        "\"service\":\"${escapeJson(scriptName)}\"," +
                        "\"zone_id\":\"${escapeJson(zoneId)}\"," +
                        "\"environment\":\"production\"}"
                )
            )
        }.map { }
    }

    suspend fun deleteWorkerDomain(accountId: String, domainId: String): Result<Unit> {
        return apiCall {
            apiService.deleteWorkerDomain(accountId, domainId)
        }.map { }
    }

    suspend fun listWorkerRoutes(zoneId: String): Result<List<WorkerRoute>> {
        return apiCall {
            apiService.listWorkerRoutes(zoneId)
        }.map { response ->
            response.result?.jsonArray?.map { route ->
                val obj = route.jsonObject
                WorkerRoute(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    pattern = obj["pattern"]?.jsonPrimitive?.contentOrNull ?: "",
                    script = obj["script"]?.jsonPrimitive?.contentOrNull
                )
            } ?: emptyList()
        }
    }

    suspend fun createWorkerRoute(
        zoneId: String,
        pattern: String,
        scriptName: String
    ): Result<Unit> {
        return apiCall {
            apiService.createWorkerRoute(
                zoneId,
                json.parseToJsonElement(
                    "{\"pattern\":\"${escapeJson(pattern)}\",\"script\":\"${escapeJson(scriptName)}\"}"
                )
            )
        }.map { }
    }

    suspend fun deleteWorkerRoute(zoneId: String, routeId: String): Result<Unit> {
        return apiCall {
            apiService.deleteWorkerRoute(zoneId, routeId)
        }.map { }
    }

    suspend fun putWorkerSecret(
        accountId: String,
        scriptName: String,
        name: String,
        value: String
    ): Result<Unit> {
        return apiCall {
            apiService.putWorkerSecret(
                accountId,
                scriptName,
                json.parseToJsonElement(
                    "{\"name\":\"${escapeJson(name)}\"," +
                        "\"type\":\"secret_text\"," +
                        "\"text\":\"${escapeJson(value)}\"}"
                )
            )
        }.map { }
    }

    suspend fun deleteWorkerSecret(
        accountId: String,
        scriptName: String,
        name: String
    ): Result<Unit> {
        return apiCall {
            apiService.deleteWorkerSecret(accountId, scriptName, name)
        }.map { }
    }

    // Pages
    suspend fun listPagesProjects(accountId: String): Result<List<PageProject>> {
        return apiCall {
            apiService.listPagesProjects(accountId)
        }.map { response ->
            response.result?.jsonArray?.map { project ->
                val obj = project.jsonObject
                PageProject(
                    name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    productionBranch = obj["production_branch"]?.jsonPrimitive?.contentOrNull,
                    latestDeployment = obj["latest_deployment"]?.jsonObject?.get("modified_on")
                        ?.jsonPrimitive?.contentOrNull,
                    subdomain = obj["subdomain"]?.jsonPrimitive?.contentOrNull,
                    createdOn = obj["created_on"]?.jsonPrimitive?.contentOrNull
                )
            } ?: emptyList()
        }
    }

    suspend fun createPagesProject(
        accountId: String,
        name: String,
        productionBranch: String = "main"
    ): Result<Unit> {
        return apiCall {
            apiService.createPagesProject(
                accountId,
                json.parseToJsonElement(
                    "{\"name\":\"${escapeJson(name)}\",\"production_branch\":\"${escapeJson(productionBranch)}\"}"
                )
            )
        }.map { }
    }

    suspend fun deletePagesProject(accountId: String, projectName: String): Result<Unit> {
        return apiCall {
            apiService.deletePagesProject(accountId, projectName)
        }.map { }
    }

    suspend fun listPagesDeployments(
        accountId: String,
        projectName: String
    ): Result<List<PageDeployment>> {
        return apiCall {
            apiService.listPagesDeployments(accountId, projectName)
        }.map { response ->
            response.result?.jsonArray?.map { deployment ->
                val obj = deployment.jsonObject
                PageDeployment(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    environment = obj["environment"]?.jsonPrimitive?.contentOrNull,
                    url = obj["url"]?.jsonPrimitive?.contentOrNull,
                    createdOn = obj["created_on"]?.jsonPrimitive?.contentOrNull
                )
            } ?: emptyList()
        }
    }

    suspend fun listPagesDomains(
        accountId: String,
        projectName: String
    ): Result<List<PageDomain>> {
        return apiCall {
            apiService.listPagesDomains(accountId, projectName)
        }.map { response ->
            response.result?.jsonArray?.map { domain ->
                val obj = domain.jsonObject
                PageDomain(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    status = obj["status"]?.jsonPrimitive?.contentOrNull,
                    zoneName = obj["zone_name"]?.jsonPrimitive?.contentOrNull
                )
            } ?: emptyList()
        }
    }

    suspend fun createPagesDomain(
        accountId: String,
        projectName: String,
        domain: String
    ): Result<Unit> {
        return apiCall {
            apiService.createPagesDomain(
                accountId,
                projectName,
                json.parseToJsonElement("{\"name\":\"${escapeJson(domain)}\"}")
            )
        }.map { }
    }

    suspend fun deletePagesDomain(
        accountId: String,
        projectName: String,
        domainName: String
    ): Result<Unit> {
        return apiCall {
            apiService.deletePagesDomain(accountId, projectName, domainName)
        }.map { }
    }

    suspend fun retryPagesDomain(
        accountId: String,
        projectName: String,
        domainName: String
    ): Result<Unit> {
        return apiCall {
            apiService.retryPagesDomain(accountId, projectName, domainName)
        }.map { }
    }

    // R2
    suspend fun listR2Buckets(accountId: String): Result<List<R2Bucket>> {
        return apiCall {
            apiService.listR2Buckets(accountId)
        }.map { response ->
            response.result?.jsonObject?.get("buckets")?.jsonArray?.map { bucket ->
                val obj = bucket.jsonObject
                R2Bucket(
                    name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    creationDate = obj["creation_date"]?.jsonPrimitive?.contentOrNull
                )
            } ?: emptyList()
        }
    }

    suspend fun createR2Bucket(accountId: String, name: String): Result<Unit> {
        return apiCall {
            apiService.createR2Bucket(
                accountId,
                json.parseToJsonElement("{\"name\":\"$name\"}")
            )
        }.map { }
    }

    suspend fun deleteR2Bucket(accountId: String, name: String): Result<Unit> {
        return apiCall {
            apiService.deleteR2Bucket(accountId, name)
        }.map { }
    }

    // KV
    suspend fun listKvNamespaces(accountId: String): Result<List<KvNamespace>> {
        return apiCall {
            apiService.listKvNamespaces(accountId)
        }.map { response ->
            response.result?.jsonArray?.map { ns ->
                val obj = ns.jsonObject
                KvNamespace(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
                )
            } ?: emptyList()
        }
    }

    suspend fun createKvNamespace(accountId: String, title: String): Result<Unit> {
        return apiCall {
            apiService.createKvNamespace(
                accountId,
                json.parseToJsonElement("{\"title\":\"$title\"}")
            )
        }.map { }
    }

    suspend fun deleteKvNamespace(accountId: String, namespaceId: String): Result<Unit> {
        return apiCall {
            apiService.deleteKvNamespace(accountId, namespaceId)
        }.map { }
    }

    suspend fun listKvKeys(accountId: String, namespaceId: String): Result<List<KvKey>> {
        return apiCall {
            apiService.listKvKeys(accountId, namespaceId)
        }.map { response ->
            response.result?.jsonArray?.map { key ->
                val obj = key.jsonObject
                KvKey(
                    name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    expiration = obj["expiration"]?.jsonPrimitive?.longOrNull,
                    metadata = obj["metadata"]?.toString()
                )
            } ?: emptyList()
        }
    }

    suspend fun getKvValue(accountId: String, namespaceId: String, key: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = apiService.getKvValue(accountId, namespaceId, key)
                val value = responseBody.string()
                Result.success(value)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun putKvValue(
        accountId: String,
        namespaceId: String,
        key: String,
        value: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val requestBody = value.toRequestBody("application/octet-stream".toMediaTypeOrNull())
            val response = apiService.putKvValue(accountId, namespaceId, key, requestBody)
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteKvValue(
        accountId: String,
        namespaceId: String,
        key: String
    ): Result<Unit> {
        return apiCall {
            apiService.deleteKvValue(accountId, namespaceId, key)
        }.map { }
    }

    // D1
    suspend fun listD1Databases(accountId: String): Result<List<D1Database>> {
        return apiCall {
            apiService.listD1Databases(accountId)
        }.map { response ->
            response.result?.jsonArray?.map { db ->
                val obj = db.jsonObject
                D1Database(
                    uuid = obj["uuid"]?.jsonPrimitive?.contentOrNull ?: "",
                    name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    version = obj["version"]?.jsonPrimitive?.contentOrNull,
                    createdAt = obj["created_at"]?.jsonPrimitive?.contentOrNull
                )
            } ?: emptyList()
        }
    }

    suspend fun createD1Database(accountId: String, name: String): Result<Unit> {
        return apiCall {
            apiService.createD1Database(
                accountId,
                json.parseToJsonElement("{\"name\":\"$name\"}")
            )
        }.map { }
    }

    suspend fun deleteD1Database(accountId: String, databaseId: String): Result<Unit> {
        return apiCall {
            apiService.deleteD1Database(accountId, databaseId)
        }.map { }
    }

    suspend fun queryD1(
        accountId: String,
        databaseId: String,
        sql: String
    ): Result<List<Map<String, JsonElement>>> {
        return apiCall {
            apiService.queryD1Database(
                accountId,
                databaseId,
                json.parseToJsonElement("{\"sql\":\"${escapeSql(sql)}\"}")
            )
        }.map { response ->
            val results = response.result?.jsonArray?.firstOrNull()?.jsonObject?.get("results")?.jsonArray
            results?.map { it.jsonObject.toMap() } ?: emptyList()
        }
    }

    private fun escapeSql(sql: String): String {
        return sql.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun extractScriptFromMultipart(text: String, contentType: String): String {
        // 如果不是 multipart，直接返回
        if (!contentType.startsWith("multipart/", ignoreCase = true)) {
            return text
        }
        
        // 提取 boundary
        val boundary = contentType
            .substringAfter("boundary=", "")
            .trim()
            .removeSurrounding("\"")
            .ifEmpty { 
                // 尝试从文本第一行提取 boundary
                text.lineSequence().firstOrNull()?.let { firstLine ->
                    if (firstLine.startsWith("--")) {
                        firstLine.removePrefix("--").trim()
                    } else null
                } ?: return text
            }
        
        // 方法1：使用正则表达式直接提取 worker.js part 的内容
        // 匹配 --boundary 到下一个 --boundary 或 --boundary-- 之间的内容
        val pattern = Regex("""--${Regex.escape(boundary)}\r?\n[\s\S]*?\r?\n\r?\n([\s\S]*?)(?:\r?\n--${Regex.escape(boundary)}(?:--)?\r?\n|\r?\n--${Regex.escape(boundary)}(?:--)?$|$)""")
        val match = pattern.find(text)
        if (match != null) {
            val script = match.groupValues[1].trimEnd()
            // 移除末尾的 --
            return if (script.endsWith("--")) script.removeSuffix("--").trimEnd() else script
        }
        
        // 方法2：传统的 split 方法作为 fallback
        val delimiter = "--$boundary"
        val parts = text.split(delimiter)
        
        for (part in parts) {
            val normalized = part.replace("\r\n", "\n")
            if (
                normalized.contains("name=\"worker.js\"", ignoreCase = true) ||
                normalized.contains("javascript", ignoreCase = true)
            ) {
                val idx = normalized.indexOf("\n\n")
                val script = if (idx >= 0) {
                    normalized.substring(idx + 2)
                } else {
                    val idx2 = normalized.indexOf("\r\n\r\n")
                    if (idx2 >= 0) normalized.substring(idx2 + 4) else normalized
                }
                var result = script.trimEnd()
                if (result.endsWith("--")) {
                    result = result.removeSuffix("--").trimEnd()
                }
                if (result.endsWith(boundary)) {
                    result = result.removeSuffix(boundary).trimEnd()
                }
                if (result.endsWith("--")) {
                    result = result.removeSuffix("--").trimEnd()
                }
                return result
            }
        }
        
        // 方法3：简单的 heuristic fallback
        val lines = text.lines()
        var foundEmptyLine = false
        val result = StringBuilder()
        for (line in lines) {
            if (foundEmptyLine) {
                result.append(line).append("\n")
            } else if (line.isBlank() && !line.startsWith("--")) {
                foundEmptyLine = true
            }
        }
        val extracted = result.toString().trimEnd()
        return if (extracted.isNotEmpty()) extracted else text
    }

    private fun parseSettingValue(result: JsonElement?, settingId: String): Boolean {
        if (result == null) return false
        val value = result.jsonObject["value"] ?: return false
        return when (settingId) {
            "argo_smart_routing" -> value.jsonPrimitive.contentOrNull == "on"
            "polish" -> value.jsonPrimitive.contentOrNull != "off"
            "minify" -> {
                val obj = value.jsonObject
                obj["html"]?.jsonPrimitive?.booleanOrNull == true ||
                    obj["css"]?.jsonPrimitive?.booleanOrNull == true ||
                    obj["js"]?.jsonPrimitive?.booleanOrNull == true
            }
            else -> false
        }
    }

    private suspend fun <T> apiCall(call: suspend () -> CloudflareResponse<T>): Result<CloudflareResponse<T>> {
        return try {
            val response = call()
            if (response.success) {
                Result.success(response)
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
