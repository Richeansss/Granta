package com.example.granta

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
                                    // Log or use the rect and orientation as needed
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
        onRectChanged: (android.graphics.Rect, String) -> Unit
    ) {
        val configuration = LocalConfiguration.current

        // Логирование текущей ориентации перед оператором when
        Log.d("Orientation", "Current orientation rect: ${configuration.orientation}")

        // Оператор when для определения ширины и высоты в зависимости от ориентации
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

        // Используем LaunchedEffect и snapshotFlow для отслеживания изменений конфигурации
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

        // State to hold the current rectangle coordinates and orientation
        var currentRect by remember { mutableStateOf(android.graphics.Rect(0, 0, 0, 0)) }
        var currentOrientation by remember { mutableStateOf("Undefined") }

        // Define a function to handle rectangle and orientation changes
        val handleRectAndOrientationChange: (android.graphics.Rect, String) -> Unit = { rect, orientation ->
            currentRect = rect
            currentOrientation = orientation
            // Log.d("RectAndOrientationListener", "Rect: $rect, Orientation: $orientation")
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
                        preview
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
                    // Crop the image using the provided rectangle
                    val croppedBitmap = cropImage(Uri.fromFile(photoFile), rect, context, orientation)
                    // Save the cropped image
                    val croppedFile = saveBitmapToFile(croppedBitmap, context)
                    // Notify the caller about the saved photo URI
                    onPhotoTaken(Uri.fromFile(croppedFile))
                    showToast(context, "Photo saved: $savedUri")
                }

                override fun onError(exception: ImageCaptureException) {
                    showToast(context, "Error saving photo: ${exception.message}")
                }
            }
        )
    }

    private fun cropImage(uri: Uri, rect: Rect, context: Context, orientation: String): Bitmap {
        // Load the original image from URI
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)

        // Log the orientation
        Log.d("CropImage", "Orientation during crop: $orientation")
        Log.d("CropImage", "Rect during crop: left=${rect.left}, top=${rect.top}, right=${rect.right}, bottom=${rect.bottom}")

        // Calculate the cropping coordinates in the original image
        val left = rect.left * originalBitmap.width / context.resources.displayMetrics.widthPixels
        val top = rect.top * originalBitmap.height / context.resources.displayMetrics.heightPixels
        val right = rect.right * originalBitmap.width / context.resources.displayMetrics.widthPixels
        val bottom = rect.bottom * originalBitmap.height / context.resources.displayMetrics.heightPixels

        // Crop the original image
        return Bitmap.createBitmap(originalBitmap, left, top, right - left, bottom - top)
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