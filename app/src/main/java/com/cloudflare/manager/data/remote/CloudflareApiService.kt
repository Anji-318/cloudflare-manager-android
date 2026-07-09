package com.cloudflare.manager.data.remote

import com.cloudflare.manager.data.remote.dto.AccountDto
import com.cloudflare.manager.data.remote.dto.DnsRecordDto
import com.cloudflare.manager.data.remote.dto.DnsRecordRequest
import com.cloudflare.manager.data.remote.dto.ZoneDto
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface CloudflareApiService {

    // Token verification
    @GET("user/tokens/verify")
    suspend fun verifyToken(): CloudflareResponse<JsonElement>

    // Accounts
    @GET("accounts")
    suspend fun listAccounts(): CloudflareResponse<List<AccountDto>>

    @GET("accounts/{accountId}")
    suspend fun getAccount(@Path("accountId") accountId: String): CloudflareResponse<AccountDto>

    // Zones
    @GET("zones")
    suspend fun listZones(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 100
    ): CloudflareResponse<List<ZoneDto>>

    @GET("zones/{zoneId}")
    suspend fun getZone(@Path("zoneId") zoneId: String): CloudflareResponse<ZoneDto>

    // DNS Records
    @GET("zones/{zoneId}/dns_records")
    suspend fun listDnsRecords(
        @Path("zoneId") zoneId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 100
    ): CloudflareResponse<List<DnsRecordDto>>

    @POST("zones/{zoneId}/dns_records")
    suspend fun createDnsRecord(
        @Path("zoneId") zoneId: String,
        @Body request: DnsRecordRequest
    ): CloudflareResponse<DnsRecordDto>

    @PUT("zones/{zoneId}/dns_records/{recordId}")
    suspend fun updateDnsRecord(
        @Path("zoneId") zoneId: String,
        @Path("recordId") recordId: String,
        @Body request: DnsRecordRequest
    ): CloudflareResponse<DnsRecordDto>

    @DELETE("zones/{zoneId}/dns_records/{recordId}")
    suspend fun deleteDnsRecord(
        @Path("zoneId") zoneId: String,
        @Path("recordId") recordId: String
    ): CloudflareResponse<DnsRecordDto>

    // Cache
    @POST("zones/{zoneId}/purge_cache")
    suspend fun purgeCache(
        @Path("zoneId") zoneId: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>

    // Zone settings
    @GET("zones/{zoneId}/settings/{settingId}")
    suspend fun getZoneSetting(
        @Path("zoneId") zoneId: String,
        @Path("settingId") settingId: String
    ): CloudflareResponse<JsonElement>

    @PATCH("zones/{zoneId}/settings/{settingId}")
    suspend fun updateZoneSetting(
        @Path("zoneId") zoneId: String,
        @Path("settingId") settingId: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>

    // Analytics
    @GET("zones/{zoneId}/analytics/dashboard")
    suspend fun getAnalytics(
        @Path("zoneId") zoneId: String,
        @Query("since") since: String,
        @Query("until") until: String
    ): CloudflareResponse<JsonElement>

    // Firewall
    @GET("zones/{zoneId}/firewall/rules")
    suspend fun listFirewallRules(
        @Path("zoneId") zoneId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 100
    ): CloudflareResponse<JsonElement>

    @GET("zones/{zoneId}/firewall/access_rules/rules")
    suspend fun listAccessRules(
        @Path("zoneId") zoneId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 100
    ): CloudflareResponse<JsonElement>

    // Tunnels
    @GET("accounts/{accountId}/cfd_tunnel")
    suspend fun listTunnels(
        @Path("accountId") accountId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 100
    ): CloudflareResponse<JsonElement>

    // Workers
    @GET("accounts/{accountId}/workers/scripts")
    suspend fun listWorkers(
        @Path("accountId") accountId: String
    ): CloudflareResponse<JsonElement>

    @GET("accounts/{accountId}/workers/scripts/{scriptName}")
    suspend fun getWorkerScript(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String
    ): ResponseBody

    @DELETE("accounts/{accountId}/workers/scripts/{scriptName}")
    suspend fun deleteWorker(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String
    ): CloudflareResponse<JsonElement>

    // Workers secrets
    @PUT("accounts/{accountId}/workers/scripts/{scriptName}/secrets")
    suspend fun putWorkerSecret(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>

    @DELETE("accounts/{accountId}/workers/scripts/{scriptName}/secrets/{secretName}")
    suspend fun deleteWorkerSecret(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String,
        @Path("secretName") secretName: String
    ): CloudflareResponse<JsonElement>

    // Workers subdomains / custom domains / routes
    @GET("accounts/{accountId}/workers/subdomain")
    suspend fun getWorkersSubdomain(
        @Path("accountId") accountId: String
    ): CloudflareResponse<JsonElement>

    @POST("accounts/{accountId}/workers/scripts/{scriptName}/subdomain")
    suspend fun setWorkersSubdomain(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>

    @GET("accounts/{accountId}/workers/scripts/{scriptName}/domains")
    suspend fun listWorkerDomains(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String
    ): CloudflareResponse<JsonElement>

    @GET("accounts/{accountId}/workers/domains")
    suspend fun listAccountWorkerDomains(
        @Path("accountId") accountId: String
    ): CloudflareResponse<JsonElement>

    @POST("accounts/{accountId}/workers/domains")
    suspend fun createWorkerDomain(
        @Path("accountId") accountId: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>

    @DELETE("accounts/{accountId}/workers/domains/{domainId}")
    suspend fun deleteWorkerDomain(
        @Path("accountId") accountId: String,
        @Path("domainId") domainId: String
    ): CloudflareResponse<JsonElement>

    @PUT("accounts/{accountId}/workers/scripts/{scriptName}/domains/records/{domainId}")
    suspend fun updateWorkerDomain(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String,
        @Path("domainId") domainId: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>

    @GET("zones/{zoneId}/workers/routes")
    suspend fun listWorkerRoutes(
        @Path("zoneId") zoneId: String
    ): CloudflareResponse<JsonElement>

    @POST("zones/{zoneId}/workers/routes")
    suspend fun createWorkerRoute(
        @Path("zoneId") zoneId: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>

    @DELETE("zones/{zoneId}/workers/routes/{routeId}")
    suspend fun deleteWorkerRoute(
        @Path("zoneId") zoneId: String,
        @Path("routeId") routeId: String
    ): CloudflareResponse<JsonElement>

    // Pages
    @GET("accounts/{accountId}/pages/projects")
    suspend fun listPagesProjects(
        @Path("accountId") accountId: String
    ): CloudflareResponse<JsonElement>

    @POST("accounts/{accountId}/pages/projects")
    suspend fun createPagesProject(
        @Path("accountId") accountId: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>

    @DELETE("accounts/{accountId}/pages/projects/{projectName}")
    suspend fun deletePagesProject(
        @Path("accountId") accountId: String,
        @Path("projectName") projectName: String
    ): CloudflareResponse<JsonElement>

    @GET("accounts/{accountId}/pages/projects/{projectName}/deployments")
    suspend fun listPagesDeployments(
        @Path("accountId") accountId: String,
        @Path("projectName") projectName: String
    ): CloudflareResponse<JsonElement>

    @GET("accounts/{accountId}/pages/projects/{projectName}/domains")
    suspend fun listPagesDomains(
        @Path("accountId") accountId: String,
        @Path("projectName") projectName: String
    ): CloudflareResponse<JsonElement>

    @POST("accounts/{accountId}/pages/projects/{projectName}/domains")
    suspend fun createPagesDomain(
        @Path("accountId") accountId: String,
        @Path("projectName") projectName: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>

    @DELETE("accounts/{accountId}/pages/projects/{projectName}/domains/{domainName}")
    suspend fun deletePagesDomain(
        @Path("accountId") accountId: String,
        @Path("projectName") projectName: String,
        @Path("domainName") domainName: String
    ): CloudflareResponse<JsonElement>

    @POST("accounts/{accountId}/pages/projects/{projectName}/domains/{domainName}/retry")
    suspend fun retryPagesDomain(
        @Path("accountId") accountId: String,
        @Path("projectName") projectName: String,
        @Path("domainName") domainName: String
    ): CloudflareResponse<JsonElement>

    // R2
    @GET("accounts/{accountId}/r2/buckets")
    suspend fun listR2Buckets(
        @Path("accountId") accountId: String
    ): CloudflareResponse<JsonElement>

    @POST("accounts/{accountId}/r2/buckets")
    suspend fun createR2Bucket(
        @Path("accountId") accountId: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>

    @DELETE("accounts/{accountId}/r2/buckets/{bucketName}")
    suspend fun deleteR2Bucket(
        @Path("accountId") accountId: String,
        @Path("bucketName") bucketName: String
    ): CloudflareResponse<JsonElement>

    // KV
    @GET("accounts/{accountId}/storage/kv/namespaces")
    suspend fun listKvNamespaces(
        @Path("accountId") accountId: String
    ): CloudflareResponse<JsonElement>

    @POST("accounts/{accountId}/storage/kv/namespaces")
    suspend fun createKvNamespace(
        @Path("accountId") accountId: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>

    @DELETE("accounts/{accountId}/storage/kv/namespaces/{namespaceId}")
    suspend fun deleteKvNamespace(
        @Path("accountId") accountId: String,
        @Path("namespaceId") namespaceId: String
    ): CloudflareResponse<JsonElement>

    @GET("accounts/{accountId}/storage/kv/namespaces/{namespaceId}/keys")
    suspend fun listKvKeys(
        @Path("accountId") accountId: String,
        @Path("namespaceId") namespaceId: String
    ): CloudflareResponse<JsonElement>

    @GET("accounts/{accountId}/storage/kv/namespaces/{namespaceId}/values/{key}")
    suspend fun getKvValue(
        @Path("accountId") accountId: String,
        @Path("namespaceId") namespaceId: String,
        @Path("key") key: String
    ): ResponseBody

    @PUT("accounts/{accountId}/storage/kv/namespaces/{namespaceId}/values/{key}")
    suspend fun putKvValue(
        @Path("accountId") accountId: String,
        @Path("namespaceId") namespaceId: String,
        @Path("key") key: String,
        @Body body: okhttp3.RequestBody
    ): CloudflareResponse<JsonElement>

    @DELETE("accounts/{accountId}/storage/kv/namespaces/{namespaceId}/values/{key}")
    suspend fun deleteKvValue(
        @Path("accountId") accountId: String,
        @Path("namespaceId") namespaceId: String,
        @Path("key") key: String
    ): CloudflareResponse<JsonElement>

    // D1
    @GET("accounts/{accountId}/d1/database")
    suspend fun listD1Databases(
        @Path("accountId") accountId: String
    ): CloudflareResponse<JsonElement>

    @POST("accounts/{accountId}/d1/database")
    suspend fun createD1Database(
        @Path("accountId") accountId: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>

    @DELETE("accounts/{accountId}/d1/database/{databaseId}")
    suspend fun deleteD1Database(
        @Path("accountId") accountId: String,
        @Path("databaseId") databaseId: String
    ): CloudflareResponse<JsonElement>

    @POST("accounts/{accountId}/d1/database/{databaseId}/query")
    suspend fun queryD1Database(
        @Path("accountId") accountId: String,
        @Path("databaseId") databaseId: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>

    // Generic fallback
    @GET("{path}")
    suspend fun genericGet(@Path("path", encoded = true) path: String): CloudflareResponse<JsonElement>

    @POST("{path}")
    suspend fun genericPost(
        @Path("path", encoded = true) path: String,
        @Body body: JsonElement
    ): CloudflareResponse<JsonElement>
}
