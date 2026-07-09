package com.cloudflare.manager.data.repository

import com.cloudflare.manager.data.remote.CloudflareApiService
import com.cloudflare.manager.data.remote.dto.DnsRecordRequest
import com.cloudflare.manager.data.remote.errorMessage
import com.cloudflare.manager.domain.model.DnsRecord
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsRepository @Inject constructor(
    private val apiService: CloudflareApiService
) {
    suspend fun listRecords(zoneId: String): Result<List<DnsRecord>> {
        return try {
            val response = apiService.listDnsRecords(zoneId)
            if (response.success) {
                val records = response.result.orEmpty().map { dto ->
                    DnsRecord(
                        id = dto.id,
                        type = dto.type,
                        name = dto.name,
                        content = dto.content,
                        ttl = dto.ttl,
                        proxied = dto.proxied,
                        zoneId = dto.zone_id ?: zoneId
                    )
                }
                Result.success(records)
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRecord(zoneId: String, record: DnsRecord): Result<DnsRecord> {
        return try {
            val request = DnsRecordRequest(
                type = record.type,
                name = record.name,
                content = record.content,
                ttl = record.ttl,
                proxied = record.proxied
            )
            val response = apiService.createDnsRecord(zoneId, request)
            if (response.success && response.result != null) {
                Result.success(
                    DnsRecord(
                        id = response.result.id,
                        type = response.result.type,
                        name = response.result.name,
                        content = response.result.content,
                        ttl = response.result.ttl,
                        proxied = response.result.proxied,
                        zoneId = zoneId
                    )
                )
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateRecord(zoneId: String, record: DnsRecord): Result<DnsRecord> {
        return try {
            val request = DnsRecordRequest(
                type = record.type,
                name = record.name,
                content = record.content,
                ttl = record.ttl,
                proxied = record.proxied
            )
            val response = apiService.updateDnsRecord(zoneId, record.id, request)
            if (response.success && response.result != null) {
                Result.success(
                    DnsRecord(
                        id = response.result.id,
                        type = response.result.type,
                        name = response.result.name,
                        content = response.result.content,
                        ttl = response.result.ttl,
                        proxied = response.result.proxied,
                        zoneId = zoneId
                    )
                )
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRecord(zoneId: String, recordId: String): Result<Unit> {
        return try {
            val response = apiService.deleteDnsRecord(zoneId, recordId)
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
