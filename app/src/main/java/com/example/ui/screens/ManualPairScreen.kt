package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.AppScreen
import com.example.ui.ScannerUiState
import com.example.ui.ScannerViewModel
import com.example.ui.theme.TechDeskError
import com.example.ui.theme.TechDeskLaser
import com.example.ui.theme.TechDeskNavy
import com.example.ui.theme.TechDeskPrimary
import com.example.ui.theme.TechDeskWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualPairScreen(
    uiState: ScannerUiState,
    viewModel: ScannerViewModel
) {
    BackHandler {
        viewModel.navigateTo(AppScreen.HOME)
    }

    var ref by remember { mutableStateOf(uiState.credentials?.installationReference ?: "") }
    var code by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf(uiState.credentials?.host ?: "") }
    var port by remember { mutableStateOf(uiState.credentials?.port?.toString() ?: "3000") }
    var localError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Pair Your TechDesk Desktop") },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.HOME) },
                        modifier = Modifier.testTag("back_from_pair_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Local Network Interface Status Banner (small informational section)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.tetheringStatus.isTetheringDetected) TechDeskNavy else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = null,
                        tint = if (uiState.tetheringStatus.isTetheringDetected) TechDeskLaser else TechDeskWarning,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.tetheringStatus.isTetheringDetected) {
                                "Local Network Active: ${uiState.tetheringStatus.candidateInterface}"
                            } else {
                                stringResource(R.string.tethering_not_detected)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.tetheringStatus.isTetheringDetected) TechDeskLaser else MaterialTheme.colorScheme.onSurface
                        )
                        if (uiState.tetheringStatus.isTetheringDetected) {
                            Text(
                                text = "IP: ${uiState.tetheringStatus.candidateIp}/${uiState.tetheringStatus.candidatePrefixLength}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            // 1. Installation Reference
            OutlinedTextField(
                value = ref,
                onValueChange = { ref = it; localError = null },
                label = { Text("Installation Reference") },
                placeholder = { Text("e.g. TDZ-XXXXXX") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_ref"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
            )

            // 2. Pairing Code
            OutlinedTextField(
                value = code,
                onValueChange = { code = it; localError = null },
                label = { Text("Pairing Code") },
                placeholder = { Text("Enter pairing code") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_code"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // 3. PC IP Address
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it; localError = null },
                label = { Text("PC IP Address") },
                placeholder = { Text("e.g. 192.168.84.63") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_ip"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            // 4. Port
            OutlinedTextField(
                value = port,
                onValueChange = { port = it; localError = null },
                label = { Text("Port") },
                placeholder = { Text("3000") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_port"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // Error display
            val displayError = localError ?: uiState.pairingErrorMessage
            if (displayError != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TechDeskError.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = TechDeskError
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayError,
                            color = TechDeskError,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Progress status display
            if (uiState.isPairingInProgress) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TechDeskNavy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = TechDeskLaser,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = uiState.statusMessage ?: "Pairing…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Primary Button: Pair
            Button(
                onClick = {
                    if (ref.isBlank()) {
                        localError = "Installation Reference is required"
                        return@Button
                    }
                    if (code.isBlank()) {
                        localError = "Pairing Code is required"
                        return@Button
                    }
                    if (ip.isBlank()) {
                        localError = "PC IP Address is required"
                        return@Button
                    }
                    if (port.isBlank()) {
                        localError = "Port is required"
                        return@Button
                    }
                    viewModel.pairManuallyWithIp(ref, code, ip, port)
                },
                enabled = !uiState.isPairingInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("pair_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "Pair",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
