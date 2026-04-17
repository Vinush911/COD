package com.example.codbenchmarker

import android.os.Bundle
import android.graphics.*
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var detector: CamouflageDetector? = null
    private lateinit var overlay: OverlayView
    private lateinit var fpsView: TextView
    private lateinit var latencyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        overlay = findViewById(R.id.boundingBoxOverlay)
        fpsView = findViewById(R.id.fpsVal)
        latencyView = findViewById(R.id.latencyVal)

        // Ensure this matches your file name in assets exactly
        detector = CamouflageDetector(this, "yolov8n_int8.tflite")

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
            // FIX: Rotating the hardware sensor pixels 90 degrees to be upright
            val bitmap = imageProxyToBitmap(imageProxy)
            val resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true)

            val boxes = detector?.runInference(resized) ?: emptyList()
            val latency = (System.nanoTime() - start) / 1_000_000.0

            runOnUiThread {
                latencyView.text = String.format("%.1f ms", latency)
                fpsView.text = String.format("%.1f FPS", 1000.0 / latency)
                overlay.setResults(boxes)
            }
        } finally { imageProxy.close() }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        // Dynamic transformation based on device sensor rotation
        val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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