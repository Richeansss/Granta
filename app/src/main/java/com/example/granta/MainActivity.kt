package com.example.granta

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import java.io.FileOutputStream
import java.lang.Integer.max
import java.lang.Integer.min
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
                                onRectChanged = { rect, orientation ->
                                    // Handle the rectangle change if needed
                                    Log.d("MainActivity", "Rect: $rect, Orientation: $orientation")
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
            val preview = Preview.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA


            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                showToast("Error starting camera: ${exc.message}")
                Log.e("MainActivity", "Error starting camera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Composable
    fun RectangularOverlay(
        modifier: Modifier = Modifier,
        onRectChanged: (android.graphics.Rect, String) -> Unit
    ) {
        val configuration = LocalConfiguration.current

        Log.d("Orientation", "Current orientation rect: ${configuration.orientation}")

        val (rectWidth: Dp, rectHeight: Dp) = when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> 600.dp to 300.dp
            else -> 300.dp to 200.dp
        }

        val orientationName = when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "Landscape"
            Configuration.ORIENTATION_PORTRAIT -> "Portrait"
            else -> "Undefined"
        }

        Box(
            modifier = modifier.fillMaxSize()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val screenWidth = size.width.toDp()
                val screenHeight = size.height.toDp()

                val rectLeft = (screenWidth - rectWidth) / 2
                val rectTop = (screenHeight - rectHeight) / 2
                val rectRight = rectLeft + rectWidth
                val rectBottom = rectTop + rectHeight

                drawRect(
                    color = Color.Black.copy(alpha = 0.7f),
                    size = size
                )

                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(rectLeft.toPx(), rectTop.toPx()),
                    size = androidx.compose.ui.geometry.Size(rectWidth.toPx(), rectHeight.toPx()),
                    blendMode = BlendMode.Clear
                )

                onRectChanged(
                    android.graphics.Rect(
                        rectLeft.toPx().toInt(),
                        rectTop.toPx().toInt(),
                        rectRight.toPx().toInt(),
                        rectBottom.toPx().toInt()
                    ),
                    orientationName
                )
            }
        }
    }

    @Composable
    fun ScreenSizeListener(onSizeChanged: (Int, Int, String) -> Unit) {
        val configuration = LocalConfiguration.current

        LaunchedEffect(Unit) {
            snapshotFlow { configuration }
                .collect { newConfiguration ->
                    val orientationName = when (newConfiguration.orientation) {
                        Configuration.ORIENTATION_LANDSCAPE -> "Landscape"
                        Configuration.ORIENTATION_PORTRAIT -> "Portrait"
                        else -> "Undefined"
                    }
                    Log.d("Orientation", "Orientation: $orientationName")
                    onSizeChanged(newConfiguration.screenWidthDp, newConfiguration.screenHeightDp, orientationName)
                    Log.d("ScreenSizeListener", "Screen size changed: width=${newConfiguration.screenWidthDp}, height=${newConfiguration.screenHeightDp}, orientation=$orientationName")
                }
        }
    }

    @Composable
    fun CameraContent(
        onPhotoTaken: (Uri) -> Unit,
        onRectChanged: (android.graphics.Rect, String) -> Unit
    ) {
        val context = LocalContext.current
        val previewView = remember { PreviewView(context) }

        var currentRect by remember { mutableStateOf(android.graphics.Rect(0, 0, 0, 0)) }
        var currentOrientation by remember { mutableStateOf("Undefined") }

        val handleRectAndOrientationChange: (android.graphics.Rect, String) -> Unit = { rect, orientation ->
            currentRect = rect
            currentOrientation = orientation
            onRectChanged(rect, orientation)
        }

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
                        imageCapture
                    )
                } catch (exc: Exception) {
                    Log.e("CameraContent", "Error starting camera: ${exc.message}")
                }
            }, ContextCompat.getMainExecutor(context))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            RectangularOverlay(modifier = Modifier.fillMaxSize()) { rect, orientation ->
                handleRectAndOrientationChange(rect, orientation)
            }

            CaptureButton(
                onClick = { takePhoto(context, onPhotoTaken, currentRect, currentOrientation, previewView.display.rotation) },
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

    private fun takePhoto(
        context: Context,
        onPhotoTaken: (Uri) -> Unit,
        rect: Rect,
        orientation: String,
        rotation: Int
    ) {
        val photoFile = File(
            getOutputDirectory(context),
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
                    val croppedBitmap = cropImage(Uri.fromFile(photoFile), rect, context, orientation)
                    if (croppedBitmap != null) {
                        val croppedFile = saveBitmapToFile(croppedBitmap, context)
                        onPhotoTaken(Uri.fromFile(croppedFile))
                        showToast(context, "Photo saved: $savedUri")
                    } else {
                        showToast(context, "Error cropping photo: cropped bitmap is null")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    showToast(context, "Error saving photo: ${exception.message}")
                }
            }
        )
    }

    private fun cropImage(uri: Uri, rect: Rect, context: Context, orientation: String): Bitmap? {
        try {
            // Открыть поток для чтения изображения из URI
            val inputStream = context.contentResolver.openInputStream(uri)

            // Загрузить оригинальное изображение из потока
            val originalBitmap = BitmapFactory.decodeStream(inputStream)

            // Проверка на null, если оригинальное изображение не удалось загрузить
            if (originalBitmap == null) {
                Log.e("CropImage", "Failed to load the original image.")
                return null
            }

            // Вычисление угла поворота изображения в зависимости от ориентации устройства
            val rotationAngle = when (orientation) {
                "Portrait" -> 90f
                else -> 0f
            }

            // Создание матрицы для поворота изображения
            val matrix = Matrix().apply {
                postRotate(rotationAngle)
            }

            // Поворот оригинального изображения
            val rotatedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )

            // Преобразование координат прямоугольника с экрана в координаты оригинального изображения
            val left = (rect.left.toFloat() * rotatedBitmap.width / context.resources.displayMetrics.widthPixels).toInt()
            val top = (rect.top.toFloat() * rotatedBitmap.height / context.resources.displayMetrics.heightPixels).toInt()
            val right = (rect.right.toFloat() * rotatedBitmap.width / context.resources.displayMetrics.widthPixels).toInt()
            val bottom = (rect.bottom.toFloat() * rotatedBitmap.height / context.resources.displayMetrics.heightPixels).toInt()

            // Дополнительные корректировки для более точной обрезки
            val epsilon = 1 // Допустимое отклонение
            val correctedLeft = max(0, left - epsilon)
            val correctedTop = max(0, top - epsilon)
            val correctedRight = min(rotatedBitmap.width, right + epsilon)
            val correctedBottom = min(rotatedBitmap.height, bottom + epsilon)

            // Обрезка изображения с учетом преобразованных и скорректированных координат прямоугольника
            val croppedBitmap = Bitmap.createBitmap(rotatedBitmap, correctedLeft, correctedTop, correctedRight - correctedLeft, correctedBottom - correctedTop)

            return croppedBitmap
        } catch (e: Exception) {
            Log.e("CropImage", "Error cropping image: ${e.message}")
            return null
        }
    }


    private fun saveBitmapToFile(bitmap: Bitmap, context: Context): File {
        val photoFile = File(
            getOutputDirectory(context),
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + "_cropped.jpg"
        )
        val outputStream = FileOutputStream(photoFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        return photoFile
    }

    private fun getOutputDirectory(context: Context): File {
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, context.resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else context.filesDir
    }

    private fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}
