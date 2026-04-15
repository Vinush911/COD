package com.example.codbenchmarker

import android.util.Log
import android.graphics.Bitmap

object ModelBenchmarker {
    private const val TAG = "MLPerf_Terminal"

    fun runBenchmark(images: List<Bitmap>, inferenceBlock: (Bitmap) -> Unit) {
        Log.i(TAG, String.format("%-12s | %-15s | %-10s | %-15s", "Dataset", "Latency (ms)", "FPS", "Memory Δ (MB)"))
        Log.i(TAG, "-".repeat(60))

        images.forEachIndexed { index, bitmap ->
            Runtime.getRuntime().gc() // Force garbage collection before test
            val startMemory = getUsedMemory()
            val startTime = System.nanoTime()

            // Pass the bitmap to the AI
            inferenceBlock(bitmap)

            val endTime = System.nanoTime()
            val endMemory = getUsedMemory()

            val latencyMs = (endTime - startTime) / 1_000_000.0
            val fps = if (latencyMs > 0) 1000.0 / latencyMs else 0.0
            val memoryUsedMb = Math.max(0.0, (endMemory - startMemory) / (1024.0 * 1024.0))

            Log.i(TAG, String.format("Image %-6d | %-15.2f | %-10.2f | %-15.2f", index + 1, latencyMs, fps, memoryUsedMb))
        }
        Log.i(TAG, "-".repeat(60))
    }

    private fun getUsedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}