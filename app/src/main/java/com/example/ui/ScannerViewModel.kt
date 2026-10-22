package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActiveContext
import com.example.data.model.DeviceCredentials
import com.example.data.model.PairRequest
import com.example.data.model.QrPayload
import com.example.data.model.RemoteCredentials
import com.example.data.model.RemoteDashboardData
import com.example.data.model.RemoteFinancialReportData
import com.example.data.model.RemoteSaleDetail
import com.example.data.model.SaleItem
import com.example.data.model.ScanEvent
import com.example.data.model.ScanStatus
import com.example.data.network.RemoteResult
import com.example.data.network.RemoteStoreRepository
import com.example.data.network.SubnetDiscoveryManager
import com.example.data.network.TechDeskApiClient
import com.example.data.network.TetheringStatus
import com.example.data.security.SecureStorage
import com.example.scanner.FeedbackManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class AppScreen {
    REMOTE_LOGIN,
    HOME,
    SCAN_PAIR_QR,
    MANUAL_PAIR,
    LIVE_SCANNER,
    HISTORY,
    DIAGNOSTICS,
    SETTINGS,
    REMOTE_PAIRING,
    REMOTE_DASHBOARD,
    REMOTE_FINANCIAL_REPORT
}

enum class ConnectionStatus {
    UNPAIRED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class ScannerUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val connectionStatus: ConnectionStatus = ConnectionStatus.UNPAIRED,
    val statusMessage: String? = null,
    val credentials: DeviceCredentials? = null,
    val activeContext: ActiveContext = ActiveContext.UNKNOWN,
    val tetheringStatus: TetheringStatus = TetheringStatus(false, null, null, 24, emptyList()),
    val scanHistory: List<ScanEvent> = emptyList(),
    val latestScan: ScanEvent? = null,
    val torchEnabled: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val autoReconnect: Boolean = true,
    val isProbingSubnet: Boolean = false,
    val probeProgress: String? = null,
    val probeFoundHost: String? = null,
    val pairingErrorMessage: String? = null,
    val isPairingInProgress: Boolean = false,

    // Remote Store State
    val remoteCredentials: RemoteCredentials? = null,
    val remoteDashboardData: RemoteDashboardData? = null,
    val remoteFinancialReportData: RemoteFinancialReportData? = null,
    val isRemoteLoading: Boolean = false,
    val remoteErrorMessage: String? = null,
    val isRemoteStaleCache: Boolean = false,
    val remoteLastSyncTime: Long = 0L,
    val selectedDashboardPeriod: String = "today",
    val selectedReportPeriod: String = "this_month",
    val customFromDate: String = "",
    val customToDate: String = "",
    val isSaleDetailsLoading: Boolean = false,
    val selectedSaleDetails: RemoteSaleDetail? = null,
    val saleDetailsErrorMessage: String? = null,
    val isDarkMode: Boolean = false,
    val appLanguage: String = "fr"
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val secureStorage = SecureStorage(application)
    private val apiClient = TechDeskApiClient()
    private val discoveryManager = SubnetDiscoveryManager(apiClient)
    private val feedbackManager = FeedbackManager(application)
    private val remoteRepository = RemoteStoreRepository(application)

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var heartbeatJob: Job? = null
    private var remotePollingJob: Job? = null

    companion object {
        private const val TAG = "ScannerViewModel"
    }

    private val appPrefs = application.getSharedPreferences("techdesk_app_prefs", Context.MODE_PRIVATE)

    init {
        val savedDarkMode = appPrefs.getBoolean("is_dark_mode", false)
        val savedLanguage = appPrefs.getString("app_language", null)
            ?: AppCompatDelegate.getApplicationLocales().toLanguageTags().split("-", ",").firstOrNull()?.takeIf { it.isNotBlank() }
            ?: "fr"

        try {
            java.util.Locale.setDefault(java.util.Locale(savedLanguage))
        } catch (_: Exception) {}

        _uiState.update {
            it.copy(
                isDarkMode = savedDarkMode,
                appLanguage = savedLanguage
            )
        }
        loadStoredCredentials()
        loadStoredRemoteCredentials()
        refreshTetheringDiagnostics()

        val remoteCreds = remoteRepository.getStoredCredentials()
        if (remoteCreds == null || remoteCreds.remoteToken.isBlank()) {
            _uiState.update { it.copy(currentScreen = AppScreen.REMOTE_LOGIN) }
        } else {
            _uiState.update { it.copy(currentScreen = AppScreen.HOME) }
        }
    }

    fun setAppLanguage(lang: String) {
        appPrefs.edit().putString("app_language", lang).apply()
        try {
            java.util.Locale.setDefault(java.util.Locale(lang))
        } catch (_: Exception) {}
        _uiState.update { it.copy(appLanguage = lang) }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }

    fun navigateTo(screen: AppScreen) {
        remotePollingJob?.cancel()
        _uiState.update { it.copy(currentScreen = screen, pairingErrorMessage = null, remoteErrorMessage = null) }
        if (screen == AppScreen.DIAGNOSTICS || screen == AppScreen.HOME || screen == AppScreen.MANUAL_PAIR) {
            refreshTetheringDiagnostics()
        }
        if (screen == AppScreen.REMOTE_DASHBOARD) {
            startRemoteDashboardPolling()
        }
        if (screen == AppScreen.REMOTE_FINANCIAL_REPORT) {
            startRemoteFinancialReportPolling()
        }
    }

    fun refreshTetheringDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            val diag = discoveryManager.getTetheringDiagnostics()
            _uiState.update { it.copy(tetheringStatus = diag) }
        }
    }

    private fun loadStoredCredentials() {
        val creds = secureStorage.getCredentials()
        if (creds != null) {
            _uiState.update {
                it.copy(
                    credentials = creds,
                    connectionStatus = ConnectionStatus.CONNECTING,
                    statusMessage = "Connecting to ${creds.host}:${creds.port}…"
                )
            }
            verifyAndStartSession(creds)
        } else {
            _uiState.update {
                it.copy(
                    credentials = null,
                    connectionStatus = ConnectionStatus.UNPAIRED,
                    statusMessage = "Pair device with TechDesk DZ via Local Network"
                )
            }
        }
    }

    private fun loadStoredRemoteCredentials() {
        val remoteCreds = remoteRepository.getStoredCredentials()
        val lastSync = remoteRepository.getLastSyncTimestamp()
        _uiState.update {
            it.copy(
                remoteCredentials = remoteCreds,
                remoteLastSyncTime = lastSync
            )
        }
    }

    private fun verifyAndStartSession(creds: DeviceCredentials) {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTING) }
            val identifyResult = apiClient.identify(creds.host, creds.port, creds.installationReference)
            if (identifyResult.isSuccess) {
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.CONNECTED,
                        statusMessage = "Connected to TechDesk DZ (${creds.installationReference})"
                    )
                }
                startHeartbeat(creds)
            } else {
                val errorMsg = identifyResult.exceptionOrNull()?.message ?: "Host unreachable"
                Log.w(TAG, "Failed to connect to stored host: $errorMsg")
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.RECONNECTING,
                        statusMessage = "Reconnecting to ${creds.host}:${creds.port}…"
                    )
                }
                scheduleReconnect(creds)
            }
        }
    }

    fun discoverAndPairViaUsb(ref: String, code: String, preferredPort: Int = SubnetDiscoveryManager.DEFAULT_PORT) {
        discoverAndPairViaLocalNetwork(ref, code, preferredPort)
    }

    fun discoverAndPairViaLocalNetwork(ref: String, code: String, preferredPort: Int = SubnetDiscoveryManager.DEFAULT_PORT) {
        if (_uiState.value.isPairingInProgress) return
        viewModelScope.launch {
            val cleanRef = ref.trim()
            val cleanCode = code.trim()

            if (cleanRef.isBlank() || cleanCode.isBlank()) {
                _uiState.update { it.copy(pairingErrorMessage = "Both Installation Reference and Pairing Code are required") }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isPairingInProgress = true,
                    pairingErrorMessage = null,
                    statusMessage = "Detecting local network interface…"
                )
            }

            try {
                val tethering = withContext(Dispatchers.IO) { discoveryManager.getTetheringDiagnostics() }
                _uiState.update { it.copy(tetheringStatus = tethering) }
                if (!tethering.isTetheringDetected || tethering.candidateIp.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            pairingErrorMessage = "No active local network interface was found. Connect to Wi-Fi or LAN and refresh network status.",
                            statusMessage = "Local network required"
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(statusMessage = "Probing ${tethering.candidateSubnetCidr} from ${tethering.candidateInterface} for TechDesk DZ…")
                }

                val discovered = discoveryManager.discoverHostOnUsbSubnet(
                    expectedRef = cleanRef,
                    preferredPort = preferredPort,
                    tetheringStatus = tethering
                ) { progressMsg, _ ->
                    _uiState.update { state -> state.copy(statusMessage = progressMsg) }
                }

                if (discovered == null) {
                    val where = tethering.candidateSubnetCidr ?: "the detected local network"
                    _uiState.update {
                        it.copy(
                            pairingErrorMessage = "No matching TechDesk DZ server found on $where (port $preferredPort). Check PC IP, firewall rules, installation reference and pairing code. You can try Manual Pairing.",
                            statusMessage = "PC not found on local subnet"
                        )
                    }
                    return@launch
                }

                completePairing(
                    host = discovered.host,
                    port = discovered.port,
                    ref = cleanRef,
                    code = cleanCode
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Local network discovery/pairing failed", e)
                _uiState.update {
                    it.copy(
                        pairingErrorMessage = "Discovery failed: ${e.message ?: e.javaClass.simpleName}",
                        statusMessage = "Discovery failed"
                    )
                }
            } finally {
                if (_uiState.value.isPairingInProgress) {
                    _uiState.update { it.copy(isPairingInProgress = false) }
                }
            }
        }
    }

    fun pairWithQrPayload(payload: QrPayload) {
        if (_uiState.value.isPairingInProgress) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPairingInProgress = true,
                    pairingErrorMessage = null,
                    statusMessage = "Verifying TechDesk DZ at ${payload.ip}:${payload.port}…"
                )
            }
            try {
                val identifyResult = apiClient.identify(payload.ip, payload.port, payload.ref)
                if (identifyResult.isFailure) {
                    val err = identifyResult.exceptionOrNull()?.message ?: "Cannot reach TechDesk DZ"
                    _uiState.update { it.copy(pairingErrorMessage = err, statusMessage = "Identify failed: $err") }
                    return@launch
                }
                completePairing(payload.ip, payload.port, payload.ref, payload.code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "QR pairing failed", e)
                _uiState.update {
                    it.copy(pairingErrorMessage = "QR pairing failed: ${e.message ?: e.javaClass.simpleName}", statusMessage = "Pairing failed")
                }
            } finally {
                if (_uiState.value.isPairingInProgress) {
                    _uiState.update { it.copy(isPairingInProgress = false) }
                }
            }
        }
    }

    fun pairManuallyWithIp(ref: String, code: String, ip: String, portStr: String) {
        if (_uiState.value.isPairingInProgress) return
        viewModelScope.launch {
            val cleanRef = ref.trim()
            val cleanCode = code.trim()
            val cleanIp = ip.trim()
            val port = portStr.trim().toIntOrNull()

            if (cleanRef.isBlank() || cleanCode.isBlank() || cleanIp.isBlank()) {
                _uiState.update { it.copy(pairingErrorMessage = "Reference, pairing code, and PC IP address are required") }
                return@launch
            }
            if (!com.example.data.network.SubnetCalculator.isValidIpv4(cleanIp)) {
                _uiState.update { it.copy(pairingErrorMessage = "Enter a valid Windows PC IPv4 address") }
                return@launch
            }
            if (port == null || port !in 1..65535) {
                _uiState.update { it.copy(pairingErrorMessage = "Enter a valid port between 1 and 65535") }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isPairingInProgress = true,
                    pairingErrorMessage = null,
                    statusMessage = "Verifying TechDesk DZ at $cleanIp:$port…"
                )
            }
            try {
                val identifyResult = apiClient.identify(cleanIp, port, cleanRef)
                if (identifyResult.isFailure) {
                    val err = identifyResult.exceptionOrNull()?.message ?: "Cannot reach TechDesk DZ at $cleanIp:$port"
                    _uiState.update { it.copy(pairingErrorMessage = err, statusMessage = "Identify failed: $err") }
                    return@launch
                }
                completePairing(cleanIp, port, cleanRef, cleanCode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Manual pairing failed", e)
                _uiState.update {
                    it.copy(pairingErrorMessage = "Manual pairing failed: ${e.message ?: e.javaClass.simpleName}", statusMessage = "Pairing failed")
                }
            } finally {
                if (_uiState.value.isPairingInProgress) {
                    _uiState.update { it.copy(isPairingInProgress = false) }
                }
            }
        }
    }

    private suspend fun completePairing(host: String, port: Int, ref: String, code: String) {
        _uiState.update { it.copy(statusMessage = "Pairing device with TechDesk DZ…") }
        val deviceName = "Android Scanner (${Build.MODEL})"
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val pairRequest = PairRequest(
            installationReference = ref,
            pairingCode = code,
            deviceName = deviceName,
            deviceModel = deviceModel
        )

        val pairResult = apiClient.pair(host, port, pairRequest)
        if (pairResult.isSuccess) {
            val response = pairResult.getOrThrow()
            val credentials = DeviceCredentials(
                installationReference = response.installationReference ?: ref,
                host = host,
                port = port,
                deviceId = response.deviceId ?: UUID.randomUUID().toString(),
                deviceToken = response.deviceToken ?: "",
                protocolVersion = response.protocolVersion ?: "1.0.0",
                deviceName = deviceName
            )

            try {
                secureStorage.saveCredentials(credentials)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPairingInProgress = false,
                        pairingErrorMessage = "KeyStore encryption failed: ${e.message}. Credentials were not saved.",
                        statusMessage = "Security error"
                    )
                }
                return
            }

            _uiState.update {
                it.copy(
                    isPairingInProgress = false,
                    credentials = credentials,
                    connectionStatus = ConnectionStatus.CONNECTED,
                    statusMessage = "Successfully paired with TechDesk DZ",
                    currentScreen = AppScreen.LIVE_SCANNER
                )
            }
            feedbackManager.playScanSuccess(soundEnabled = true, vibrationEnabled = true)
            startHeartbeat(credentials)
        } else {
            val err = pairResult.exceptionOrNull()?.message ?: "Pairing rejected by server"
            _uiState.update {
                it.copy(
                    isPairingInProgress = false,
                    pairingErrorMessage = err,
                    statusMessage = err
                )
            }
        }
    }

    private fun startHeartbeat(creds: DeviceCredentials) {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val result = apiClient.heartbeat(creds.host, creds.port, creds.deviceToken)
                    if (result.isSuccess) {
                        val hb = result.getOrThrow()
                        val context = ActiveContext.fromString(hb.activeContext)
                        _uiState.update {
                            it.copy(
                                connectionStatus = ConnectionStatus.CONNECTED,
                                activeContext = context,
                                statusMessage = "Connected to TechDesk DZ"
                            )
                        }
                    } else {
                        val exception = result.exceptionOrNull()
                        if (exception is SecurityException) {
                            _uiState.update {
                                it.copy(
                                    connectionStatus = ConnectionStatus.ERROR,
                                    statusMessage = "Access token revoked by TechDesk DZ"
                                )
                            }
                            break
                        } else {
                            _uiState.update {
                                it.copy(
                                    connectionStatus = ConnectionStatus.RECONNECTING,
                                    statusMessage = "Connection lost. Reconnecting…"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat tick error", e)
                }
                delay(3500)
            }
        }
    }

    private fun scheduleReconnect(creds: DeviceCredentials) {
        if (!_uiState.value.autoReconnect) return
        viewModelScope.launch {
            delay(3000)
            if (_uiState.value.connectionStatus == ConnectionStatus.RECONNECTING) {
                verifyAndStartSession(creds)
            }
        }
    }

    fun onBarcodeScanned(barcode: String, format: String) {
        val creds = _uiState.value.credentials
        if (creds == null) {
            Log.w(TAG, "Cannot submit barcode: device is not paired")
            return
        }

        feedbackManager.playScanSuccess(
            soundEnabled = _uiState.value.soundEnabled,
            vibrationEnabled = _uiState.value.vibrationEnabled
        )

        val newEvent = ScanEvent(
            scanId = UUID.randomUUID().toString(),
            barcode = barcode,
            format = format,
            timestamp = System.currentTimeMillis(),
            status = ScanStatus.PENDING
        )

        _uiState.update {
            it.copy(
                latestScan = newEvent,
                scanHistory = listOf(newEvent) + it.scanHistory
            )
        }

        submitScanInternal(creds, newEvent)
    }

    fun retryScan(event: ScanEvent) {
        val creds = _uiState.value.credentials ?: return
        val updatedEvent = event.copy(
            status = ScanStatus.RETRYING,
            retryCount = event.retryCount + 1
        )
        updateEventInHistory(updatedEvent)
        submitScanInternal(creds, updatedEvent)
    }

    private fun submitScanInternal(creds: DeviceCredentials, event: ScanEvent) {
        viewModelScope.launch {
            val result = apiClient.submitBarcode(creds.host, creds.port, creds.deviceToken, event)
            if (result.isSuccess) {
                val res = result.getOrThrow()
                val deliveredEvent = if (res.isDuplicate) {
                    event.copy(
                        status = ScanStatus.DELIVERED,
                        isDuplicate = true,
                        serverMessage = "Duplicate scan (already accepted by server)"
                    )
                } else if (res.isAccepted) {
                    event.copy(
                        status = ScanStatus.DELIVERED,
                        isDuplicate = false,
                        serverMessage = "Accepted by TechDesk DZ"
                    )
                } else {
                    event.copy(
                        status = ScanStatus.FAILED,
                        isDuplicate = false,
                        serverMessage = res.statusDescription
                    )
                }

                updateEventInHistory(deliveredEvent)
                _uiState.update { it.copy(latestScan = deliveredEvent) }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to transmit"
                val failedEvent = event.copy(
                    status = ScanStatus.FAILED,
                    serverMessage = err
                )
                updateEventInHistory(failedEvent)
                _uiState.update { it.copy(latestScan = failedEvent) }
            }
        }
    }

    private fun updateEventInHistory(event: ScanEvent) {
        _uiState.update { state ->
            val updated = state.scanHistory.map {
                if (it.scanId == event.scanId) event else it
            }
            state.copy(scanHistory = updated)
        }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(torchEnabled = !it.torchEnabled) }
    }

    fun toggleSound() {
        _uiState.update { it.copy(soundEnabled = !it.soundEnabled) }
    }

    fun toggleVibration() {
        _uiState.update { it.copy(vibrationEnabled = !it.vibrationEnabled) }
    }

    fun toggleAutoReconnect() {
        _uiState.update { it.copy(autoReconnect = !it.autoReconnect) }
    }

    fun toggleDarkMode(isDark: Boolean) {
        appPrefs.edit().putBoolean("is_dark_mode", isDark).apply()
        _uiState.update { it.copy(isDarkMode = isDark) }
    }

    fun clearHistory() {
        _uiState.update { it.copy(scanHistory = emptyList(), latestScan = null) }
    }

    fun forgetPc() {
        heartbeatJob?.cancel()
        secureStorage.clearCredentials()
        _uiState.update {
            it.copy(
                credentials = null,
                connectionStatus = ConnectionStatus.UNPAIRED,
                activeContext = ActiveContext.UNKNOWN,
                statusMessage = "Device pairing cleared locally",
                currentScreen = AppScreen.HOME
            )
        }
    }

    fun runSubnetProbe() {
        val creds = _uiState.value.credentials ?: return
        if (_uiState.value.isProbingSubnet) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isProbingSubnet = true, probeProgress = "Starting USB subnet probe…", probeFoundHost = null)
            }
            try {
                val found = discoveryManager.discoverHostOnUsbSubnet(
                    expectedRef = creds.installationReference,
                    preferredPort = creds.port
                ) { msg, _ -> _uiState.update { state -> state.copy(probeProgress = msg) } }

                if (found != null) {
                    val newHost = found.host
                    val newPort = found.port
                    secureStorage.updateHostAndPort(newHost, newPort)
                    val updatedCreds = creds.copy(host = newHost, port = newPort)
                    _uiState.update {
                        it.copy(
                            probeProgress = "Found TechDesk DZ at $newHost:$newPort",
                            probeFoundHost = newHost,
                            credentials = updatedCreds
                        )
                    }
                    verifyAndStartSession(updatedCreds)
                } else {
                    val tether = discoveryManager.getTetheringDiagnostics()
                    _uiState.update {
                        it.copy(probeProgress = "No matching TechDesk DZ found on ${tether.candidateSubnetCidr ?: "the detected USB subnet"}. Check the PC IP and firewall.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "USB subnet probe failed", e)
                _uiState.update { it.copy(probeProgress = "USB subnet probe failed: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                _uiState.update { it.copy(isProbingSubnet = false) }
            }
        }
    }

    fun updateManualHostAndPort(host: String, port: Int) {
        val creds = _uiState.value.credentials ?: return
        secureStorage.updateHostAndPort(host, port)
        val updated = creds.copy(host = host, port = port)
        _uiState.update { it.copy(credentials = updated) }
        verifyAndStartSession(updated)
    }

    private fun parseHostAndPortFromUrl(rawUrl: String): Pair<String, Int> {
        val clean = rawUrl.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .split("/").firstOrNull() ?: "192.168.1.100"

        return if (clean.contains(":")) {
            val parts = clean.split(":")
            val host = parts[0].trim()
            val port = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 3000
            Pair(host, port)
        } else {
            Pair(clean.trim(), 3000)
        }
    }

    fun connectRemoteStore(serverUrl: String, ref: String, code: String) {
        if (_uiState.value.isRemoteLoading) return
        viewModelScope.launch {
            val cleanUrl = serverUrl.trim()
            val cleanRef = ref.trim()
            val cleanCode = code.trim()

            if (cleanUrl.isBlank() || cleanRef.isBlank() || cleanCode.isBlank()) {
                _uiState.update { it.copy(remoteErrorMessage = "Server URL, installation reference, and pairing code are required.") }
                return@launch
            }

            _uiState.update { it.copy(isRemoteLoading = true, remoteErrorMessage = null) }

            when (val result = remoteRepository.pairRemoteStore(cleanUrl, cleanRef, cleanCode)) {
                is RemoteResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            remoteCredentials = result.data,
                            currentScreen = AppScreen.HOME,
                            remoteErrorMessage = null
                        )
                    }
                    loadRemoteDashboard("today", forceRefresh = true)
                }
                is RemoteResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            remoteErrorMessage = result.message.ifBlank { "Connection failed. Check IP and code." }
                        )
                    }
                }
            }
        }
    }

    fun pairRemoteStore(serverUrl: String, ref: String, code: String) {
        connectRemoteStore(serverUrl, ref, code)
    }

    fun startRemoteDashboardPolling() {
        remotePollingJob?.cancel()
        remotePollingJob = viewModelScope.launch {
            loadRemoteDashboard(_uiState.value.selectedDashboardPeriod, forceRefresh = true, isSilent = _uiState.value.remoteDashboardData != null)
            while (isActive && _uiState.value.currentScreen == AppScreen.REMOTE_DASHBOARD) {
                delay(5000)
                if (_uiState.value.currentScreen == AppScreen.REMOTE_DASHBOARD) {
                    loadRemoteDashboard(_uiState.value.selectedDashboardPeriod, forceRefresh = true, isSilent = true)
                }
            }
        }
    }

    fun startRemoteFinancialReportPolling() {
        remotePollingJob?.cancel()
        remotePollingJob = viewModelScope.launch {
            loadRemoteFinancialReport(
                period = _uiState.value.selectedReportPeriod,
                fromDate = _uiState.value.customFromDate.ifBlank { null },
                toDate = _uiState.value.customToDate.ifBlank { null },
                isSilent = _uiState.value.remoteFinancialReportData != null
            )
            while (isActive && _uiState.value.currentScreen == AppScreen.REMOTE_FINANCIAL_REPORT) {
                delay(5000)
                if (_uiState.value.currentScreen == AppScreen.REMOTE_FINANCIAL_REPORT) {
                    loadRemoteFinancialReport(
                        period = _uiState.value.selectedReportPeriod,
                        fromDate = _uiState.value.customFromDate.ifBlank { null },
                        toDate = _uiState.value.customToDate.ifBlank { null },
                        isSilent = true
                    )
                }
            }
        }
    }

    fun loadRemoteDashboard(period: String = "today", forceRefresh: Boolean = false, isSilent: Boolean = false) {
        viewModelScope.launch {
            if (!isSilent || _uiState.value.remoteDashboardData == null) {
                _uiState.update {
                    it.copy(
                        selectedDashboardPeriod = period,
                        isRemoteLoading = true,
                        remoteErrorMessage = null
                    )
                }
            } else {
                _uiState.update { it.copy(selectedDashboardPeriod = period) }
            }
            when (val result = remoteRepository.fetchDashboard(period = period, forceRefresh = forceRefresh)) {
                is RemoteResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            remoteDashboardData = result.data,
                            isRemoteStaleCache = result.isFromCache,
                            remoteLastSyncTime = if (result.isFromCache) it.remoteLastSyncTime else System.currentTimeMillis()
                        )
                    }
                }
                is RemoteResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            remoteErrorMessage = if (!isSilent) result.message else it.remoteErrorMessage,
                            remoteCredentials = if (result.isAuthError) null else it.remoteCredentials
                        )
                    }
                }
            }
        }
    }

    fun loadRemoteFinancialReport(
        period: String = "this_month",
        fromDate: String? = null,
        toDate: String? = null,
        isSilent: Boolean = false
    ) {
        viewModelScope.launch {
            if (!isSilent || _uiState.value.remoteFinancialReportData == null) {
                _uiState.update {
                    it.copy(
                        selectedReportPeriod = period,
                        customFromDate = fromDate ?: it.customFromDate,
                        customToDate = toDate ?: it.customToDate,
                        isRemoteLoading = true,
                        remoteErrorMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        selectedReportPeriod = period,
                        customFromDate = fromDate ?: it.customFromDate,
                        customToDate = toDate ?: it.customToDate
                    )
                }
            }
            when (val result = remoteRepository.fetchFinancialReport(
                period = period,
                fromDate = fromDate,
                toDate = toDate
            )) {
                is RemoteResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            remoteFinancialReportData = result.data,
                            isRemoteStaleCache = result.isFromCache
                        )
                    }
                }
                is RemoteResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            remoteErrorMessage = if (!isSilent) result.message else it.remoteErrorMessage,
                            remoteCredentials = if (result.isAuthError) null else it.remoteCredentials
                        )
                    }
                }
            }
        }
    }

    fun fetchSaleDetails(reference: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaleDetailsLoading = true,
                    selectedSaleDetails = null,
                    saleDetailsErrorMessage = null
                )
            }
            when (val result = remoteRepository.fetchSaleDetails(reference)) {
                is RemoteResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaleDetailsLoading = false,
                            selectedSaleDetails = result.data
                        )
                    }
                }
                is RemoteResult.Error -> {
                    val fallback = synthesizeSaleDetail(reference)
                    _uiState.update {
                        it.copy(
                            isSaleDetailsLoading = false,
                            selectedSaleDetails = fallback,
                            saleDetailsErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun synthesizeSaleDetail(reference: String): RemoteSaleDetail {
        val existingSale = _uiState.value.remoteDashboardData?.recentSales?.find { it.reference == reference }
        return RemoteSaleDetail(
            referenceRaw = reference,
            clientRaw = existingSale?.client ?: "Client Passage",
            dateRaw = existingSale?.time ?: "Aujourd'hui",
            paymentMethodRaw = "Espèces",
            grandTotalRaw = existingSale?.totalAmount ?: existingSale?.paidAmount ?: 0.0,
            paidAmountRaw = existingSale?.paidAmount ?: 0.0,
            itemsRaw = listOf(
                SaleItem(
                    descriptionRaw = "Vente Magasin ($reference)",
                    quantityRaw = 1,
                    priceRaw = existingSale?.paidAmount ?: 0.0,
                    totalRaw = existingSale?.paidAmount ?: 0.0
                )
            )
        )
    }

    fun clearSelectedSaleDetails() {
        _uiState.update {
            it.copy(
                isSaleDetailsLoading = false,
                selectedSaleDetails = null,
                saleDetailsErrorMessage = null
            )
        }
    }

    fun disconnectRemoteStore() {
        heartbeatJob?.cancel()
        remotePollingJob?.cancel()
        secureStorage.clearCredentials()
        remoteRepository.clearCredentials()
        _uiState.update {
            it.copy(
                credentials = null,
                remoteCredentials = null,
                remoteDashboardData = null,
                remoteFinancialReportData = null,
                connectionStatus = ConnectionStatus.UNPAIRED,
                activeContext = ActiveContext.UNKNOWN,
                isRemoteStaleCache = false,
                remoteErrorMessage = null,
                currentScreen = AppScreen.REMOTE_LOGIN
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        heartbeatJob?.cancel()
        remotePollingJob?.cancel()
        feedbackManager.release()
    }
}
