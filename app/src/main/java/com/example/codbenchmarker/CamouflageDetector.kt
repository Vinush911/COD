package com.example.codbenchmarker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class CamouflageDetector(context: Context, modelName: String) {

    private var interpreter: Interpreter? = null
    private var inputBuffer: ByteBuffer? = null

    init {
        try {
            val modelBuffer = loadModelFile(context, modelName)
            interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                setNumThreads(4)
            })

            val inputTensor = interpreter!!.getInputTensor(0)
            inputBuffer = ByteBuffer.allocateDirect(inputTensor.numBytes()).order(ByteOrder.nativeOrder())

            Log.i("MLPerf_Terminal", "✅ Model Loaded: $modelName")
        } catch (e: Exception) {
            Log.e("MLPerf_Terminal", "❌ Model Load Failed: ${e.message}")
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun runInference(bitmap: Bitmap): List<RectF> {
        val results = mutableListOf<RectF>()
        if (interpreter == null || inputBuffer == null) return results

        try {
            // 1. DYNAMIC INPUT SIZING
            val inputShape = interpreter!!.getInputTensor(0).shape()
            val inputSize = inputShape[1] // Usually 640
            val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

            inputBuffer!!.rewind()
            val pixels = IntArray(inputSize * inputSize)
            resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

            for (pixel in pixels) {
                inputBuffer!!.putFloat(((pixel shr 16) and 0xFF) / 255.0f) // R
                inputBuffer!!.putFloat(((pixel shr 8) and 0xFF) / 255.0f)  // G
                inputBuffer!!.putFloat((pixel and 0xFF) / 255.0f)          // B
            }

            // 2. DYNAMIC OUTPUT SHAPE EXTRACTION
            // Standard YOLOv8 is [1, 84, 8400]. Custom models might be [1, 5, 8400].
            val outputShape = interpreter!!.getOutputTensor(0).shape()
            val numChannels = outputShape[1] // e.g., 5 or 84
            val numElements = outputShape[2] // e.g., 8400

            // 3. FAST FLAT-BUFFER EXECUTION (Prevents array cast crashes)
            val outputBuffer = ByteBuffer.allocateDirect(1 * numChannels * numElements * 4).order(ByteOrder.nativeOrder())
            interpreter!!.run(inputBuffer, outputBuffer)

            // 4. PARSE DYNAMIC OUTPUT
            outputBuffer.rewind()
            val out = FloatArray(numChannels * numElements)
            outputBuffer.asFloatBuffer().get(out)

            // Loop through all 8400 bounding box predictions
            // Loop through all 8,400 guesses
            for (i in 0 until 8400) {
                // Index 4 is the "Person" class in a simple COCO model
                val personConfidence = out[4 * 8400 + i]

                if (personConfidence > 0.40f) {
                    val cx = out[0 * 8400 + i] // Center X
                    val cy = out[1 * 8400 + i] // Center Y
                    val w  = out[2 * 8400 + i] // Width
                    val h  = out[3 * 8400 + i] // Height

                    // Convert to Corner Coordinates
                    results.add(RectF(cx - w/2f, cy - h/2f, cx + w/2f, cy + h/2f))
                }
            }

        } catch (e: Exception) {
            Log.e("MLPerf_Terminal", "Inference Error: ${e.message}")
        }

        return results
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}