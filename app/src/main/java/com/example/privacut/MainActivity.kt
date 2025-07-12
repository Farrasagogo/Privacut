package com.example.privacut

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.privacut.ui.components.LoadingIndicator
import com.example.privacut.ui.theme.PrivacutTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var permissionHandler: PermissionHandler
    private lateinit var backgroundRemover: OnnxBackgroundRemover
    private lateinit var cameraHelper: CameraHelper
    
    // ViewModel for state management
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionHandler = PermissionHandler(this)
        backgroundRemover = OnnxBackgroundRemover(this)
        cameraHelper = CameraHelper(this)
        
        // Initialize model in lifecycleScope to handle configuration changes
        lifecycleScope.launch {
            try {
                val initialized = backgroundRemover.initialize()
                viewModel.updateModelLoadingState(initialized)
            } catch (e: Exception) {
                viewModel.updateModelLoadingState(false)
            }
        }

        setContent {
            PrivacutTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val uiState by viewModel.uiState.collectAsState()
                val context = LocalContext.current
                
                // Register crop image launcher
                val cropImageLauncher = rememberLauncherForActivityResult(
                    contract = CropImageContract()
                ) { uri ->
                    uri?.let {
                        viewModel.startImageLoading()
                        val bitmap = ImageUtils.uriToBitmap(context, it)
                        viewModel.finishImageLoading(bitmap)
                        viewModel.setCroppedImageUri(it)
                        viewModel.setProcessedBitmap(null)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    when {
                        uiState.loadingState == LoadingState.MODEL_LOADING -> SplashScreen()
                        !uiState.isModelLoaded -> ModelErrorScreen(
                            modifier = Modifier.padding(innerPadding),
                            onTryAgain = {
                                scope.launch {
                                    try {
                                        val initialized = backgroundRemover.initialize()
                                        viewModel.updateModelLoadingState(initialized)
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Error: ${e.message}")
                                        viewModel.updateModelLoadingState(false)
                                    }
                                }
                            }
                        )
                        uiState.currentScreen == Screen.CAMERA -> CameraScreen(
                            permissionHandler = permissionHandler,
                            backgroundRemover = backgroundRemover,
                            snackbarHostState = snackbarHostState,
                            onImageCaptured = { uri ->
                                viewModel.setCapturedImageUri(uri)
                                viewModel.setCurrentScreen(Screen.MAIN)
                                
                                // Load bitmap from URI with loading state
                                viewModel.startImageLoading()
                                val bitmap = ImageUtils.uriToBitmap(this, uri)
                                viewModel.finishImageLoading(bitmap)
                                viewModel.resetRotationAngle()
                                viewModel.setProcessedBitmap(null)
                            },
                            onNavigateBack = {
                                viewModel.setCurrentScreen(Screen.MAIN)
                            }
                        )
                        uiState.currentScreen == Screen.MAIN -> BackgroundRemovalScreen(
                            modifier = Modifier.padding(innerPadding),
                            permissionHandler = permissionHandler,
                            backgroundRemover = backgroundRemover,
                            snackbarHostState = snackbarHostState,
                            viewModel = viewModel,
                            onOpenCamera = {
                                viewModel.setCurrentScreen(Screen.CAMERA)
                            },
                            onCropImage = { uri ->
                                if (uri != null) {
                                    cropImageLauncher.launch(uri)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundRemover.close()
    }
    
    enum class Screen {
        MAIN, CAMERA, CROP
    }
}

@Composable
fun ModelErrorScreen(
    modifier: Modifier = Modifier,
    onTryAgain: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Model Loading Error",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "There was an error loading the model. Please make sure the model file is correctly placed in the ml directory.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onTryAgain) {
            Text("Try Again")
        }
    }
}

@Composable
fun BackgroundRemovalScreen(
    modifier: Modifier = Modifier,
    permissionHandler: PermissionHandler,
    backgroundRemover: OnnxBackgroundRemover,
    snackbarHostState: SnackbarHostState,
    viewModel: MainViewModel,
    onOpenCamera: () -> Unit = {},
    onCropImage: (Uri?) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val permissionState = rememberPermissionState()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setCapturedImageUri(it)
            viewModel.setProcessedBitmap(null)
            viewModel.resetRotationAngle()
            
            // Load bitmap from URI with loading state
            viewModel.startImageLoading()
            val bitmap = ImageUtils.uriToBitmap(context, it)
            viewModel.finishImageLoading(bitmap)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Privacut Background Removal",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Image Preview Area with Rotation and Crop Buttons
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.LightGray.copy(alpha = 0.3f))
                .border(1.dp, Color.Gray, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.loadingState == LoadingState.IMAGE_LOADING -> {
                    LoadingIndicator("Loading image...")
                }
                uiState.loadingState == LoadingState.PROCESSING -> {
                    LoadingIndicator("Processing image...")
                }
                uiState.loadingState == LoadingState.ROTATING -> {
                    LoadingIndicator("Rotating image...")
                }
                uiState.loadingState == LoadingState.SAVING -> {
                    LoadingIndicator("Saving image...")
                }
                uiState.loadingState == LoadingState.CROPPING -> {
                    LoadingIndicator("Cropping image...")
                }
                uiState.processedBitmap != null -> {
                    Image(
                        bitmap = uiState.processedBitmap!!.asImageBitmap(),
                        contentDescription = "Processed Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                uiState.originalBitmap != null -> {
                    Image(
                        bitmap = uiState.originalBitmap!!.asImageBitmap(),
                        contentDescription = "Selected Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                uiState.capturedImageUri != null -> {
                    AsyncImage(
                        model = uiState.capturedImageUri,
                        contentDescription = "Selected Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "No image selected",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                        Text(
                            "Please select an image or take a photo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
                        )
                    }
                }
            }
            
            // Action buttons (only show when an image is selected and not loading)
            if ((uiState.capturedImageUri != null || uiState.originalBitmap != null) && 
                uiState.loadingState == LoadingState.NONE) {
                
                // Rotate button
                FloatingActionButton(
                    onClick = {
                        // Rotate image by 90 degrees
                        viewModel.rotateImage(90f)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rotate Image"
                    )
                }
                
                // Crop button
                FloatingActionButton(
                    onClick = {
                        // Launch crop activity
                        onCropImage(uiState.capturedImageUri)
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_crop),
                        contentDescription = "Crop Image"
                    )
                }
                
                // Show rotation angle indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "${uiState.rotationAngle.toInt()}°",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Control buttons - gallery and camera
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    if (uiState.loadingState == LoadingState.NONE) {
                    permissionHandler.requestStoragePermissions { granted ->
                        if (granted) {
                            permissionState.updateStoragePermissions(true)
                            galleryLauncher.launch("image/*")
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Storage permission is required to select images")
                            }
                        }
                    }
                }
                },
                enabled = uiState.loadingState == LoadingState.NONE || uiState.loadingState == LoadingState.IMAGE_LOADING
            ) {
                if (uiState.loadingState == LoadingState.IMAGE_LOADING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Loading...")
                } else {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_gallery),
                        contentDescription = "Gallery",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Gallery")
                }
            }

            Button(
                onClick = {
                    if (uiState.loadingState == LoadingState.NONE) {
                        permissionHandler.requestCameraPermission { granted ->
                            if (granted) {
                                permissionState.updateCameraPermission(true)
                                onOpenCamera()
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Camera permission is required to take photos")
                                }
                            }
                        }
                    }
                },
                enabled = uiState.loadingState == LoadingState.NONE
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_camera),
                    contentDescription = "Camera",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Camera")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Process and save buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    if (uiState.loadingState != LoadingState.PROCESSING) {
                        uiState.originalBitmap?.let { bitmap ->
                            viewModel.startProcessing()
                        scope.launch {
                            try {
                                    val result = backgroundRemover.removeBackground(bitmap)
                                    viewModel.finishProcessing(result)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                snackbarHostState.showSnackbar("Error processing image: ${e.message}")
                                    viewModel.finishProcessing(null)
                                }
                            }
                        }
                    }
                },
                enabled = uiState.originalBitmap != null && 
                          (uiState.loadingState == LoadingState.NONE || uiState.loadingState == LoadingState.PROCESSING) && 
                          uiState.processedBitmap == null
            ) {
                if (uiState.loadingState == LoadingState.PROCESSING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Processing...")
                } else {
                Text("Remove Background")
            }
        }

        Button(
            onClick = {
                    if (uiState.loadingState != LoadingState.SAVING) {
                        uiState.processedBitmap?.let { bitmap ->
                            viewModel.startSaving()
                            scope.launch {
                                try {
                    val uri = ImageUtils.saveBitmapToGallery(context, bitmap)
                    if (uri != null) {
                            snackbarHostState.showSnackbar("Image saved to gallery")
                    } else {
                            snackbarHostState.showSnackbar("Failed to save image")
                        }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Error saving image: ${e.message}")
                                } finally {
                                    viewModel.finishSaving()
                                }
                            }
                        }
                }
            },
                enabled = uiState.processedBitmap != null && 
                         (uiState.loadingState == LoadingState.NONE || uiState.loadingState == LoadingState.SAVING)
            ) {
                if (uiState.loadingState == LoadingState.SAVING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Saving...")
                } else {
                    Text("Save Image")
                }
            }
        }
    }
}