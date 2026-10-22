package com.example.data.network

import com.example.data.model.RemoteDashboardData
import com.example.data.model.RemoteFinancialReportData
import com.example.data.model.RemotePairRequest
import com.example.data.model.RemotePairResponse
import com.example.data.model.RemoteSaleDetail
import com.example.data.model.RemoteSyncStatus
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface RemoteApi {

    @POST("api/mobile/remote/pair")
    suspend fun pairRemote(
        @Body request: RemotePairRequest
    ): RemotePairResponse

    @GET("api/mobile/remote/dashboard")
    suspend fun getDashboard(
        @Header("Authorization") authHeader: String,
        @Query("period") period: String? = "today"
    ): RemoteDashboardData

    @GET("api/mobile/remote/financial-report")
    suspend fun getFinancialReport(
        @Header("Authorization") authHeader: String,
        @Query("period") period: String? = null,
        @Query("from") fromDate: String? = null,
        @Query("to") toDate: String? = null
    ): RemoteFinancialReportData

    @GET("api/mobile/remote/sync-status")
    suspend fun getSyncStatus(
        @Header("Authorization") authHeader: String
    ): RemoteSyncStatus

    @GET("api/mobile/remote/sale/{reference}")
    suspend fun getSaleDetails(
        @Header("Authorization") authHeader: String,
        @Path("reference") reference: String
    ): RemoteSaleDetail
}

class FlexibleDoubleAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Double {
        return when (reader.peek()) {
            JsonReader.Token.NUMBER -> reader.nextDouble()
            JsonReader.Token.STRING -> {
                val str = reader.nextString().replace(" ", "").replace("DA", "", ignoreCase = true).replace(",", ".").trim()
                str.toDoubleOrNull() ?: 0.0
            }
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                0.0
            }
            else -> {
                reader.skipValue()
                0.0
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: Double) {
        writer.value(value)
    }
}

class FlexibleIntAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Int {
        return when (reader.peek()) {
            JsonReader.Token.NUMBER -> reader.nextInt()
            JsonReader.Token.STRING -> {
                val str = reader.nextString().replace(" ", "").trim()
                str.toIntOrNull() ?: 0
            }
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                0
            }
            else -> {
                reader.skipValue()
                0
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: Int) {
        writer.value(value)
    }
}

class RemoteStoreApiClient {

    private val moshi = Moshi.Builder()
        .add(FlexibleDoubleAdapter())
        .add(FlexibleIntAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private fun formatBaseUrl(baseUrl: String): String {
        var clean = baseUrl.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        if (!clean.endsWith("/")) {
            clean = "$clean/"
        }
        return clean
    }

    fun createService(baseUrl: String): RemoteApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(formatBaseUrl(baseUrl))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(RemoteApi::class.java)
    }
}
