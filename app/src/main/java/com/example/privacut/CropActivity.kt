package com.example.privacut

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.UUID

class CropActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SOURCE_URI = "source_uri"
        const val EXTRA_RESULT_URI = "result_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get source URI from intent
        val sourceUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SOURCE_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SOURCE_URI)
        }
        
        if (sourceUri == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        
        // Create destination URI
        val destinationUri = Uri.fromFile(
            File(cacheDir, "cropped_${UUID.randomUUID()}.jpg")
        )
        
        // Start UCrop activity
        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f) // Square by default
            .withOptions(UCrop.Options().apply {
                setCompressionQuality(95)
                setHideBottomControls(false)
                setFreeStyleCropEnabled(true)
                setToolbarTitle("Crop Image")
            })
            .start(this)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == UCrop.REQUEST_CROP) {
            if (resultCode == RESULT_OK && data != null) {
                val resultUri = UCrop.getOutput(data)
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_RESULT_URI, resultUri)
                }
                setResult(RESULT_OK, resultIntent)
            } else if (resultCode == UCrop.RESULT_ERROR && data != null) {
                val error = UCrop.getError(data)
                setResult(RESULT_CANCELED)
            } else {
                setResult(RESULT_CANCELED)
            }
            finish()
        }
    }
}

/**
 * Activity result contract for image cropping
 */
class CropImageContract : ActivityResultContract<Uri, Uri?>() {
    override fun createIntent(context: Context, input: Uri): Intent {
        return Intent(context, CropActivity::class.java).apply {
            putExtra(CropActivity.EXTRA_SOURCE_URI, input)
        }
    }
    
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(CropActivity.EXTRA_RESULT_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(CropActivity.EXTRA_RESULT_URI)
        }
    }
} 