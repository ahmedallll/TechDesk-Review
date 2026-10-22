package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
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
import androidx.compose.material.icons.filled.Sync
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
import androidx.core.content.ContextCompat
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

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var cameraControl by remember { mutableStateOf<Camera?>(null) }
    var meteringPointFactory by remember { mutableStateOf<MeteringPointFactory?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Toggle torch when state changes
    LaunchedEffect(uiState.torchEnabled, cameraControl) {
        cameraControl?.cameraControl?.enableTorch(uiState.torchEnabled)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            // Tap to focus
                            val factory = meteringPointFactory
                            if (factory != null) {
                                val point = factory.createPoint(offset.x, offset.y)
                                val action = FocusMeteringAction.Builder(point).build()
                                cameraControl?.cameraControl?.startFocusAndMetering(action)
                            }
                        }
                    },
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    meteringPointFactory = previewView.meteringPointFactory

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(
                            cameraExecutor,
                            BarcodeAnalyzer(isQrModeOnly = false) { barcode, format ->
                                viewModel.onBarcodeScanned(barcode, format)
                            }
                        )

                        try {
                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            cameraControl = cam
                        } catch (e: Exception) {
                            // Camera bind error
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

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
        } else {
            // Permission missing
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.camera_permission_required),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.testTag("request_cam_perm_button")
                ) {
                    Text(text = stringResource(R.string.camera_permission_grant))
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

