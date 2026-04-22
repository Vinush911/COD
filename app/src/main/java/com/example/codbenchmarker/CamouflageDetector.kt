package com.example.codbenchmarker

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

data class Detection(
    val box: RectF,
    val score: Float,
    val label: String
)

class CamouflageDetector(
    context: Context,
    modelName: String
) {

    private var interpreter: Interpreter
    private val inputSize = 640

    // BERSERKER: Pre-allocate Memory to eliminate GC stutters and drop Latency
    private val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val intValues = IntArray(inputSize * inputSize)

    init {
        val modelBuffer = loadModel(context, modelName)

        // BERSERKER: Force TFLite to use 4 threads to match your UI readout
        val options = Interpreter.Options().apply {
            numThreads = 4
        }
        interpreter = Interpreter(modelBuffer, options)
    }

    private fun loadModel(context: Context, modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    fun runInference(image: ImageProxy): List<Detection> {
        return try {
            preprocess(image) // Fills the pre-allocated inputBuffer
            val outputTensor = interpreter.getOutputTensor(0)

            if (outputTensor.dataType() != DataType.FLOAT32) {
                Log.e("MLPerf_Terminal", "CRITICAL ERROR: Model is ${outputTensor.dataType()}! Code expects FLOAT32. Re-export your YOLO model.")
                return emptyList()
            }

            val shape = outputTensor.shape()
            val output = Array(1) { Array(shape[1]) { FloatArray(shape[2]) } }

            // ARCHITECT FIX: Explicitly rewind the buffer pointer to the beginning before TFLite reads it.
            inputBuffer.rewind()
            interpreter.run(inputBuffer, output)
            Log.d("YOLO_DEBUG", "cx raw: ${output[0][0][0]}")
            Log.d("YOLO_DEBUG", "cy raw: ${output[0][1][0]}")
            Log.d("YOLO_DEBUG", "w raw: ${output[0][2][0]}")
            Log.d("YOLO_DEBUG", "h raw: ${output[0][3][0]}")

            postProcess(output[0], shape[1], shape[2])
        } catch (e: Exception) {
            // BERSERKER SHIELD: Catch any hardware buffer/padding issues gracefully
            Log.e("MLPerf_Terminal", "Hardware or Inference Error CAUGHT: ${e.message}")
            emptyList()
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun preprocess(image: ImageProxy) {
        val bitmap = imageProxyToBitmap(image)
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        inputBuffer.rewind()
        resized.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        // ARCHITECT FIX: Restored the / 255f logic.
        // Standard YOLOv8 TFLite models absolutely require 0.0-1.0 float normalization.
        for (pixel in intValues) {
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            inputBuffer.putFloat((pixel and 0xFF) / 255f)
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        // BERSERKER: Try CameraX native conversion first. If hardware fails, fallback to blank bitmap.
        val sourceBitmap = try {
            image.toBitmap()
        } catch (e: Exception) {
            Log.e("MLPerf_Terminal", "toBitmap() Failed: ${e.message}")
            Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        }

        val matrix = Matrix().apply {
            postRotate(image.imageInfo.rotationDegrees.toFloat())
        }

        return Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, true)
    }

    private fun postProcess(output: Array<FloatArray>, dim1: Int, dim2: Int): List<Detection> {
        val detections = mutableListOf<Detection>()
        val isTransposed = dim1 > dim2
        val numBoxes = if (isTransposed) dim1 else dim2
        val numAttributes = if (isTransposed) dim2 else dim1

        for (i in 0 until numBoxes) {
            val cx = if (isTransposed) output[i][0] else output[0][i]
            val cy = if (isTransposed) output[i][1] else output[1][i]
            val w  = if (isTransposed) output[i][2] else output[2][i]
            val h  = if (isTransposed) output[i][3] else output[3][i]

            var maxConf = 0f
            var maxClassIndex = -1

            for (c in 0 until (numAttributes - 4)) {
                val classScore = if (isTransposed) output[i][4 + c] else output[4 + c][i]

                // APPLY SIGMOID (IMPORTANT)
                val score = classScore

                if (score > maxConf) {
                    maxConf = score
                    maxClassIndex = c
                }
            }

            // BERSERKER & ARCHITECT: STRICT HUMAN FILTER. Class 0 is Person.
            // Lowered confidence from 0.25f to 0.15f to test inference pipeline sensitivity.
            if (maxConf > 0.3f && maxClassIndex == 0){
                val left = cx - w / 2f
                val top = cy - h / 2f
                val right = cx + w / 2f
                val bottom = cy + h / 2f

                detections.add(
                    Detection(
                        RectF(left, top, right, bottom),
                        maxConf,
                        "PERSON"
                    )
                )
            }
        }

        return nms(detections)
    }

    private fun nms(detections: List<Detection>, iouThreshold: Float = 0.5f): List<Detection> {
        val result = mutableListOf<Detection>()
        val sorted = detections.sortedByDescending { it.score }.toMutableList()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)
            sorted.removeAll { iou(best.box, it.box) > iouThreshold }
        }

        return result.take(5)
    }

    private fun iou(a: RectF, b: RectF): Float {
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)

        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)

        val interArea = max(0f, right - left) * max(0f, bottom - top)
        return interArea / (areaA + areaB - interArea)
    }

    fun close() {
        interpreter.close()
    }
}