package com.example.privacut

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CameraHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "CameraHelper"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
    
    private val outputDirectory: File by lazy {
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, "Privacut").apply { mkdirs() }
        }
        mediaDir ?: context.filesDir
    }
    
    private val executor by lazy { ContextCompat.getMainExecutor(context) }
    
    /**
     * Take a photo using CameraX
     */
    fun takePhoto(
        imageCapture: ImageCapture,
        onImageCaptured: (Uri) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        // Create output file
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        // Create output options object
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener
        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
                    Log.d(TAG, "Photo captured: $savedUri")
                    onImageCaptured(savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    onError(exception)
                }
            }
        )
    }
}

/**
 * Flash modes for camera
 */
enum class FlashMode {
    AUTO, ON, OFF;
    
    fun next(): FlashMode {
        return when (this) {
            AUTO -> ON
            ON -> OFF
            OFF -> AUTO
        }
    }
    
    fun toImageCaptureFlashMode(): Int {
        return when (this) {
            AUTO -> ImageCapture.FLASH_MODE_AUTO
            ON -> ImageCapture.FLASH_MODE_ON
            OFF -> ImageCapture.FLASH_MODE_OFF
        }
    }
    
    companion object {
        fun fromImageCaptureFlashMode(mode: Int): FlashMode {
            return when (mode) {
                ImageCapture.FLASH_MODE_AUTO -> AUTO
                ImageCapture.FLASH_MODE_ON -> ON
                ImageCapture.FLASH_MODE_OFF -> OFF
                else -> AUTO
            }
        }
    }
}

/**
 * Remember the camera controller
 */
@Composable
fun rememberImageCapture(flashMode: FlashMode = FlashMode.AUTO): ImageCapture {
    return remember { 
        ImageCapture.Builder()
            .setFlashMode(flashMode.toImageCaptureFlashMode())
            .build() 
    }
}

/**
 * Camera preview composable
 */
@Composable
fun CameraPreview(
    imageCapture: ImageCapture,
    onError: (String) -> Unit = { Log.e("CameraPreview", it) }
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                // Unbind use cases when leaving the composition
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                Log.e("CameraPreview", "Failed to unbind camera use cases", e)
            }
        }
    }
    
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            
            try {
                // Set up the camera
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                // Select back camera as a default
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                onError("Failed to initialize camera: ${e.message}")
                Log.e("CameraPreview", "Use case binding failed", e)
            }
            
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
} 