package com.example.codbenchmarker

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class CamouflageDetector(context: Context, modelName: String) {

    private var interpreter: Interpreter? = null

    // Architect Upgrade: Pre-allocate memory ONCE to prevent RAM crashes
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null

    init {
        try {
            val modelBuffer = loadModelFile(context, modelName)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)

            // Allocate memory buffers during startup
            val inputTensor = interpreter?.getInputTensor(0)
            val outputTensor = interpreter?.getOutputTensor(0)

            if (inputTensor != null && outputTensor != null) {
                inputBuffer = ByteBuffer.allocateDirect(inputTensor.numBytes()).order(ByteOrder.nativeOrder())
                outputBuffer = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())
                Log.i("MLPerf_Terminal", "✅ AI Core & Memory Allocated.")
            }

        } catch (e: Exception) {
            Log.e("MLPerf_Terminal", "❌ Failed to load model: ${e.message}")
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun runInference(bitmap: Bitmap) {
        val currentInput = inputBuffer ?: return
        val currentOutput = outputBuffer ?: return

        // "Rewind" resets the buffer position to zero so we can overwrite it safely
        currentInput.rewind()
        currentOutput.rewind()

        // Run the math using the pre-allocated memory
        interpreter?.run(currentInput, currentOutput)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}