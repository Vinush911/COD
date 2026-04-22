package com.example.codbenchmarker

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import android.util.Log

class MainActivity : AppCompatActivity() {

    private var detector: CamouflageDetector? = null
    private lateinit var overlay: OverlayView
    private lateinit var fpsView: TextView
    private lateinit var latencyView: TextView
    private lateinit var ramView: TextView

    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var currentFps = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        overlay = findViewById(R.id.boundingBoxOverlay)
        fpsView = findViewById(R.id.fpsVal)
        latencyView = findViewById(R.id.latencyVal)
        ramView = findViewById(R.id.ramVal)

        detector = CamouflageDetector(this, "yolov8n_float32.tflite")

        if (allPermissionsGranted()) startCamera()
        else requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build().also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy -> processFrame(proxy) }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        val start = System.nanoTime()
        try {
            val boxes = detector?.runInference(imageProxy) ?: emptyList()
            val latency = (System.nanoTime() - start) / 1_000_000.0

            frameCount++
            val currentTime = System.currentTimeMillis()
            var updateMetricsUI = false

            if (currentTime - lastFpsTimestamp >= 1000) {
                currentFps = frameCount
                frameCount = 0
                lastFpsTimestamp = currentTime
                updateMetricsUI = true
            }

            runOnUiThread {
                if (updateMetricsUI) {
                    // BERSERKER: Correctly fetch RAM here and assign it
                    val runtime = Runtime.getRuntime()
                    val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

                    latencyView.text = String.format("%.1f ms", latency)
                    fpsView.text = "$currentFps FPS"
                    ramView.text = "$usedMemMb MB"
                }
                overlay.setResults(boxes)
                overlay.invalidate()
            }
        } catch (e: Exception) {
            Log.e("MLPerf_Terminal", "Inference Error: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == 0

    override fun onRequestPermissionsResult(rc: Int, p: Array<String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (rc == 101 && g.isNotEmpty() && g[0] == 0) startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
    }
}