package com.example.privacut

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.privacut.ui.components.LoadingIndicator
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(
    permissionHandler: PermissionHandler,
    backgroundRemover: OnnxBackgroundRemover,
    snackbarHostState: SnackbarHostState,
    onImageCaptured: (Uri) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraHelper = remember { CameraHelper(context) }
    
    var hasCameraPermission by remember {
        mutableStateOf(PermissionHandler.hasCameraPermission(context))
    }
    
    var isCapturingPhoto by remember { mutableStateOf(false) }
    var flashMode by remember { mutableStateOf(FlashMode.AUTO) }
    
    // Create ImageCapture with current flash mode
    val imageCapture = rememberImageCapture(flashMode)
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    
    // Check camera permission
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionHandler.requestCameraPermission { granted ->
                hasCameraPermission = granted
                if (!granted) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Camera permission is required to take photos")
                    }
                }
            }
        }
    }
    
    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (hasCameraPermission) {
                // Camera preview
                CameraPreview(
                    imageCapture = imageCapture,
                    onError = { errorMsg ->
                        Log.e("CameraScreen", errorMsg)
                        scope.launch {
                            snackbarHostState.showSnackbar("Camera error: $errorMsg")
                        }
                    }
                )
                
                // Show loading overlay when capturing
                if (isCapturingPhoto) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(
                            message = "Capturing photo...",
                            textColor = Color.White
                        )
                    }
                }
                
                // Camera controls overlay
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Camera controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back button
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(48.dp),
                            enabled = !isCapturingPhoto
                        ) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        
                        // Capture button
                        Surface(
                            modifier = Modifier
                                .size(72.dp)
                                .clickable(enabled = !isCapturingPhoto) {
                                    isCapturingPhoto = true
                                    cameraHelper.takePhoto(
                                        imageCapture = imageCapture,
                                        onImageCaptured = { uri ->
                                            isCapturingPhoto = false
                                            onImageCaptured(uri)
                                        },
                                        onError = { exception ->
                                            isCapturingPhoto = false
                                            Log.e("CameraScreen", "Photo capture failed", exception)
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "Failed to capture image: ${exception.message}"
                                                )
                                            }
                                        }
                                    )
                                },
                            shape = CircleShape,
                            color = if (isCapturingPhoto) Color.Gray else Color.White
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Surface(
                                    modifier = Modifier.size(56.dp),
                                    shape = CircleShape,
                                    color = Color.LightGray
                                ) {}
                            }
                        }
                        
                        // Flash control button (moved to the right side)
                        IconButton(
                            onClick = {
                                // Toggle flash mode
                                flashMode = flashMode.next()
                                // Update image capture flash mode
                                imageCapture.flashMode = flashMode.toImageCaptureFlashMode()
                            },
                            modifier = Modifier.size(48.dp),
                            enabled = !isCapturingPhoto
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = when (flashMode) {
                                        FlashMode.AUTO -> R.drawable.ic_flash_auto
                                        FlashMode.ON -> R.drawable.ic_flash_on
                                        FlashMode.OFF -> R.drawable.ic_flash_off
                                    }
                                ),
                                contentDescription = "Flash mode: ${flashMode.name}",
                                tint = Color.White
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else {
                // No camera permission UI
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Please grant camera permission to use this feature",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(onClick = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }) {
                        Text("Grant Permission")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
} 