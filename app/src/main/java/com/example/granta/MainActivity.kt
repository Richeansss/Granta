package com.example.granta

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
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
    private val cameraViewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textRecognizer = TextRecognizer(this)
        imageCapture = ImageCapture.Builder().build()

        if (CameraUtils.allPermissionsGranted(this)) {
            startCamera()
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

                    val isPhotoTaken by cameraViewModel.isPhotoTaken.collectAsState()

                    when {
                        !isPhotoTaken && photoUri == null -> {
                            imageRecognitionMenu(
                                onTakePhoto = {
                                    cameraViewModel.setPhotoTaken(true)
                                },
                                onRecognizeText = {
                                    // No action needed here, text recognition will be available after taking a photo
                                }
                            )
                        }
                        isPhotoTaken -> {
                            CameraContent(
                                onPhotoTaken = { uri ->
                                    photoUri = uri
                                    cameraViewModel.setPhotoTaken(false)
                                },
                                onRectChanged = { rect ->
                                    // Handle the rectangle change if needed
                                }
                            )
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
        // Получение конфигурации экрана
        val configuration = LocalConfiguration.current

        // Установка размеров прямоугольника в зависимости от ориентации
        val (rectWidth: Dp, rectHeight: Dp) = when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> 600.dp to 300.dp
            else -> 300.dp to 200.dp
        }

        Box(
            modifier = modifier.fillMaxSize()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Получение размеров экрана
                val screenWidth = size.width.toDp()
                val screenHeight = size.height.toDp()

                // Расчет координат прямоугольника для центрирования
                val rectLeft = (screenWidth - rectWidth) / 2
                val rectTop = (screenHeight - rectHeight) / 2
                val rectRight = rectLeft + rectWidth
                val rectBottom = rectTop + rectHeight

                // Верхний левый угол фона
                drawRect(
                    color = Color.Black.copy(alpha = 0.7f),
                    size = size
                )

                // Прозрачный прямоугольник в центре
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(rectLeft.toPx(), rectTop.toPx()),
                    size = androidx.compose.ui.geometry.Size(rectWidth.toPx(), rectHeight.toPx()),
                    blendMode = BlendMode.Clear
                )

                // Отправка координат прямоугольника через колбэк
                onRectChanged(Rect(rectLeft, rectTop, rectRight, rectBottom))
            }
        }
    }

    data class Rect(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp)

    fun Float.toDp(): Dp = (this / Resources.getSystem().displayMetrics.density).dp

    @Composable
    fun CameraContent(
        onPhotoTaken: (Uri) -> Unit,
        onRectChanged: (Rect) -> Unit
    ) {
        val context = LocalContext.current
        val previewView = remember { PreviewView(context) }

        // State to hold the current rectangle coordinates
        var currentRect by remember { mutableStateOf(Rect(0.dp, 0.dp, 0.dp, 0.dp)) }

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
                        this@MainActivity,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (exc: Exception) {
                    showToast("Error starting camera: ${exc.message}")
                }
            }, ContextCompat.getMainExecutor(context))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            RectangularOverlay(modifier = Modifier.fillMaxSize()) { rect ->
                currentRect = rect // Update the current rectangle coordinates
                onRectChanged(rect) // Pass the rectangle coordinates to the parent
            }

            CaptureButton(
                onClick = { takePhoto(context, onPhotoTaken, currentRect, previewView.display.rotation) },
                modifier = Modifier.align(Alignment.BottomCenter)
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

    private fun takePhoto(context: Context, onPhotoTaken: (Uri) -> Unit, rect: Rect, rotation: Int) {
        val bounds = Rect(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom
        )

        val photoFile = File(
            getOutputDirectory(),
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .setMetadata(ImageCapture.Metadata().apply {
                // Add metadata if needed
            })
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    onPhotoTaken(savedUri)
                    showToast("Photo saved: $savedUri")
                }

                override fun onError(exception: ImageCaptureException) {
                    showToast("Error saving photo: ${exception.message}")
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
