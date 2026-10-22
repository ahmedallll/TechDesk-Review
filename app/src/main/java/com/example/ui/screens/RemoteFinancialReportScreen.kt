package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.RemoteTopProductItem
import com.example.ui.AppScreen
import com.example.ui.ScannerUiState
import com.example.ui.ScannerViewModel
import com.example.ui.theme.TechDeskWarning
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteFinancialReportScreen(
    uiState: ScannerUiState,
    viewModel: ScannerViewModel
) {
    BackHandler {
        viewModel.navigateTo(AppScreen.HOME)
    }

    val reportData = uiState.remoteFinancialReportData
    val storeName = uiState.remoteCredentials?.storeName ?: "TechDesk DZ Store"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = storeName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.nav_remote_reports),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.HOME) },
                        modifier = Modifier.testTag("reports_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val (from, to) = calculateDatesForPeriod(uiState.selectedReportPeriod)
                            viewModel.loadRemoteFinancialReport(
                                period = uiState.selectedReportPeriod,
                                fromDate = from,
                                toDate = to
                            )
                        },
                        modifier = Modifier.testTag("reports_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavyHeader)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Offline Warning Banner
            if (uiState.isRemoteStaleCache) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TechDeskWarning.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = TechDeskWarning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.remote_offline_banner, formatTimestamp(uiState.remoteLastSyncTime)),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Period Selector Chips (LazyRow ensures all 4 fit and scroll horizontally if needed)
            item {
                val periodOptions = listOf(
                    "today" to "Aujourd'hui",
                    "this_week" to "Cette semaine",
                    "this_month" to "Ce mois-ci",
                    "all" to "Tous"
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(periodOptions) { (periodKey, periodLabel) ->
                        val isSelected = uiState.selectedReportPeriod == periodKey
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val (from, to) = calculateDatesForPeriod(periodKey)
                                viewModel.loadRemoteFinancialReport(
                                    period = periodKey,
                                    fromDate = from,
                                    toDate = to
                                )
                            },
                            label = { Text(text = periodLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DarkNavyHeader,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("report_chip_$periodKey")
                        )
                    }
                }
            }

            // Loading state indicator when switching periods or initial load
            if (uiState.isRemoteLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = DarkNavyHeader, modifier = Modifier.size(24.dp))
                            Text(
                                text = "Chargement des données...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (reportData != null) {
                // Top Section: 4 Colored Grid Cards
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Card 1: Total Ventes (Blue)
                            DesktopMetricCard(
                                title = "Total Ventes",
                                value = formatDa(reportData.totalRevenue),
                                subtitle = "Chiffre d'affaires brut",
                                icon = Icons.Default.TrendingUp,
                                cardColor = DesktopSalesBlue,
                                modifier = Modifier.weight(1f)
                            )
                            // Card 2: Bénéfice net estimé (Green)
                            DesktopMetricCard(
                                title = "Bénéfice net estimé",
                                value = formatDa(reportData.netProfit),
                                subtitle = "Marge nette calculée",
                                icon = Icons.Default.Payments,
                                cardColor = DesktopCashGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Card 3: Encaissé (Purple)
                            DesktopMetricCard(
                                title = "Encaissé",
                                value = formatDa(reportData.totalCollected),
                                subtitle = "Encaissements effectifs",
                                icon = Icons.Default.AccountBalance,
                                cardColor = DesktopRepairsPurple,
                                modifier = Modifier.weight(1f)
                            )
                            // Card 4: Total Dépenses (Orange)
                            DesktopMetricCard(
                                title = "Total Dépenses",
                                value = formatDa(reportData.operatingExpenses),
                                subtitle = "Charges & débours",
                                icon = Icons.Default.MoneyOff,
                                cardColor = DesktopStockOrange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Financial Comparison Bar Chart
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Comparatif Financier",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FinancialBarChartCard(
                        ventes = reportData.totalRevenue,
                        marge = reportData.grossProfit,
                        encaissements = reportData.totalCollected,
                        depenses = reportData.operatingExpenses
                    )
                }

                // Middle Section: Top 10 des produits classés par bénéfice brut
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Top 10 des produits classés par bénéfice brut",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (reportData.topProducts.isEmpty()) {
                    item {
                        EmptyStateCard(message = "Aucune activité pour cette période")
                    }
                } else {
                    itemsIndexed(reportData.topProducts.take(10)) { index, product ->
                        TopProductRowCard(index = index + 1, product = product)
                    }
                }

            } else if (!uiState.isRemoteLoading) {
                // Not connected state
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Rapports Financiers non Connectés",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Appairez votre téléphone avec le serveur TechDesk DZ pour consulter le bilan, les marges et le top des ventes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { viewModel.navigateTo(AppScreen.REMOTE_PAIRING) },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkNavyHeader)
                            ) {
                                Text(text = "Appairer le Serveur Magasin")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun calculateDatesForPeriod(periodKey: String): Pair<String, String> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val now = Date()
    val todayStr = sdf.format(now)

    return when (periodKey) {
        "today" -> Pair(todayStr, todayStr)
        "this_week" -> {
            val cal = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            Pair(sdf.format(cal.time), todayStr)
        }
        "this_month" -> {
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
            }
            Pair(sdf.format(cal.time), todayStr)
        }
        "all" -> Pair("2000-01-01", "2099-12-31")
        else -> Pair(todayStr, todayStr)
    }
}

@Composable
fun TopProductRowCard(index: Int, product: RemoteTopProductItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Circle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (index <= 3) DesktopCashGreen else DarkNavyHeader),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$index",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "SKU: ${product.sku}  •  Qte vendue: ${product.qty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Bénéfice: ${formatDa(product.grossProfit)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = DesktopCashGreen
                )
                Text(
                    text = "Ventes: ${formatDa(product.netSales)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FinancialBarChartCard(
    ventes: Double,
    marge: Double,
    encaissements: Double,
    depenses: Double
) {
    val items = listOf(
        "Ventes Nettes" to (ventes to DesktopSalesBlue),
        "Marge Brute" to (marge to DesktopCashGreen),
        "Encaissé" to (encaissements to DesktopRepairsPurple),
        "Dépenses" to (depenses to DesktopStockOrange)
    )

    val maxVal = maxOf(ventes, marge, encaissements, depenses).coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val width = size.width
                val height = size.height
                val barGroupWidth = width / items.size
                val barWidth = barGroupWidth * 0.45f

                items.forEachIndexed { index, (label, valueAndColor) ->
                    val (valDouble, color) = valueAndColor
                    val barHeight = ((valDouble / maxVal) * (height - 24.dp.toPx())).toFloat().coerceAtLeast(6.dp.toPx())
                    val xPos = index * barGroupWidth + (barGroupWidth - barWidth) / 2

                    drawRect(
                        color = color,
                        topLeft = Offset(xPos, height - barHeight),
                        size = Size(barWidth, barHeight)
                    )
                }
            }

            // Labels under canvas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                items.forEach { (label, valueAndColor) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(valueAndColor.second)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDa(valueAndColor.first),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
