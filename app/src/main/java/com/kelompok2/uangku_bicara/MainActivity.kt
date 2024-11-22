package com.kelompok2.uangku_bicara

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var detectionResult: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var objectDetector: ObjectDetector
    private lateinit var mediaAudio: MediaPlayer
    private var audioIndex: Int = 0
    private var detects: List<String> = listOf()
    private var introFinished: Boolean = false
    private var lastSoundPlayed: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        detectionResult = findViewById(R.id.detectionResult)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check for camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionsLauncher.launch(Manifest.permission.CAMERA)
        }

        mediaAudio = MediaPlayer.create(baseContext, R.raw.intro)
        mediaAudio.setOnCompletionListener {
            introFinished = true
            Log.i("INTRO", "FINISHED...")
        }
        mediaAudio.start()

        // Initialize the object detector
        setupObjectDetector()
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Log.e("Camera Permission", "Permission denied")
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor, { imageProxy ->
                processImage(imageProxy)
            })

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupObjectDetector() {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setScoreThreshold(0.5f)  // Confidence threshold
            .setMaxResults(10)         // Maximum number of results to show
            .build()
        objectDetector = ObjectDetector.createFromFileAndOptions(this, "model.tflite", options)
    }

    private fun processImage(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val tensorImage = TensorImage.fromBitmap(bitmap)

        val results = objectDetector.detect(tensorImage)
        Log.i("CAMERA", introFinished.toString())
        val resultsLabel = results.map { it.categories[0].label }.toList()
        if(!detects.containsAll(resultsLabel)){
            detects = results.map { it.categories[0].label }
            audioIndex = 0;
            Log.i("DETECTS", "Terubah")
        }
        runOnUiThread {
            if(introFinished){
                detectionResult.text = results.joinToString("\n") { obj ->
                    "Label: ${obj.categories[0].label}, Confidence: ${obj.categories[0].score}"
                }
                if(detects.getOrNull(audioIndex) != null) {
                    if(!mediaAudio.isPlaying) {
                        mediaAudio = MediaPlayer.create(baseContext, getAudio(detects[audioIndex]))
                        mediaAudio.setOnCompletionListener {
                            Log.i("MediaPlayer","Compleetedd...");
                            audioIndex++;
                        }
                        mediaAudio.start()
                    }
                }
                if(detects.getOrNull(audioIndex) != results.elementAtOrNull(audioIndex)?.categories?.getOrNull(0)?.label) {
                    mediaAudio.stop()
                }
                if((lastSoundPlayed * 10000) <= System.currentTimeMillis() && !mediaAudio.isPlaying) {
                    lastSoundPlayed = System.currentTimeMillis()
                    mediaAudio = MediaPlayer.create(baseContext, R.raw.intro_jeda)
                    mediaAudio.start()
                }
            }
        }

        imageProxy.close()
    }

    private fun getAudio(label: String?): Int {
        return when(label){
            "Rp. 1.000" -> R.raw.seribu
            "Rp. 2.000" -> R.raw.duaribu
            "Rp. 5.000" -> R.raw.limaribu
            "Rp. 10.000" -> R.raw.sepuluh_ribu
            "Rp. 20.000" -> R.raw.duapuluh_ribu
            "Rp. 50.000" -> R.raw.limapuluh_ribu
            else -> R.raw.seratus_ribu
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer // Y
        val uBuffer = planes[1].buffer // U
        val vBuffer = planes[2].buffer // V
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            width,
            height,
            null
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}