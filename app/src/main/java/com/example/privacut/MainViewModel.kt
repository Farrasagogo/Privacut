package com.example.privacut

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel : ViewModel() {
    
    // App state
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    // Initialize model loading state
    init {
        _uiState.update { it.copy(loadingState = LoadingState.MODEL_LOADING) }
    }
    
    fun updateModelLoadingState(isLoaded: Boolean) {
        _uiState.update { 
            it.copy(
                isModelLoaded = isLoaded,
                loadingState = LoadingState.NONE
            )
        }
    }
    
    fun setCurrentScreen(screen: MainActivity.Screen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }
    
    fun setCapturedImageUri(uri: Uri?) {
        _uiState.update { it.copy(capturedImageUri = uri) }
    }
    
    fun setOriginalBitmap(bitmap: Bitmap?) {
        _uiState.update { it.copy(originalBitmap = bitmap) }
    }
    
    fun startImageLoading() {
        _uiState.update { it.copy(loadingState = LoadingState.IMAGE_LOADING) }
    }
    
    fun finishImageLoading(bitmap: Bitmap?) {
        _uiState.update { 
            it.copy(
                originalBitmap = bitmap,
                loadingState = LoadingState.NONE
            )
        }
    }
    
    fun setProcessedBitmap(bitmap: Bitmap?) {
        _uiState.update { it.copy(processedBitmap = bitmap) }
    }
    
    fun startProcessing() {
        _uiState.update { it.copy(loadingState = LoadingState.PROCESSING) }
    }
    
    fun finishProcessing(bitmap: Bitmap?) {
        _uiState.update { 
            it.copy(
                processedBitmap = bitmap,
                loadingState = LoadingState.NONE
            )
        }
    }
    
    fun startCropping() {
        _uiState.update { it.copy(loadingState = LoadingState.CROPPING) }
    }
    
    fun finishCropping(bitmap: Bitmap?) {
        _uiState.update { 
            it.copy(
                originalBitmap = bitmap,
                processedBitmap = null,
                loadingState = LoadingState.NONE
            )
        }
    }
    
    fun setCroppedImageUri(uri: Uri?) {
        _uiState.update { it.copy(croppedImageUri = uri) }
    }
    
    fun startSaving() {
        _uiState.update { it.copy(loadingState = LoadingState.SAVING) }
    }
    
    fun finishSaving() {
        _uiState.update { it.copy(loadingState = LoadingState.NONE) }
    }
    
    fun setProcessingState(isProcessing: Boolean) {
        _uiState.update { 
            it.copy(
                isProcessing = isProcessing,
                loadingState = if (isProcessing) LoadingState.PROCESSING else LoadingState.NONE
            )
        }
    }
    
    fun rotateImage(degrees: Float) {
        val currentBitmap = _uiState.value.originalBitmap ?: return
        
        // Set loading state for rotation
        _uiState.update { it.copy(loadingState = LoadingState.ROTATING) }
        
        val rotatedBitmap = ImageUtils.rotateBitmap(currentBitmap, degrees)
        
        _uiState.update { 
            it.copy(
                originalBitmap = rotatedBitmap,
                rotationAngle = (it.rotationAngle + degrees) % 360f,
                loadingState = LoadingState.NONE
            )
        }
        
        // Also rotate processed bitmap if it exists
        _uiState.value.processedBitmap?.let { processed ->
            val rotatedProcessed = ImageUtils.rotateBitmap(processed, degrees)
            _uiState.update { it.copy(processedBitmap = rotatedProcessed) }
        }
    }
    
    fun resetRotationAngle() {
        _uiState.update { it.copy(rotationAngle = 0f) }
    }
}

enum class LoadingState {
    NONE,
    MODEL_LOADING,
    IMAGE_LOADING,
    PROCESSING,
    ROTATING,
    SAVING,
    CROPPING
}

data class MainUiState(
    val isModelLoaded: Boolean = false,
    val currentScreen: MainActivity.Screen = MainActivity.Screen.MAIN,
    val capturedImageUri: Uri? = null,
    val originalBitmap: Bitmap? = null,
    val processedBitmap: Bitmap? = null,
    val croppedImageUri: Uri? = null,
    val isProcessing: Boolean = false,
    val rotationAngle: Float = 0f,
    val loadingState: LoadingState = LoadingState.NONE
) {
    val isLoading: Boolean
        get() = loadingState != LoadingState.NONE
} 