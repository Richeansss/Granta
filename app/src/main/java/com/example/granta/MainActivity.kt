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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.granta.ui.theme.GrantaTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


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
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp

        val rectWidth = 300.dp // Ширина прямоугольника
        val rectHeight = 200.dp // Высота прямоугольника

        // Рассчитываем начальные координаты для центрирования прямоугольника
        val initialLeft = (screenWidth - rectWidth) / 2
        val initialTop = (screenHeight - rectHeight) / 2
        val initialRight = (screenWidth + rectWidth) / 2
        val initialBottom = (screenHeight + rectHeight) / 2

        // Используем эти начальные значения
        var left by remember { mutableStateOf(initialLeft) }
        var top by remember { mutableStateOf(initialTop) }
        var right by remember { mutableStateOf(initialRight) }
        var bottom by remember { mutableStateOf(initialBottom) }

        var isResizing by remember { mutableStateOf(false) }
        var activeBorder by remember { mutableStateOf<Border?>(null) }
        var dragStartX by remember { mutableStateOf(0f) }
        var dragStartY by remember { mutableStateOf(0f) }
        var originalLeft by remember { mutableStateOf(0.dp) }
        var originalTop by remember { mutableStateOf(0.dp) }
        var originalRight by remember { mutableStateOf(0.dp) }
        var originalBottom by remember { mutableStateOf(0.dp) }

        // Вывод значений до и после округления в консоль для отладки
        println("initialLeft: $initialLeft, initialTop: $initialTop, initialRight: $initialRight, initialBottom: $initialBottom")

        val roundedLeft = left.value.roundToInt()
        val roundedTop = top.value.roundToInt()
        val roundedRight = right.value.roundToInt()
        val roundedBottom = bottom.value.roundToInt()
        println("roundedLeft: $roundedLeft, roundedTop: $roundedTop, roundedRight: $roundedRight, roundedBottom: $roundedBottom")

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(color = Color.Transparent),
            contentAlignment = Alignment.Center // Центрируем содержимое по центру экрана
        ) {
            val borderThickness = 4.dp
            val expandedBorderThickness = 24.dp

            // Прямоугольник с толстой границей
            Box(
                modifier = Modifier
                    .size(width = right - left, height = bottom - top)
                    .offset(x = left, y = top) // Используем смещение для центрирования прямоугольника
                    .background(color = Color.Transparent)
                    .border(width = borderThickness, color = Color.Black, shape = RectangleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStartX = offset.x
                                dragStartY = offset.y
                                originalLeft = left
                                originalTop = top
                                originalRight = right
                                originalBottom = bottom
                                val x = offset.x
                                val y = offset.y
                                val leftBorder = x <= borderThickness.toPx() + expandedBorderThickness.toPx()
                                val rightBorder = x >= (right - left).toPx() - borderThickness.toPx() - expandedBorderThickness.toPx()
                                val topBorder = y <= borderThickness.toPx() + expandedBorderThickness.toPx()
                                val bottomBorder = y >= (bottom - top).toPx() - borderThickness.toPx() - expandedBorderThickness.toPx()

                                activeBorder = when {
                                    leftBorder && topBorder -> Border.TOP_LEFT
                                    leftBorder && bottomBorder -> Border.BOTTOM_LEFT
                                    rightBorder && topBorder -> Border.TOP_RIGHT
                                    rightBorder && bottomBorder -> Border.BOTTOM_RIGHT
                                    leftBorder -> Border.LEFT
                                    rightBorder -> Border.RIGHT
                                    topBorder -> Border.TOP
                                    bottomBorder -> Border.BOTTOM
                                    else -> null
                                }

                                if (activeBorder != null) {
                                    isResizing = true
                                }
                            },
                            onDragEnd = {
                                isResizing = false
                                activeBorder = null
                            },
                            onDrag = { change, dragAmount ->
                                if (isResizing && activeBorder != null) {
                                    val offsetX = change.position.x - dragStartX
                                    val offsetY = change.position.y - dragStartY

                                    when (activeBorder) {
                                        Border.LEFT -> {
                                            val newRight = right
                                            val newLeft = (left + offsetX.dp).coerceIn(0.dp, right - rectWidth)
                                            if (newRight - newLeft >= rectWidth) {
                                                left = newLeft
                                            }
                                        }
                                        Border.RIGHT -> {
                                            val newRight = (right + offsetX.dp).coerceIn(left + rectWidth, screenWidth)
                                            if (newRight - left >= rectWidth) {
                                                right = newRight
                                            }
                                        }
                                        Border.TOP -> {
                                            val newBottom = bottom
                                            val newTop = (top + offsetY.dp).coerceIn(0.dp, bottom - rectHeight)
                                            if (newBottom - newTop >= rectHeight) {
                                                top = newTop
                                            }
                                        }
                                        Border.BOTTOM -> {
                                            val newBottom = (bottom + offsetY.dp).coerceIn(top + rectHeight, screenHeight)
                                            if (newBottom - top >= rectHeight) {
                                                bottom = newBottom
                                            }
                                        }
                                        else -> {}
                                    }

                                    onRectChanged(
                                        Rect(
                                            left = left.value.roundToInt(),
                                            top = top.value.roundToInt(),
                                            right = right.value.roundToInt(),
                                            bottom = bottom.value.roundToInt()
                                        )
                                    )
                                }
                            }
                        )
                    }
            )

            // Темная область за пределами прямоугольника
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = Color.Black.copy(alpha = 0.5f))
            )

            // Отображение координат центра экрана
            Text(
                text = "Screen Center: (${screenWidth.value / 2}, ${screenHeight.value / 2})",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.Center)
                    .background(color = Color.Red)
            )
        }
    }

    // Определение сторон границ для изменения размера
    enum class Border {
        LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, BOTTOM_LEFT, TOP_RIGHT, BOTTOM_RIGHT
    }

    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)






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
