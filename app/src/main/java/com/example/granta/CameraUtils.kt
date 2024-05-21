package com.example.granta

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object CameraUtils {

    lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    var capturedPhotoUri: Uri? = null

    const val REQUEST_CODE_PERMISSIONS = 10
    val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    fun startCamera(activity: Activity) {
        if (allPermissionsGranted(activity)) {
            startCameraProvider(activity)
        } else {
            ActivityCompat.requestPermissions(
                activity,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCameraProvider(activity: Activity) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    activity as LifecycleOwner,
                    cameraSelector,
                    imageCapture
                )
                Log.d("CameraUtils", "Camera initialized successfully")
            } catch (exc: Exception) {
                Toast.makeText(activity, "Ошибка при привязке камеры: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(activity))

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun takePhoto(activity: Activity, onPhotoTaken: (Uri) -> Unit) {
        if (!::imageCapture.isInitialized) {
            activity.runOnUiThread {
                Toast.makeText(activity, "Camera not initialized", Toast.LENGTH_SHORT).show()
            }
            Log.e("CameraUtils", "Camera not initialized")
            return
        }

        val photoFile = File(
            activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    capturedPhotoUri = savedUri // Сохраняем Uri сделанной фотографии
                    activity.runOnUiThread {
                        onPhotoTaken(savedUri)
                        Toast.makeText(activity, "Photo saved: $savedUri", Toast.LENGTH_SHORT).show()
                    }
                    Log.d("CameraUtils", "Photo saved: $savedUri")
                }

                override fun onError(exception: ImageCaptureException) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Error saving photo: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    Log.e("CameraUtils", "Error saving photo: ${exception.message}")
                }
            }
        )
    }

    fun stopCamera() {
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }

    fun allPermissionsGranted(activity: Activity) = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }

    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
}
