package com.cloudflare.manager.data.repository

import android.util.Log
import com.cloudflare.manager.data.remote.CloudflareApiService
import com.cloudflare.manager.data.remote.errorMessage
import com.cloudflare.manager.domain.model.Zone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZoneRepository @Inject constructor(
    private val apiService: CloudflareApiService
) {
    suspend fun listZones(): Result<List<Zone>> {
        return try {
            val response = apiService.listZones()
            Log.d("ZoneRepository", "API success=${response.success}, result size=${response.result?.size}")
            if (response.success) {
                val zones = response.result.orEmpty().map { dto ->
                    Zone(
                        id = dto.id,
                        name = dto.name,
                        status = dto.status,
                        planName = dto.plan?.name ?: "Free",
                        nameServer = dto.name_servers?.firstOrNull(),
                        accountId = dto.account?.id
                    )
                }
                Log.d("ZoneRepository", "Mapped ${zones.size} zones")
                Result.success(zones)
            } else {
                Log.e("ZoneRepository", "API error: ${response.errorMessage()}")
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Log.e("ZoneRepository", "Exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
