package com.manish.miragelens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.manish.miragelens.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var currentBitmap: Bitmap? = null
    private var modelReady = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_denied, Toast.LENGTH_LONG).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { setPhoto(decodeBitmap(it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.takePhotoButton.setOnClickListener { takePhoto() }
        binding.choosePhotoButton.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.analyseButton.setOnClickListener { analysePhoto() }

        // Load the 22 MB model off the main thread.
        Thread {
            val ok = try {
                NativeBridge.loadModel(assets, "mirage.cact")
            } catch (e: Throwable) {
                false
            }
            runOnUiThread {
                modelReady = ok
                binding.statusText.setText(
                    if (ok) R.string.model_ready else R.string.model_failed
                )
                updateAnalyseButton()
            }
        }.start()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun updateAnalyseButton() {
        binding.analyseButton.isEnabled = modelReady && currentBitmap != null
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                binding.statusText.text = "Camera error: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val file = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    setPhoto(decodeBitmap(file))
                }

                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(
                        this@MainActivity,
                        "Photo failed: ${exc.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun setPhoto(bitmap: Bitmap?) {
        if (bitmap == null) {
            Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show()
            return
        }
        currentBitmap = bitmap
        binding.photoView.bitmap = bitmap
        binding.photoView.boxes = emptyList()
        binding.photoView.visibility = View.VISIBLE
        binding.cameraPreview.visibility = View.GONE
        binding.sceneText.text = ""
        binding.textFound.text = ""
        binding.objectsFound.text = ""
        updateAnalyseButton()
    }

    private fun analysePhoto() {
        val bitmap = currentBitmap ?: run {
            Toast.makeText(this, R.string.no_photo, Toast.LENGTH_SHORT).show()
            return
        }
        binding.statusText.setText(R.string.analysing)
        binding.analyseButton.isEnabled = false
        Thread {
            try {
                val scaled = scaleBitmap(bitmap, 1024)
                val rgb = bitmapToRgb(scaled)
                val json = NativeBridge.describe(rgb, scaled.width, scaled.height)
                runOnUiThread { showResult(json) }
            } catch (e: Throwable) {
                runOnUiThread {
                    binding.statusText.text = "Analysis failed: ${e.message}"
                    binding.analyseButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun showResult(json: String) {
        binding.analyseButton.isEnabled = true
        try {
            val root = JSONObject(json)
            if (root.has("error")) {
                binding.statusText.text = "Analysis failed: ${root.optString("error")}"
                return
            }
            binding.statusText.setText(R.string.model_ready)

            // Scene description.
            val scene = root.optJSONObject("scene")
            val sceneLines = StringBuilder()
            scene?.let {
                it.optString("background").takeIf { s -> s.isNotEmpty() }?.let { b ->
                    sceneLines.append("Background: $b\n")
                }
                it.optString("style").takeIf { s -> s.isNotEmpty() }?.let { s ->
                    sceneLines.append("Style: $s\n")
                }
            }
            val reasoning = root.optString("reasoning")
            if (reasoning.isNotEmpty()) sceneLines.append(reasoning)
            binding.sceneText.text =
                sceneLines.toString().trim().ifEmpty { getString(R.string.nothing_found) }

            // Text found in the photo (OCR).
            val textArr = root.optJSONArray("text") ?: JSONArray()
            val textLines = StringBuilder()
            val textBoxes = mutableListOf<OverlayBox>()
            for (i in 0 until textArr.length()) {
                val t = textArr.getJSONObject(i)
                val s = t.optString("string")
                if (s.isBlank()) continue
                textLines.append("• $s\n")
                val box = t.optJSONArray("bbox")
                if (box != null && box.length() == 4) {
                    textBoxes.add(
                        OverlayBox(
                            box.getDouble(0).toFloat(), box.getDouble(1).toFloat(),
                            box.getDouble(2).toFloat(), box.getDouble(3).toFloat(),
                            Color.parseColor("#FFB300"), s
                        )
                    )
                }
            }
            binding.textFound.text =
                textLines.toString().trim().ifEmpty { getString(R.string.nothing_found) }

            // Objects found.
            val objArr = root.optJSONArray("objects") ?: JSONArray()
            val objLines = StringBuilder()
            val objBoxes = mutableListOf<OverlayBox>()
            for (i in 0 until objArr.length()) {
                val o = objArr.getJSONObject(i)
                val name = o.optString("name")
                if (name.isBlank()) continue
                val score = o.optDouble("score", 0.0)
                objLines.append("• $name (${(score * 100).toInt()}%)\n")
                val box = o.optJSONArray("bbox")
                if (box != null && box.length() == 4) {
                    objBoxes.add(
                        OverlayBox(
                            box.getDouble(0).toFloat(), box.getDouble(1).toFloat(),
                            box.getDouble(2).toFloat(), box.getDouble(3).toFloat(),
                            Color.parseColor("#2E7D32"), name
                        )
                    )
                }
            }
            binding.objectsFound.text =
                objLines.toString().trim().ifEmpty { getString(R.string.nothing_found) }

            binding.photoView.boxes = objBoxes + textBoxes
        } catch (e: Exception) {
            binding.statusText.text = "Could not read the result: ${e.message}"
        }
    }

    // ---- image helpers ----

    private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (maxOf(w, h) <= maxDim) return src
        val scale = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    private fun bitmapToRgb(bitmap: Bitmap): ByteArray {
        val argb = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(argb)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        val pixels = IntArray(argb.width * argb.height)
        argb.getPixels(pixels, 0, argb.width, 0, 0, argb.width, argb.height)
        val rgb = ByteArray(pixels.size * 3)
        for (i in pixels.indices) {
            val p = pixels[i]
            rgb[i * 3] = ((p shr 16) and 0xFF).toByte()
            rgb[i * 3 + 1] = ((p shr 8) and 0xFF).toByte()
            rgb[i * 3 + 2] = (p and 0xFF).toByte()
        }
        return rgb
    }

    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val angle = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (angle == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun decodeBitmap(file: File): Bitmap? {
        return try {
            val exif = ExifInterface(file)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
            rotateIfNeeded(
                BitmapFactory.decodeFile(file.absolutePath) ?: return null,
                orientation
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            val orientation = contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
            contentResolver.openInputStream(uri)?.use { input ->
                rotateIfNeeded(BitmapFactory.decodeStream(input) ?: return null, orientation)
            }
        } catch (e: Exception) {
            null
        }
    }
}
