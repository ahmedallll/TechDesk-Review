package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraUnavailableException
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import com.example.data.model.ActiveContext
import com.example.data.model.ScanStatus
import com.example.scanner.BarcodeAnalyzer
import com.example.ui.AppScreen
import com.example.ui.ConnectionStatus
import com.example.ui.ScannerUiState
import com.example.ui.ScannerViewModel
import com.example.ui.theme.ContextAutoColor
import com.example.ui.theme.ContextPosColor
import com.example.ui.theme.ContextProductsColor
import com.example.ui.theme.TechDeskError
import com.example.ui.theme.TechDeskLaser
import com.example.ui.theme.TechDeskNavy
import com.example.ui.theme.TechDeskPrimary
import com.example.ui.theme.TechDeskSuccess
import com.example.ui.theme.TechDeskWarning
import java.util.concurrent.Executors

private const val CAMERA_LOG_TAG = "TechDeskScannerCamera"

@Composable
fun LiveScannerScreen(
    uiState: ScannerUiState,
    viewModel: ScannerViewModel
) {
    BackHandler {
        viewModel.navigateTo(AppScreen.HOME)
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Safety check: Prevent camera initialization if scanner is not paired
    if (uiState.credentials == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = TechDeskNavy.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = TechDeskWarning,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Scanner not paired. Please go back and pair your device.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.navigateTo(AppScreen.MANUAL_PAIR) },
                        modifier = Modifier.testTag("pair_scanner_button")
                    ) {
                        Text(text = "Go to Pairing Screen")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.navigateTo(AppScreen.HOME) },
                        modifier = Modifier.testTag("not_paired_back_button")
                    ) {
                        Text(text = "Back to Home", color = Color.White)
                    }
                }
            }
        }
        return
    }

    // TASK 2: Robust camera permission check
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var permissionRequested by remember { mutableStateOf(false) }

    // Re-check permission when resuming (e.g. returning from app settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val isGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (isGranted != hasCameraPermission) {
                    hasCameraPermission = isGranted
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionRequested = true
    }

    var cameraControl by remember { mutableStateOf<Camera?>(null) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var meteringPointFactory by remember { mutableStateOf<MeteringPointFactory?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var activeAnalyzer by remember { mutableStateOf<BarcodeAnalyzer?>(null) }
    var activeCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    var cameraError by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableStateOf(0) }

    // Safe toggle torch when state changes
    LaunchedEffect(uiState.torchEnabled, cameraControl, hasFlashUnit) {
        val cam = cameraControl
        if (cam != null && hasFlashUnit) {
            try {
                cam.cameraControl.enableTorch(uiState.torchEnabled)
            } catch (e: Exception) {
                Log.e(CAMERA_LOG_TAG, "Error enabling torch: ${e.message}", e)
            }
        }
    }

    // TASK 5: Clean disposal of camera resources and shutdown cameraExecutor
    DisposableEffect(Unit) {
        onDispose {
            try {
                activeAnalyzer?.close()
                activeCameraProvider?.unbindAll()
                cameraExecutor.shutdown()
                Log.d(CAMERA_LOG_TAG, "Camera resources cleanly disposed and executor shut down.")
            } catch (e: Exception) {
                Log.w(CAMERA_LOG_TAG, "Error disposing camera resources: ${e.message}", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // TASK 2: Only initialize/render camera if permission IS granted
        if (hasCameraPermission) {
            if (cameraError != null) {
                // Show recovery UI with Retry and Back buttons
                CameraErrorView(
                    errorMessage = cameraError ?: "Camera failed to initialize",
                    onRetry = {
                        cameraError = null
                        retryTrigger++
                    },
                    onBack = { viewModel.navigateTo(AppScreen.HOME) }
                )
            } else {
                key(retryTrigger) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val cam = cameraControl ?: return@detectTapGestures
                                    val factory = meteringPointFactory ?: return@detectTapGestures
                                    try {
                                        val point = factory.createPoint(offset.x, offset.y)
                                        val action = FocusMeteringAction.Builder(point).build()
                                        cam.cameraControl.startFocusAndMetering(action)
                                    } catch (e: Exception) {
                                        Log.w(CAMERA_LOG_TAG, "Focus metering action failed: ${e.message}")
                                    }
                                }
                            },
                        factory = { ctx ->
                            try {
                                val previewView = PreviewView(ctx)
                                meteringPointFactory = previewView.meteringPointFactory

                                try {
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        try {
                                            val cameraProvider = cameraProviderFuture.get()
                                            activeCameraProvider = cameraProvider

                                            // Verify camera availability before binding
                                            val cameraSelector = when {
                                                cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                                                cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                                                else -> {
                                                    val msg = "No suitable camera found on this device"
                                                    Log.e(CAMERA_LOG_TAG, msg)
                                                    Toast.makeText(ctx, "Camera failed to initialize", Toast.LENGTH_SHORT).show()
                                                    cameraError = "Camera failed to initialize"
                                                    return@addListener
                                                }
                                            }

                                            val preview = Preview.Builder().build().also {
                                                it.surfaceProvider = previewView.surfaceProvider
                                            }

                                            val imageAnalysis = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()

                                            val analyzer = BarcodeAnalyzer(isQrModeOnly = false) { barcode, format ->
                                                viewModel.onBarcodeScanned(barcode, format)
                                            }
                                            activeAnalyzer = analyzer
                                            imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                                            // Call unbindAll() before binding new use cases
                                            cameraProvider.unbindAll()

                                            val cam = cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                imageAnalysis
                                            )
                                            cameraControl = cam
                                            hasFlashUnit = cam.cameraInfo.hasFlashUnit()
                                            cameraError = null
                                            Log.i(CAMERA_LOG_TAG, "Camera bound successfully to lifecycle.")
                                        } catch (e: IllegalArgumentException) {
                                            Log.e(CAMERA_LOG_TAG, "Camera bind failed (IllegalArgumentException): ${e.message}", e)
                                            Toast.makeText(ctx, "Camera failed to initialize", Toast.LENGTH_SHORT).show()
                                            cameraError = "Camera failed to initialize"
                                        } catch (e: CameraUnavailableException) {
                                            Log.e(CAMERA_LOG_TAG, "Camera unavailable (CameraUnavailableException): ${e.message}", e)
                                            Toast.makeText(ctx, "Camera failed to initialize", Toast.LENGTH_SHORT).show()
                                            cameraError = "Camera failed to initialize"
                                        } catch (e: Throwable) {
                                            Log.e(CAMERA_LOG_TAG, "Camera initialization failed: ${e.message}", e)
                                            Toast.makeText(ctx, "Camera failed to initialize", Toast.LENGTH_SHORT).show()
                                            cameraError = "Camera failed to initialize"
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                } catch (e: Throwable) {
                                    Log.e(CAMERA_LOG_TAG, "ProcessCameraProvider.getInstance failed: ${e.message}", e)
                                    Toast.makeText(ctx, "Camera failed to initialize", Toast.LENGTH_SHORT).show()
                                    cameraError = "Camera failed to initialize"
                                }

                                previewView
                            } catch (e: Throwable) {
                                Log.e(CAMERA_LOG_TAG, "PreviewView factory failed: ${e.message}", e)
                                Toast.makeText(ctx, "Camera failed to initialize", Toast.LENGTH_SHORT).show()
                                cameraError = "Camera failed to initialize"
                                PreviewView(ctx)
                            }
                        }
                    )
                }

                // Scanning Viewfinder Overlay
                ScanningViewfinderOverlay()

                // Top HUD Bar
                TopScannerHud(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo(AppScreen.HOME) },
                    onToggleTorch = { viewModel.toggleTorch() },
                    onOpenHistory = { viewModel.navigateTo(AppScreen.HISTORY) }
                )

                // Bottom Status Card
                BottomScanStatusCard(
                    uiState = uiState,
                    onRetry = { event -> viewModel.retryScan(event) }
                )
            }
        } else {
            // TASK 2: Permission NOT granted state - display message and request button
            val activity = context as? Activity
            val isPermanentlyDenied = permissionRequested && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isPermanentlyDenied) TechDeskWarning else TechDeskLaser,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isPermanentlyDenied) {
                        stringResource(R.string.camera_permission_permanently_denied)
                    } else {
                        stringResource(R.string.camera_permission_required)
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                if (isPermanentlyDenied) {
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Text(text = stringResource(R.string.camera_permission_settings))
                    }
                } else {
                    Button(
                        onClick = {
                            permissionRequested = true
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.testTag("request_cam_perm_button")
                    ) {
                        Text(text = stringResource(R.string.camera_permission_grant))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.navigateTo(AppScreen.HOME) },
                    modifier = Modifier.testTag("perm_back_button")
                ) {
                    Text(text = "Back to Dashboard", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CameraErrorView(
    errorMessage: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = TechDeskNavy.copy(alpha = 0.95f)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = TechDeskError,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Unable to start the camera stream. You can retry or return to the dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("camera_error_back_button")
                    ) {
                        Text(text = "Back", color = Color.White)
                    }
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("camera_error_retry_button")
                    ) {
                        Text(text = stringResource(R.string.action_retry_camera))
                    }
                }
            }
        }
    }
}

@Composable
fun ScanningViewfinderOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
    val laserYFraction by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_fraction"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(200.dp)
                .border(2.5.dp, TechDeskLaser, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
        ) {
            val laserOffset = maxHeight * laserYFraction

            // Animated Laser Beam
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .offset(y = laserOffset)
                    .background(TechDeskLaser)
            )
        }
    }
}

@Composable
fun TopScannerHud(
    uiState: ScannerUiState,
    onBack: () -> Unit,
    onToggleTorch: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, start = 16.dp, end = 16.dp),
        color = TechDeskNavy.copy(alpha = 0.85f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("back_from_scanner_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // Connection & Context indicators
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val connColor = when (uiState.connectionStatus) {
                    ConnectionStatus.CONNECTED -> TechDeskSuccess
                    ConnectionStatus.RECONNECTING -> TechDeskWarning
                    else -> TechDeskError
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(connColor)
                )

                ActiveContextBadge(context = uiState.activeContext)
            }

            Row {
                IconButton(
                    onClick = onToggleTorch,
                    modifier = Modifier.testTag("toggle_torch_button")
                ) {
                    Icon(
                        imageVector = if (uiState.torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Torch",
                        tint = if (uiState.torchEnabled) TechDeskLaser else Color.White
                    )
                }

                IconButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.testTag("view_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun BottomScanStatusCard(
    uiState: ScannerUiState,
    onRetry: (com.example.data.model.ScanEvent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        val latest = uiState.latestScan

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = TechDeskNavy.copy(alpha = 0.95f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (latest != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.scanner_last_scanned),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF94A3B8)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TechDeskPrimary.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = latest.format,
                                color = TechDeskPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Exact barcode string (preserving leading zeros, monospace)
                    Text(
                        text = latest.barcode,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Delivery status indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        when (latest.status) {
                            ScanStatus.DELIVERED -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = TechDeskSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = latest.serverMessage ?: stringResource(R.string.scanner_status_sent),
                                    color = TechDeskSuccess,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            ScanStatus.PENDING, ScanStatus.RETRYING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = TechDeskLaser
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.scanner_status_sending),
                                    color = TechDeskLaser,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            ScanStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = TechDeskError,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = latest.serverMessage ?: stringResource(R.string.scanner_status_failed),
                                    color = TechDeskError,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                IconButton(
                                    onClick = { onRetry(latest) },
                                    modifier = Modifier.size(32.dp).testTag("quick_retry_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        tint = TechDeskLaser
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.scanner_reticle_hint),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Point camera at retail products (EAN-13, UPC, Code 128)",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.scanner_continuous_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
fun ActiveContextBadge(context: ActiveContext) {
    val (text, color) = when (context) {
        ActiveContext.POS -> Pair(stringResource(R.string.context_pos), ContextPosColor)
        ActiveContext.PRODUCTS -> Pair(stringResource(R.string.context_products), ContextProductsColor)
        ActiveContext.AUTO -> Pair(stringResource(R.string.context_auto), ContextAutoColor)
        ActiveContext.UNKNOWN -> Pair(stringResource(R.string.context_unknown), Color.Gray)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
