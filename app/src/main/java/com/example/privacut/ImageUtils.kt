package com.example.privacut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * Utility class for image operations
 */
object ImageUtils {
    
    /**
     * Convert Uri to Bitmap
     */
    fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Rotate bitmap by the specified degrees
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * Save bitmap to the device's gallery
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String = "background_removed_${UUID.randomUUID()}.png"): Uri? {
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }
        
        return try {
            // Insert image into MediaStore
            val imageUri = context.contentResolver.insert(imageCollection, contentValues) ?: return null
            
            // Open output stream to save bitmap
            context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            
            imageUri
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Save bitmap to cache directory
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, filename: String = "bg_removed_temp.png"): File? {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        
        return try {
            val file = File(cachePath, filename)
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            file
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
} 