package com.example.codbenchmarker

import android.util.Log
import android.graphics.Bitmap

/**
 * ARCHITECT CLEANUP: This file now ONLY contains the Benchmarker logic.
 * Duplicate 'OverlayView' has been purged to resolve the 'Redeclaration' error.
 */
object ModelBenchmarker {
    private const val TAG = "MLPerf_Terminal"

    fun runBenchmark(images: List<Bitmap>, inferenceBlock: (Bitmap) -> Unit) {
        Log.i(TAG, String.format("%-12s | %-15s | %-10s | %-15s", "Dataset", "Latency (ms)", "FPS", "Memory Δ (MB)"))
        Log.i(TAG, "-".repeat(60))

        images.forEachIndexed { index, bitmap ->
            Runtime.getRuntime().gc()
            val startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val startTime = System.nanoTime()

            inferenceBlock(bitmap)

            val endTime = System.nanoTime()
            val endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

            val latencyMs = (endTime - startTime) / 1_000_000.0
            val fps = if (latencyMs > 0) 1000.0 / latencyMs else 0.0
            val memoryUsedMb = Math.max(0.0, (endMemory - startMemory) / (1024.0 * 1024.0))

            Log.i(TAG, String.format("Image %-6d | %-15.2f | %-10.2f | %-15.2f", index + 1, latencyMs, fps, memoryUsedMb))
        }
    }
}