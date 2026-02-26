package sg.org.bcc.attendance.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import sg.org.bcc.attendance.ui.components.AppIcon
import sg.org.bcc.attendance.ui.components.AppIcons
import java.util.concurrent.Executors

@Composable
fun QrScannerContent(
    onScanResult: (String) -> Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isScanning by remember { mutableStateOf(true) }
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted: Boolean -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
        ProcessCameraProvider.getInstance(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        sg.org.bcc.attendance.ui.components.AppBottomSheetHeader(
            title = "Scan attendee QR"
        )

        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
            ) {
                ScannerCameraPreview { result ->
                    if (isScanning) {
                        val accepted = onScanResult(result)
                        if (accepted) {
                            isScanning = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        accepted
                    } else false
                }
                ScannerOverlay()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Camera permission is required to scan QR codes.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Request Permission")
                }
            }
        }
    }
}

@Composable
fun ScannerCameraPreview(onScanResult: (String) -> Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
            
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { 
                        it.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
                            processImageProxy(scanner, imageProxy, onScanResult)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    android.util.Log.e("QrScanner", "Camera binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onScanResult: (String) -> Boolean
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { 
                        onScanResult(it)
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("QrScanner", "Barcode scanning failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

@Composable
fun ScannerOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 4.dp.toPx()
        val boxSize = size.minDimension * 0.7f
        val left = (size.width - boxSize) / 2
        val top = (size.height - boxSize) / 2
        
        drawRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
            style = Stroke(width = strokeWidth)
        )
    }
}
