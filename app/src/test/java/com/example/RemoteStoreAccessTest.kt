package com.example

import com.example.data.model.RemoteCredentials
import com.example.data.model.RemoteDashboardData
import com.example.data.model.RemoteFinancialReportData
import com.example.data.model.RemotePairRequest
import com.example.data.model.RemotePairResponse
import com.example.data.model.PeriodRevenuePoint
import com.example.data.model.RecentActivityItem
import com.example.ui.screens.formatDaCurrency
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteStoreAccessTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun testRemotePairRequestAndResponseSerialization() {
        val requestAdapter = moshi.adapter(RemotePairRequest::class.java)
        val req = RemotePairRequest(
            installationReference = "TDZ-A7K29M",
            pairingCode = "849201",
            deviceName = "Phone Monitor"
        )
        val jsonReq = requestAdapter.toJson(req)
        assertTrue(jsonReq.contains("TDZ-A7K29M"))
        assertTrue(jsonReq.contains("849201"))

        val responseAdapter = moshi.adapter(RemotePairResponse::class.java)
        val rawJsonResp = """{
            "status": "success",
            "storeName": "TechDesk DZ Alger Center",
            "remoteToken": "sec_token_9988776655",
            "serverVersion": "1.2.0"
        }"""
        val resp = responseAdapter.fromJson(rawJsonResp)
        assertNotNull(resp)
        assertEquals("success", resp?.status)
        assertEquals("TechDesk DZ Alger Center", resp?.storeName)
        assertEquals("sec_token_9988776655", resp?.remoteToken)
    }

    @Test
    fun testRemoteDashboardDataParsing() {
        val adapter = moshi.adapter(RemoteDashboardData::class.java)
        val rawJson = """{
            "storeName": "TechDesk DZ Bab Ezzouar",
            "lastSyncTimestamp": 1700000000000,
            "period": "today",
            "salesAmount": 245000.0,
            "salesCount": 18,
            "cashDrawerBalance": 180000.0,
            "dailyExpenses": 15000.0,
            "lowStockCount": 3,
            "pendingRepairsCount": 5,
            "pendingDeliveriesCount": 2,
            "recentActivities": [
                {
                    "id": "act-01",
                    "type": "sale",
                    "title": "Vente POS #1024",
                    "description": "2x Imprimante Xprinter",
                    "amount": 34000.0
                }
            ]
        }"""

        val data = adapter.fromJson(rawJson)
        assertNotNull(data)
        assertEquals("TechDesk DZ Bab Ezzouar", data?.storeName)
        assertEquals(245000.0, data?.salesAmount ?: 0.0, 0.01)
        assertEquals(18, data?.salesCount)
        assertEquals(180000.0, data?.cashDrawerBalance ?: 0.0, 0.01)
        assertEquals(1, data?.recentActivities?.size)
        assertEquals("Vente POS #1024", data?.recentActivities?.first()?.title)
    }

    @Test
    fun testRemoteFinancialReportDataParsing() {
        val adapter = moshi.adapter(RemoteFinancialReportData::class.java)
        val rawJson = """{
            "storeName": "TechDesk DZ Oran",
            "period": "this_month",
            "totalRevenue": 1850000.0,
            "cogs": 1200000.0,
            "grossProfit": 650000.0,
            "grossProfitMargin": 35.1,
            "operatingExpenses": 180000.0,
            "netProfit": 470000.0,
            "customerReceivables": 95000.0,
            "supplierPayables": 140000.0,
            "revenueTrend": [
                {"label": "Semaine 1", "revenue": 450000.0, "expenses": 40000.0, "netProfit": 110000.0},
                {"label": "Semaine 2", "revenue": 520000.0, "expenses": 450000.0, "netProfit": 125000.0}
            ]
        }"""

        val report = adapter.fromJson(rawJson)
        assertNotNull(report)
        assertEquals("TechDesk DZ Oran", report?.storeName)
        assertEquals(1850000.0, report?.totalRevenue ?: 0.0, 0.01)
        assertEquals(650000.0, report?.grossProfit ?: 0.0, 0.01)
        assertEquals(35.1, report?.grossProfitMargin ?: 0.0, 0.01)
        assertEquals(470000.0, report?.netProfit ?: 0.0, 0.01)
        assertEquals(2, report?.revenueTrend?.size)
    }

    @Test
    fun testDaCurrencyFormatting() {
        val formattedZero = formatDaCurrency(0.0)
        assertTrue(formattedZero.contains("DA"))

        val formattedAmount = formatDaCurrency(125000.0)
        assertTrue(formattedAmount.contains("DA"))
        assertTrue(formattedAmount.contains("125"))
    }
}
