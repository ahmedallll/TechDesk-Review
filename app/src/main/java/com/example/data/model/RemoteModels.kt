package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemotePairRequest(
    @Json(name = "installationReference") val installationReference: String,
    @Json(name = "pairingCode") val pairingCode: String,
    @Json(name = "deviceName") val deviceName: String
)

@JsonClass(generateAdapter = true)
data class RemotePairResponse(
    @Json(name = "status") val status: String? = "success",
    @Json(name = "storeName") val storeName: String? = null,
    @Json(name = "remoteToken") val remoteToken: String? = null,
    @Json(name = "token") val token: String? = null,
    @Json(name = "access_token") val access_token: String? = null,
    @Json(name = "authToken") val authToken: String? = null,
    @Json(name = "serverVersion") val serverVersion: String? = "1.0.0",
    @Json(name = "installationReference") val installationReference: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "error") val error: String? = null
) {
    val effectiveToken: String?
        get() = remoteToken ?: token ?: access_token ?: authToken
}

data class RemoteCredentials(
    val serverUrl: String,
    val remoteToken: String,
    val storeName: String,
    val installationReference: String
)

@JsonClass(generateAdapter = true)
data class RemoteSaleItem(
    @Json(name = "reference") val referenceRaw: String? = null,
    @Json(name = "id") val idRaw: String? = null,
    @Json(name = "client") val clientRaw: String? = null,
    @Json(name = "customerName") val customerNameRaw: String? = null,
    @Json(name = "time") val timeRaw: String? = null,
    @Json(name = "timestamp") val timestampRaw: Long? = null,
    @Json(name = "totalAmount") val totalAmountRaw: Double? = null,
    @Json(name = "amount") val amountRaw: Double? = null,
    @Json(name = "paidAmount") val paidAmountRaw: Double? = null,
    @Json(name = "paid") val paidRaw: Double? = null,
    @Json(name = "status") val statusRaw: String? = "Payé"
) {
    val reference: String get() = referenceRaw ?: idRaw ?: "VTE-2026-001"
    val client: String get() = clientRaw ?: customerNameRaw ?: "Client Passage"
    val time: String get() = timeRaw ?: "Aujourd'hui"
    val totalAmount: Double get() = totalAmountRaw ?: amountRaw ?: 0.0
    val paidAmount: Double get() = paidAmountRaw ?: paidRaw ?: totalAmount
    val status: String get() = statusRaw ?: "Payé"
}

@JsonClass(generateAdapter = true)
data class RemoteSavItem(
    @Json(name = "ticket") val ticketRaw: String? = null,
    @Json(name = "id") val idRaw: String? = null,
    @Json(name = "device") val deviceRaw: String? = null,
    @Json(name = "apparel") val apparelRaw: String? = null,
    @Json(name = "description") val descriptionRaw: String? = null,
    @Json(name = "client") val clientRaw: String? = null,
    @Json(name = "customerName") val customerNameRaw: String? = null,
    @Json(name = "totalAmount") val totalAmountRaw: Double? = null,
    @Json(name = "amount") val amountRaw: Double? = null,
    @Json(name = "remainingAmount") val remainingAmountRaw: Double? = null,
    @Json(name = "remaining") val remainingRaw: Double? = null,
    @Json(name = "status") val statusRaw: String? = "En atelier"
) {
    val ticket: String get() = ticketRaw ?: idRaw ?: "SAV-2026-001"
    val device: String get() = deviceRaw ?: apparelRaw ?: descriptionRaw ?: "Appareil Client"
    val client: String get() = clientRaw ?: customerNameRaw ?: "Client SAV"
    val totalAmount: Double get() = totalAmountRaw ?: amountRaw ?: 0.0
    val remainingAmount: Double get() = remainingAmountRaw ?: remainingRaw ?: 0.0
    val status: String get() = statusRaw ?: "En atelier"
}

@JsonClass(generateAdapter = true)
data class RecentActivityItem(
    @Json(name = "id") val id: String = "",
    @Json(name = "type") val type: String = "sale",
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String = "",
    @Json(name = "amount") val amount: Double? = 0.0,
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @Json(name = "status") val status: String = "completed"
)

@JsonClass(generateAdapter = true)
data class RemoteDashboardData(
    @Json(name = "storeName") val storeName: String = "TechDesk DZ Store",
    @Json(name = "lastSyncTimestamp") val lastSyncTimestamp: Long = System.currentTimeMillis(),
    @Json(name = "period") val period: String = "today",

    // Ventes du jour / Total Sales
    @Json(name = "totalSalesToday") val totalSalesTodayRaw: Double? = null,
    @Json(name = "salesAmount") val salesAmountRaw: Double? = null,
    @Json(name = "totalSales") val totalSalesRaw: Double? = null,

    // Transaction count
    @Json(name = "salesCount") val salesCountRaw: Int? = null,
    @Json(name = "totalSalesCount") val totalSalesCountRaw: Int? = null,
    @Json(name = "transactionsCount") val transactionsCountRaw: Int? = null,

    // Encaissé aujourd'hui / Cash
    @Json(name = "totalCollectedToday") val totalCollectedTodayRaw: Double? = null,
    @Json(name = "cashDrawerBalance") val cashDrawerBalanceRaw: Double? = null,
    @Json(name = "cashCollected") val cashCollectedRaw: Double? = null,

    // Dépenses / Expenses
    @Json(name = "dailyExpenses") val dailyExpensesRaw: Double? = null,
    @Json(name = "totalExpensesToday") val totalExpensesTodayRaw: Double? = null,

    // Alertes stock
    @Json(name = "lowStockCount") val lowStockCountRaw: Int? = null,
    @Json(name = "stockAlerts") val stockAlertsRaw: Int? = null,

    // Réparations en cours
    @Json(name = "pendingRepairsCount") val pendingRepairsCountRaw: Int? = null,
    @Json(name = "ongoingRepairs") val ongoingRepairsRaw: Int? = null,

    // Lists
    @Json(name = "recentSales") val recentSalesRaw: List<RemoteSaleItem>? = null,
    @Json(name = "recentSavInterventions") val recentSavRaw: List<RemoteSavItem>? = null,
    @Json(name = "recentActivities") val recentActivitiesRaw: List<RecentActivityItem>? = null
) {
    val salesAmount: Double get() = totalSalesTodayRaw ?: salesAmountRaw ?: totalSalesRaw ?: 0.0
    val salesCount: Int get() = salesCountRaw ?: totalSalesCountRaw ?: transactionsCountRaw ?: (recentSalesRaw?.size ?: 0)
    val cashDrawerBalance: Double get() = totalCollectedTodayRaw ?: cashDrawerBalanceRaw ?: cashCollectedRaw ?: salesAmount
    val dailyExpenses: Double get() = dailyExpensesRaw ?: totalExpensesTodayRaw ?: 0.0
    val lowStockCount: Int get() = lowStockCountRaw ?: stockAlertsRaw ?: 0
    val pendingRepairsCount: Int get() = pendingRepairsCountRaw ?: ongoingRepairsRaw ?: 0

    val recentSales: List<RemoteSaleItem> get() = recentSalesRaw ?: emptyList()
    val recentSavInterventions: List<RemoteSavItem> get() = recentSavRaw ?: emptyList()
    val recentActivities: List<RecentActivityItem> get() = recentActivitiesRaw ?: emptyList()
}

@JsonClass(generateAdapter = true)
data class RemoteTopProductItem(
    @Json(name = "rank") val rankRaw: Int? = null,
    @Json(name = "name") val nameRaw: String? = null,
    @Json(name = "productName") val productNameRaw: String? = null,
    @Json(name = "sku") val skuRaw: String? = null,
    @Json(name = "barcode") val barcodeRaw: String? = null,
    @Json(name = "qty") val qtyRaw: Int? = null,
    @Json(name = "quantitySold") val quantitySoldRaw: Int? = null,
    @Json(name = "netSales") val netSalesRaw: Double? = null,
    @Json(name = "totalRevenue") val totalRevenueRaw: Double? = null,
    @Json(name = "grossProfit") val grossProfitRaw: Double? = null,
    @Json(name = "profit") val profitRaw: Double? = null
) {
    val rank: Int get() = rankRaw ?: 1
    val name: String get() = nameRaw ?: productNameRaw ?: "Produit"
    val sku: String get() = skuRaw ?: barcodeRaw ?: "-"
    val qty: Int get() = qtyRaw ?: quantitySoldRaw ?: 0
    val netSales: Double get() = netSalesRaw ?: totalRevenueRaw ?: 0.0
    val grossProfit: Double get() = grossProfitRaw ?: profitRaw ?: 0.0
}

@JsonClass(generateAdapter = true)
data class PeriodRevenuePoint(
    @Json(name = "label") val label: String,
    @Json(name = "revenue") val revenue: Double = 0.0,
    @Json(name = "expenses") val expenses: Double = 0.0,
    @Json(name = "netProfit") val netProfit: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class RemoteFinancialReportData(
    @Json(name = "storeName") val storeName: String = "TechDesk DZ Store",
    @Json(name = "period") val period: String = "this_month",
    @Json(name = "fromDate") val fromDate: String = "",
    @Json(name = "toDate") val toDate: String = "",

    // Total Ventes
    @Json(name = "totalRevenue") val totalRevenueRaw: Double? = null,
    @Json(name = "totalSales") val totalSalesRaw: Double? = null,

    // Bénéfice net estimé
    @Json(name = "netProfit") val netProfitRaw: Double? = null,
    @Json(name = "estimatedNetProfit") val estimatedNetProfitRaw: Double? = null,

    // Encaissé
    @Json(name = "totalCollected") val totalCollectedRaw: Double? = null,
    @Json(name = "collectedAmount") val collectedAmountRaw: Double? = null,

    // Dépenses
    @Json(name = "operatingExpenses") val operatingExpensesRaw: Double? = null,
    @Json(name = "totalExpenses") val totalExpensesRaw: Double? = null,

    @Json(name = "cogs") val cogsRaw: Double? = null,
    @Json(name = "grossProfit") val grossProfitRaw: Double? = null,
    @Json(name = "grossProfitMargin") val grossProfitMarginRaw: Double? = null,
    @Json(name = "customerReceivables") val customerReceivablesRaw: Double? = null,
    @Json(name = "supplierPayables") val supplierPayablesRaw: Double? = null,

    @Json(name = "topProducts") val topProductsRaw: List<RemoteTopProductItem>? = null,
    @Json(name = "revenueTrend") val revenueTrendRaw: List<PeriodRevenuePoint>? = null
) {
    val totalRevenue: Double get() = totalRevenueRaw ?: totalSalesRaw ?: 0.0
    val netProfit: Double get() = netProfitRaw ?: estimatedNetProfitRaw ?: (grossProfit - operatingExpenses)
    val totalCollected: Double get() = totalCollectedRaw ?: collectedAmountRaw ?: totalRevenue
    val operatingExpenses: Double get() = operatingExpensesRaw ?: totalExpensesRaw ?: 0.0
    val cogs: Double get() = cogsRaw ?: 0.0
    val grossProfit: Double get() = grossProfitRaw ?: (totalRevenue - cogs)
    val grossProfitMargin: Double get() = grossProfitMarginRaw ?: if (totalRevenue > 0) ((grossProfit / totalRevenue) * 100) else 0.0
    val customerReceivables: Double get() = customerReceivablesRaw ?: 0.0
    val supplierPayables: Double get() = supplierPayablesRaw ?: 0.0

    val topProducts: List<RemoteTopProductItem> get() = topProductsRaw ?: emptyList()
    val revenueTrend: List<PeriodRevenuePoint> get() = revenueTrendRaw ?: emptyList()
}

@JsonClass(generateAdapter = true)
data class RemoteSyncStatus(
    @Json(name = "isOnline") val isOnline: Boolean = true,
    @Json(name = "storeName") val storeName: String = "TechDesk DZ Store",
    @Json(name = "lastDesktopSync") val lastDesktopSync: Long = System.currentTimeMillis(),
    @Json(name = "version") val version: String = "1.0.0"
)

@JsonClass(generateAdapter = true)
data class SaleItem(
    @Json(name = "description") val descriptionRaw: String? = null,
    @Json(name = "name") val nameRaw: String? = null,
    @Json(name = "title") val titleRaw: String? = null,
    @Json(name = "quantity") val quantityRaw: Int? = null,
    @Json(name = "qty") val qtyRaw: Int? = null,
    @Json(name = "price") val priceRaw: Double? = null,
    @Json(name = "unitPrice") val unitPriceRaw: Double? = null,
    @Json(name = "total") val totalRaw: Double? = null,
    @Json(name = "totalPrice") val totalPriceRaw: Double? = null
) {
    val description: String get() = descriptionRaw ?: nameRaw ?: titleRaw ?: "Article"
    val quantity: Int get() = quantityRaw ?: qtyRaw ?: 1
    val price: Double get() = priceRaw ?: unitPriceRaw ?: 0.0
    val total: Double get() = totalRaw ?: totalPriceRaw ?: (quantity * price)
}

@JsonClass(generateAdapter = true)
data class RemoteSaleDetail(
    @Json(name = "reference") val referenceRaw: String? = null,
    @Json(name = "client") val clientRaw: String? = null,
    @Json(name = "customerName") val customerNameRaw: String? = null,
    @Json(name = "date") val dateRaw: String? = null,
    @Json(name = "time") val timeRaw: String? = null,
    @Json(name = "paymentMethod") val paymentMethodRaw: String? = null,
    @Json(name = "grandTotal") val grandTotalRaw: Double? = null,
    @Json(name = "totalAmount") val totalAmountRaw: Double? = null,
    @Json(name = "paidAmount") val paidAmountRaw: Double? = null,
    @Json(name = "paid") val paidRaw: Double? = null,
    @Json(name = "items") val itemsRaw: List<SaleItem>? = null
) {
    val reference: String get() = referenceRaw ?: "VTE-0000"
    val client: String get() = clientRaw ?: customerNameRaw ?: "Client Passage"
    val date: String get() = dateRaw ?: timeRaw ?: "Aujourd'hui"
    val paymentMethod: String get() = paymentMethodRaw ?: "Espèces"
    val grandTotal: Double get() = grandTotalRaw ?: totalAmountRaw ?: paidAmount
    val paidAmount: Double get() = paidAmountRaw ?: paidRaw ?: grandTotal
    val items: List<SaleItem> get() = itemsRaw ?: emptyList()
}
