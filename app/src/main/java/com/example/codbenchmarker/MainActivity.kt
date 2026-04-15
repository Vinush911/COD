package com.example.codbenchmarker

import android.os.Bundle
import android.graphics.Bitmap
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private var detector: CamouflageDetector? = null

    // HUD Text Views
    private lateinit var latencyView: TextView
    private lateinit var fpsView: TextView
    private lateinit var memView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 1. Connect Code to XML UI
        latencyView = findViewById(R.id.latencyVal)
        fpsView = findViewById(R.id.fpsVal)
        memView = findViewById(R.id.memVal)

        // 2. Adjust Padding for Notches/System Bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        startEngine()
    }

    private fun startEngine() {
        // Create 15 blank dummy frames to test the engine's speed
//        val dummyImage = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
//        val testImages = List(15) { dummyImage }
        // LOAD REAL IMAGES INSTEAD:
        val realImage = loadRealImage("test_image.jpg")

        if (realImage == null) {
            Log.e("MLPerf_Terminal", "Halting: test_image.jpg not found in assets.")
            return
        }

        // Let's run the real image 15 times to get a stable FPS average
        val testImages = List(15) { realImage }

        // Launch into a background CPU thread so the UI doesn't freeze
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                detector = CamouflageDetector(this@MainActivity, "best_int8.tflite")

                ModelBenchmarker.runBenchmark(testImages) { currentBitmap ->

                    val startTime = System.nanoTime()
                    val startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

                    // Trigger the C++ AI Engine
                    detector?.runInference(currentBitmap)

                    val endTime = System.nanoTime()
                    val endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

                    // Math for HUD
                    val latency = (endTime - startTime) / 1_000_000.0
                    val fps = 1000.0 / latency
                    val memDelta = Math.max(0.0, (endMem - startMem) / (1024.0 * 1024.0))

                    // Switch back to the Main UI Thread to update the screen
                    lifecycleScope.launch(Dispatchers.Main) {
                        latencyView.text = String.format("%.1f ms", latency)
                        fpsView.text = String.format("%.1f", fps)
                        memView.text = String.format("%.2f MB", memDelta)
                    }
                }
            } catch (e: Exception) {
                Log.e("MLPerf_Terminal", "Fatal Error: ${e.message}")
            }
        }
    }
    // ARCHITECT UPGRADE: Load and safely resize real images
    private fun loadRealImage(fileName: String): Bitmap? {
        return try {
            val inputStream = assets.open(fileName)
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)

            // CRITICAL: Force the image to be exactly 640x640 to prevent NPU memory crashes
            Bitmap.createScaledBitmap(originalBitmap, 640, 640, true)
        } catch (e: Exception) {
            Log.e("MLPerf_Terminal", "Failed to load $fileName: ${e.message}")
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up the memory when app closes
        detector?.close()
    }
}