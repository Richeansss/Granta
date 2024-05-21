package com.example.granta

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.granta.ui.theme.GrantaTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var textRecognizer: TextRecognizer
    private lateinit var imageCapture: ImageCapture
    private var isCameraInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textRecognizer = TextRecognizer(this)
        imageCapture = ImageCapture.Builder().build()

        if (CameraUtils.allPermissionsGranted(this)) {
            startCamera()
            isCameraInitialized = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                CameraUtils.REQUIRED_PERMISSIONS,
                CameraUtils.REQUEST_CODE_PERMISSIONS
            )
        }

        setContent {
            GrantaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var photoUri by remember { mutableStateOf<Uri?>(null) }
                    var isPhotoTaken by remember { mutableStateOf(false) }

                    when {
                        !isPhotoTaken && photoUri == null -> {
                            imageRecognitionMenu(
                                onTakePhoto = {
                                    isPhotoTaken = true
                                },
                                onRecognizeText = {
                                    // No action needed here, text recognition will be available after taking a photo
                                }
                            )
                        }
                        isPhotoTaken -> {
                            CameraContent(onPhotoTaken = { uri ->
                                photoUri = uri
                                isPhotoTaken = false
                            })
                        }
                        photoUri != null -> {
                            imageRecognitionScreen(textRecognizer, photoUri) {
                                // Reset state after recognition is done
                                photoUri = null
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textRecognizer.release()
        CameraUtils.stopCamera()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CameraUtils.REQUEST_CODE_PERMISSIONS) {
            if (CameraUtils.allPermissionsGranted(this)) {
                startCamera()
                isCameraInitialized = true
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    imageCapture
                )
            } catch (exc: Exception) {
                showToast("Error starting camera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Composable
    fun RectangularOverlay(
        modifier: Modifier = Modifier,
        onRectChanged: (Rect) -> Unit
    ) {
        var rect by remember { mutableStateOf(Rect(100, 100, 400, 400)) } // Initial size and position of the rectangle
        var isDragging by remember { mutableStateOf(false) }
        var startOffsetX by remember { mutableStateOf(0f) }
        var startOffsetY by remember { mutableStateOf(0f) }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(color = Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            // Rectangle with border
            Box(
                modifier = Modifier
                    .size(with(LocalDensity.current) { (rect.right - rect.left).toDp() }, with(LocalDensity.current) { (rect.bottom - rect.top).toDp() })
                    .offset { IntOffset(rect.left, rect.top) }
                    .background(color = Color.Transparent)
                    .border(width = 2.dp, color = Color.Black, shape = RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val hitTestResult = with(LocalDensity) {
                                    rect.contains(offset.x.toInt(), offset.y.toInt())
                                }
                                if (hitTestResult) {
                                    isDragging = true
                                    startOffsetX = offset.x - rect.left
                                    startOffsetY = offset.y - rect.top
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                            },
                            onDrag = { change, dragAmount ->
                                if (isDragging) {
                                    rect = Rect(
                                        (change.position.x - startOffsetX).toInt().coerceIn(0, (size.width - rect.width()).toInt()),
                                        (change.position.y - startOffsetY).toInt().coerceIn(0, (size.height - rect.height()).toInt()),
                                        (change.position.x - startOffsetX + rect.width()).toInt().coerceIn(rect.width(), size.width.toInt()),
                                        (change.position.y - startOffsetY + rect.height()).toInt().coerceIn(rect.height(), size.height.toInt())
                                    )
                                    onRectChanged(rect)
                                }
                            }
                        )
                    }
            )

            // Dark overlay outside the rectangle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = Color.Black.copy(alpha = 0.5f))
            )

            // Invisible box to handle dragging for resizing the rectangle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val hitTestResult = with(LocalDensity) {
                                    !rect.contains(offset.x.toInt(), offset.y.toInt())
                                }
                                if (hitTestResult) {
                                    isDragging = true
                                    startOffsetX = offset.x - rect.left
                                    startOffsetY = offset.y - rect.top
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                            },
                            onDrag = { change, dragAmount ->
                                if (isDragging) {
                                    rect = Rect(
                                        rect.left,
                                        rect.top,
                                        (change.position.x - startOffsetX).toInt().coerceIn(rect.left + 50, (size.width - rect.left).toInt()),
                                        (change.position.y - startOffsetY).toInt().coerceIn(rect.top + 50, (size.height - rect.top).toInt())
                                    )
                                    onRectChanged(rect)
                                }
                            }
                        )
                    }
            )
        }
    }



    @Composable
    fun CameraContent(onPhotoTaken: (Uri) -> Unit) {
        val context = LocalContext.current
        val previewView = remember { PreviewView(context) }
        val overlayRect = remember { mutableStateOf(Rect(100, 100, 400, 400)) }

        LaunchedEffect(Unit) {
            val cameraProvider = ProcessCameraProvider.getInstance(context)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.addListener({
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                try {
                    cameraProvider.get().unbindAll()
                    cameraProvider.get().bindToLifecycle(
                        context as LifecycleOwner,
                        cameraSelector,
                        preview,
                        ImageCapture.Builder().build()
                    )
                } catch (exc: Exception) {
                    Toast.makeText(context, "Error starting camera: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            }, ContextCompat.getMainExecutor(context))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            RectangularOverlay(
                modifier = Modifier.fillMaxSize(),
                onRectChanged = { rect ->
                    overlayRect.value = rect
                }
            )
            CaptureButton(
                onClick = { takePhoto(context, overlayRect.value, onPhotoTaken) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp) // Поднимаем кнопку на 16 dp, чтобы она не перекрывалась прямоугольной областью
            )
        }
    }


    @Composable
    fun CaptureButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Button(
            onClick = onClick,
            modifier = modifier
        ) {
            Text("Capture")
        }
    }

    private fun takePhoto(context: Context, rect: Rect, onPhotoTaken: (Uri) -> Unit) {
        // Assuming ImageCapture instance is initialized
        val imageCapture = ImageCapture.Builder().build()

        val photoFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    onPhotoTaken(savedUri)
                    Toast.makeText(context, "Photo saved: $savedUri", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(context, "Error saving photo: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}
