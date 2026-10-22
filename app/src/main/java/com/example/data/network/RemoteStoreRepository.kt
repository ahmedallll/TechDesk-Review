package com.example.data.network

import android.content.Context
import android.util.Log
import com.example.data.model.RemoteCredentials
import com.example.data.model.RemoteDashboardData
import com.example.data.model.RemoteFinancialReportData
import com.example.data.model.RemotePairRequest
import com.example.data.model.RemotePairResponse
import com.example.data.model.RemoteSaleDetail
import com.example.data.security.RemoteSecureStorage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

sealed class RemoteResult<out T> {
    data class Success<T>(val data: T, val isFromCache: Boolean = false) : RemoteResult<T>()
    data class Error(val message: String, val isAuthError: Boolean = false) : RemoteResult<Nothing>()
}

class RemoteStoreRepository(
    private val context: Context,
    private val secureStorage: RemoteSecureStorage = RemoteSecureStorage(context),
    private val apiClient: RemoteStoreApiClient = RemoteStoreApiClient()
) {
    private val cachePrefs = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder()
        .add(FlexibleDoubleAdapter())
        .add(FlexibleIntAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val dashboardAdapter = moshi.adapter(RemoteDashboardData::class.java)
    private val reportAdapter = moshi.adapter(RemoteFinancialReportData::class.java)

    companion object {
        private const val TAG = "RemoteRepository"
        private const val CACHE_PREFS_NAME = "techdesk_remote_cache"
        private const val KEY_DASHBOARD_CACHE = "cached_dashboard"
        private const val KEY_REPORT_CACHE = "cached_report_"
        private const val KEY_LAST_SYNC_TIME = "last_sync_timestamp"
    }

    fun getStoredCredentials(): RemoteCredentials? {
        return secureStorage.getRemoteCredentials()
    }

    fun clearCredentials() {
        secureStorage.clearRemoteCredentials()
        cachePrefs.edit().clear().apply()
    }

    suspend fun pairRemoteStore(
        serverUrl: String,
        ref: String,
        code: String,
        deviceName: String = "Android Store Monitor"
    ): RemoteResult<RemoteCredentials> = withContext(Dispatchers.IO) {
        try {
            val api = apiClient.createService(serverUrl)
            val request = RemotePairRequest(
                installationReference = ref,
                pairingCode = code,
                deviceName = deviceName
            )
            val response: RemotePairResponse = api.pairRemote(request)

            val token = response.effectiveToken ?: ""
            if (token.isBlank()) {
                val err = response.message ?: response.error ?: "Pairing failed: Invalid server credentials"
                return@withContext RemoteResult.Error(err)
            }

            val storeName = response.storeName ?: "TechDesk DZ Store"
            val credentials = RemoteCredentials(
                serverUrl = serverUrl.trim(),
                remoteToken = token,
                storeName = storeName,
                installationReference = ref
            )

            secureStorage.saveRemoteCredentials(credentials)
            RemoteResult.Success(credentials)
        } catch (e: HttpException) {
            val statusCode = e.code()
            val errorBody = try {
                e.response()?.errorBody()?.string()
            } catch (ex: Exception) {
                null
            }
            Log.e(TAG, "HTTP $statusCode Remote pairing failed. Error body: $errorBody", e)

            var serverMsg: String? = null
            if (!errorBody.isNullOrBlank()) {
                try {
                    val jsonAdapter = moshi.adapter(RemotePairResponse::class.java)
                    val errResp = jsonAdapter.fromJson(errorBody)
                    serverMsg = errResp?.message ?: errResp?.error
                } catch (_: Exception) {
                    serverMsg = errorBody.take(200)
                }
            }

            val finalMessage = when (statusCode) {
                401 -> serverMsg ?: "HTTP 401 Unauthorized: Invalid installation reference or pairing code."
                403 -> serverMsg ?: "HTTP 403 Forbidden: Remote pairing disabled on desktop server."
                404 -> serverMsg ?: "HTTP 404 Not Found: Remote endpoint not found on server."
                else -> serverMsg ?: "Server returned HTTP error $statusCode"
            }

            RemoteResult.Error(finalMessage, isAuthError = (statusCode == 401 || statusCode == 403))
        } catch (e: Exception) {
            Log.e(TAG, "Remote pairing error", e)
            RemoteResult.Error(e.message ?: "Network error connecting to remote store server")
        }
    }

    suspend fun fetchDashboard(
        period: String = "today",
        forceRefresh: Boolean = false
    ): RemoteResult<RemoteDashboardData> = withContext(Dispatchers.IO) {
        val creds = secureStorage.getRemoteCredentials()
            ?: return@withContext RemoteResult.Error("Remote store is not paired", isAuthError = true)

        try {
            val api = apiClient.createService(creds.serverUrl)
            val authHeader = "Bearer ${creds.remoteToken}"
            val data = api.getDashboard(authHeader = authHeader, period = period)

            // Cache successful response locally
            val json = dashboardAdapter.toJson(data)
            cachePrefs.edit()
                .putString(KEY_DASHBOARD_CACHE, json)
                .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                .apply()

            RemoteResult.Success(data, isFromCache = false)
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error fetching remote dashboard: ${e.code()}", e)
            if (e.code() == 401 || e.code() == 403) {
                RemoteResult.Error("Session expired or unauthorized. Please re-pair Remote Access.", isAuthError = true)
            } else {
                getCachedDashboardOrError("Server returned error ${e.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching remote dashboard", e)
            getCachedDashboardOrError("Offline or server unreachable: ${e.message}")
        }
    }

    suspend fun fetchFinancialReport(
        period: String = "this_month",
        fromDate: String? = null,
        toDate: String? = null
    ): RemoteResult<RemoteFinancialReportData> = withContext(Dispatchers.IO) {
        val creds = secureStorage.getRemoteCredentials()
            ?: return@withContext RemoteResult.Error("Remote store is not paired", isAuthError = true)

        try {
            val api = apiClient.createService(creds.serverUrl)
            val authHeader = "Bearer ${creds.remoteToken}"
            val data = api.getFinancialReport(
                authHeader = authHeader,
                period = period,
                fromDate = fromDate,
                toDate = toDate
            )

            // Cache report for this period
            val json = reportAdapter.toJson(data)
            cachePrefs.edit().putString("$KEY_REPORT_CACHE$period", json).apply()

            RemoteResult.Success(data, isFromCache = false)
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error fetching financial report: ${e.code()}", e)
            if (e.code() == 401 || e.code() == 403) {
                RemoteResult.Error("Session expired or unauthorized. Please re-pair Remote Access.", isAuthError = true)
            } else {
                getCachedReportOrError(period, "Server returned error ${e.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching financial report", e)
            getCachedReportOrError(period, "Offline or server unreachable: ${e.message}")
        }
    }

    private fun getCachedDashboardOrError(fallbackMsg: String): RemoteResult<RemoteDashboardData> {
        val cachedJson = cachePrefs.getString(KEY_DASHBOARD_CACHE, null)
        if (cachedJson != null) {
            try {
                val cachedData = dashboardAdapter.fromJson(cachedJson)
                if (cachedData != null) {
                    return RemoteResult.Success(cachedData, isFromCache = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cached dashboard", e)
            }
        }
        return RemoteResult.Error(fallbackMsg)
    }

    private fun getCachedReportOrError(period: String, fallbackMsg: String): RemoteResult<RemoteFinancialReportData> {
        val cachedJson = cachePrefs.getString("$KEY_REPORT_CACHE$period", null)
        if (cachedJson != null) {
            try {
                val cachedData = reportAdapter.fromJson(cachedJson)
                if (cachedData != null) {
                    return RemoteResult.Success(cachedData, isFromCache = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cached financial report", e)
            }
        }
        return RemoteResult.Error(fallbackMsg)
    }

    suspend fun fetchSaleDetails(reference: String): RemoteResult<RemoteSaleDetail> = withContext(Dispatchers.IO) {
        val creds = secureStorage.getRemoteCredentials()
            ?: return@withContext RemoteResult.Error("Remote store is not paired", isAuthError = true)

        try {
            val api = apiClient.createService(creds.serverUrl)
            val authHeader = "Bearer ${creds.remoteToken}"
            val data = api.getSaleDetails(authHeader = authHeader, reference = reference)
            RemoteResult.Success(data)
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error fetching sale details for $reference: ${e.code()}", e)
            RemoteResult.Error("HTTP error ${e.code()}")
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching sale details for $reference", e)
            RemoteResult.Error("Network error: ${e.message}")
        }
    }

    fun getLastSyncTimestamp(): Long {
        return cachePrefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }
}
