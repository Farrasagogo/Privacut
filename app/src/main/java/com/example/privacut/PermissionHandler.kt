package com.example.privacut

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class PermissionHandler(private val activity: ComponentActivity) {
    
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var multiplePermissionsLauncher: ActivityResultLauncher<Array<String>>
    
    private var onCameraPermissionResult: ((Boolean) -> Unit)? = null
    private var onStoragePermissionResult: ((Boolean) -> Unit)? = null
    
    init {
        setupPermissionLaunchers()
    }
    
    private fun setupPermissionLaunchers() {
        cameraPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            onCameraPermissionResult?.invoke(isGranted)
        }
        
        multiplePermissionsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Check if all required permissions are granted
            val allGranted = permissions.entries.all { it.value }
            onStoragePermissionResult?.invoke(allGranted)
        }
    }
    
    fun requestCameraPermission(onResult: (Boolean) -> Unit) {
        onCameraPermissionResult = onResult
        
        if (hasCameraPermission(activity)) {
            onResult(true)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    fun requestStoragePermissions(onResult: (Boolean) -> Unit) {
        onStoragePermissionResult = onResult
        
        if (hasStoragePermissions(activity)) {
            onResult(true)
        } else {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
            multiplePermissionsLauncher.launch(permissions)
        }
    }
    
    companion object {
        fun hasCameraPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        fun hasStoragePermissions(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
}

@Composable
fun rememberPermissionState(): PermissionState {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(PermissionHandler.hasCameraPermission(context))
    }
    var hasStoragePermissions by remember {
        mutableStateOf(PermissionHandler.hasStoragePermissions(context))
    }
    
    return remember {
        PermissionState(
            hasCameraPermission = hasCameraPermission,
            hasStoragePermissions = hasStoragePermissions,
            updateCameraPermission = { hasCameraPermission = it },
            updateStoragePermissions = { hasStoragePermissions = it }
        )
    }
}

data class PermissionState(
    val hasCameraPermission: Boolean,
    val hasStoragePermissions: Boolean,
    val updateCameraPermission: (Boolean) -> Unit,
    val updateStoragePermissions: (Boolean) -> Unit
) 