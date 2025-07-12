package com.example.privacut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Background remover using ONNX Runtime with the RMBG-1.4 model
 */
class OnnxBackgroundRemover(private val context: Context) {

    companion object {
        private const val TAG = "OnnxBackgroundRemover"
        private const val MODEL_NAME = "model_quantized.onnx"
        private const val INPUT_SIZE = 1024 // Input size for the model
    }

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isInitialized = false
    private var inputName: String = "input"
    private var outputName: String = "output"

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            
            // Get model file
            val modelFile = copyModelToCache()
            if (modelFile == null) {
                Log.e(TAG, "Failed to copy model to cache")
                return@withContext false
            }
            
            // Create session options
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            
            // Create session
            ortSession = ortEnvironment?.createSession(modelFile.absolutePath, sessionOptions)
            
            // Get input and output names
            ortSession?.let { session ->
                val inputInfo = session.inputInfo
                val outputInfo = session.outputInfo
                
                // Log input and output details
                Log.d(TAG, "Model inputs: ${inputInfo.keys}")
                Log.d(TAG, "Model outputs: ${outputInfo.keys}")
                
                // Store first input and output names
                if (inputInfo.isNotEmpty()) {
                    inputName = inputInfo.keys.first()
                    Log.d(TAG, "Using input name: $inputName")
                }
                
                if (outputInfo.isNotEmpty()) {
                    outputName = outputInfo.keys.first()
                    Log.d(TAG, "Using output name: $outputName")
                }
            }
            
            isInitialized = true
            Log.d(TAG, "ONNX model initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ONNX model", e)
            e.printStackTrace()
            false
        }
    }
    
    private fun copyModelToCache(): File? {
        val cacheFile = File(context.cacheDir, MODEL_NAME)
        
        if (!cacheFile.exists()) {
            try {
                // Try to find the model file in ml directory
                val mlDir = File(context.getExternalFilesDir(null)?.parentFile, "ml")
                val mlFile = File(mlDir, MODEL_NAME)
                
                Log.d(TAG, "Looking for model at: ${mlFile.absolutePath}")
                
                if (mlFile.exists()) {
                    // Copy from ml directory to cache
                    Log.d(TAG, "Found model in ml directory, copying to cache")
                    val inputStream = FileInputStream(mlFile)
                    val outputStream = FileOutputStream(cacheFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                } else {
                    // Try to find in assets as fallback
                    Log.d(TAG, "Model not found in ml directory, trying assets")
                    val inputStream = try {
                        context.assets.open(MODEL_NAME)
                    } catch (e: Exception) {
                        Log.e(TAG, "Model not found in assets either", e)
                        return null
                    }
                    
                    val outputStream = FileOutputStream(cacheFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying model to cache", e)
                e.printStackTrace()
                return null
            }
        }
        
        if (!cacheFile.exists()) {
            Log.e(TAG, "Failed to copy model to cache")
            return null
        }
        
        return cacheFile
    }
    
    suspend fun removeBackground(inputBitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        if (!isInitialized || ortSession == null || ortEnvironment == null) {
            Log.e(TAG, "ONNX model not initialized")
            return@withContext inputBitmap
        }
        
        try {
            // Resize and preprocess the input image
            val resizedBitmap = Bitmap.createScaledBitmap(inputBitmap, INPUT_SIZE, INPUT_SIZE, true)
            val inputData = preProcessImage(resizedBitmap)
            
            // Create input tensor
            val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()) // NCHW format
            val inputTensor = OnnxTensor.createTensor(ortEnvironment, inputData, shape)
            
            // Run inference
            val inputs = mapOf(inputName to inputTensor)
            val output = ortSession?.run(inputs)
            
            // Process output - get the first output value and cast it to OnnxTensor
            val outputValue = output?.get(0)
            
            // Check if we have a valid output
            if (outputValue == null) {
                Log.e(TAG, "Null output from model")
                inputTensor.close()
                output?.close()
                return@withContext inputBitmap
            }
            
            // Cast to OnnxTensor if possible
            if (outputValue !is OnnxTensor) {
                Log.e(TAG, "Output is not an OnnxTensor")
                inputTensor.close()
                output?.close()
                return@withContext inputBitmap
            }
            
            // Process the output tensor
            val result = postProcessOutput(outputValue, inputBitmap.width, inputBitmap.height)
            
            // Clean up
            inputTensor.close()
            output.close()
            
            // Apply mask to original image
            createTransparentBitmap(inputBitmap, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing background", e)
            e.printStackTrace()
            inputBitmap
        }
    }
    
    private fun preProcessImage(bitmap: Bitmap): FloatBuffer {
        // Allocate buffer for 3 channels (RGB), with float values
        val buffer = FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE)
        
        // Normalize to [0,1] and then apply mean subtraction and std division
        val mean = floatArrayOf(0.5f, 0.5f, 0.5f)
        val std = floatArrayOf(1.0f, 1.0f, 1.0f)
        
        // Extract RGB values and normalize according to the Python code
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = bitmap.getPixel(x, y)
                
                // RGB channels, normalized to [0,1] and then normalized with mean/std
                val r = ((Color.red(pixel) / 255.0f) - mean[0]) / std[0]
                val g = ((Color.green(pixel) / 255.0f) - mean[1]) / std[1]
                val b = ((Color.blue(pixel) / 255.0f) - mean[2]) / std[2]
                
                // NCHW format (channels first)
                buffer.put(0 * INPUT_SIZE * INPUT_SIZE + y * INPUT_SIZE + x, r)
                buffer.put(1 * INPUT_SIZE * INPUT_SIZE + y * INPUT_SIZE + x, g)
                buffer.put(2 * INPUT_SIZE * INPUT_SIZE + y * INPUT_SIZE + x, b)
            }
        }
        
        return buffer
    }
    
    private fun postProcessOutput(outputTensor: OnnxTensor, origWidth: Int, origHeight: Int): ByteArray {
        val outputShape = outputTensor.info.shape
        Log.d(TAG, "Output tensor shape: ${outputShape.contentToString()}")
        
        val outputData = outputTensor.floatBuffer
        
        // Process according to postprocess_image function in Python
        // Find min and max for normalization
        var minVal = Float.MAX_VALUE
        var maxVal = Float.MIN_VALUE
        
        for (i in 0 until outputData.capacity()) {
            val value = outputData.get(i)
            minVal = min(minVal, value)
            maxVal = max(maxVal, value)
        }
        
        // Normalize to [0,255] and convert to ByteArray
        val maskSize = origHeight * origWidth
        val mask = ByteArray(maskSize)
        val resizeRatioH = INPUT_SIZE.toFloat() / origHeight.toFloat()
        val resizeRatioW = INPUT_SIZE.toFloat() / origWidth.toFloat()
        
        for (h in 0 until origHeight) {
            for (w in 0 until origWidth) {
                // Map to input coordinates
                val y = (h * resizeRatioH).toInt().coerceIn(0, INPUT_SIZE - 1)
                val x = (w * resizeRatioW).toInt().coerceIn(0, INPUT_SIZE - 1)
                
                // Get value from output tensor (assuming it's a single-channel output)
                val idx = y * INPUT_SIZE + x
                val value = outputData.get(idx)
                
                // Normalize and convert to byte
                val normalizedValue = ((value - minVal) / (maxVal - minVal) * 255).toInt().coerceIn(0, 255)
                mask[h * origWidth + w] = normalizedValue.toByte()
            }
        }
        
        return mask
    }
    
    private fun createTransparentBitmap(original: Bitmap, mask: ByteArray): Bitmap {
        val width = original.width
        val height = original.height
        
        // Create output bitmap with transparent background
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Apply mask to create transparent background
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelColor = original.getPixel(x, y)
                val maskValue = mask[y * width + x].toInt() and 0xFF
                
                // Alpha based on mask value
                val r = Color.red(pixelColor)
                val g = Color.green(pixelColor)
                val b = Color.blue(pixelColor)
                val a = maskValue // Use mask value directly as alpha
                
                outputBitmap.setPixel(x, y, Color.argb(a, r, g, b))
            }
        }
        
        return outputBitmap
    }
    
    fun close() {
        try {
            ortSession?.close()
            ortEnvironment?.close()
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ONNX model", e)
        }
    }
} 