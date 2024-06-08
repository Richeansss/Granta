package com.example.granta

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.granta.camera.CameraUtils
import com.example.granta.camera.CameraViewModel
import com.example.granta.recognition.TextRecognizer
import com.example.granta.ui.theme.GrantaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ImageRecognizerActivity : AppCompatActivity() {

    private lateinit var textRecognizer: TextRecognizer
    private lateinit var imageCapture: ImageCapture
    private val cameraViewModel: CameraViewModel by viewModels()

    private val pickImageRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_recognizer)

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
                    MainScreen()
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

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, pickImageRequestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickImageRequestCode && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                setContent {
                    GrantaTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            MainScreen(photoUri = uri)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun MainScreen(photoUri: Uri? = null) {
        val isPhotoTaken by cameraViewModel.isPhotoTaken.collectAsState()

        when {
            !isPhotoTaken && photoUri == null -> {
                displayMenu()
            }

            isPhotoTaken -> {
                displayCameraContent()
            }

            photoUri != null -> {
                displayImageRecognitionScreen(photoUri)
            }
        }
    }

    @Composable
    fun displayMenu() {
        imageRecognitionMenu(
            onTakePhoto = { cameraViewModel.setPhotoTaken(true) },
            onSelectPhoto = { openFilePicker() }
        )
    }

    @Composable
    fun imageRecognitionMenu(onTakePhoto: () -> Unit, onSelectPhoto: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = onTakePhoto) {
                Text("Сделать фотографию")
            }
            Button(onClick = onSelectPhoto) {
                Text("Выбрать фотографию")
            }
        }
    }

    @Composable
    fun displayCameraContent() {
        CameraContent(
            onPhotoTaken = { uri ->
                setContent {
                    GrantaTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            MainScreen(photoUri = uri)
                        }
                    }
                }
                cameraViewModel.setPhotoTaken(false)
            },
            onRectChanged = { rect, orientation ->
                Log.d("ImageRecognitionActivity", "Rect: $rect, Orientation: $orientation")
            }
        )
    }

    @Composable
    fun displayImageRecognitionScreen(photoUri: Uri) {
        imageRecognitionScreen(textRecognizer, photoUri) {
            setContent {
                GrantaTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen()
                    }
                }
            }
        }
    }

    @Composable
    fun RectangularOverlay(
        modifier: Modifier = Modifier,
        onRectChanged: (Rect, String) -> Unit
    ) {
        val configuration = LocalConfiguration.current
        val (rectWidth: Dp, rectHeight: Dp) = when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> 600.dp to 300.dp
            else -> 300.dp to 200.dp
        }

        val orientationName = when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "Landscape"
            Configuration.ORIENTATION_PORTRAIT -> "Portrait"
            else -> "Undefined"
        }

        Box(modifier = modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val screenWidth = size.width.toDp()
                val screenHeight = size.height.toDp()
                val rectLeft = (screenWidth - rectWidth) / 2
                val rectTop = (screenHeight - rectHeight) / 2
                val rectRight = rectLeft + rectWidth
                val rectBottom = rectTop + rectHeight

                drawRect(color = Color.Black.copy(alpha = 0.7f), size = size)
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(rectLeft.toPx(), rectTop.toPx()),
                    size = androidx.compose.ui.geometry.Size(rectWidth.toPx(), rectHeight.toPx()),
                    blendMode = BlendMode.Clear
                )

                onRectChanged(
                    Rect(
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
    fun CameraContent(
        onPhotoTaken: (Uri) -> Unit,
        onRectChanged: (Rect, String) -> Unit
    ) {
        val context = LocalContext.current
        val previewView = remember { PreviewView(context) }

        var currentRect by remember { mutableStateOf(Rect(0, 0, 0, 0)) }
        var currentOrientation by remember { mutableStateOf("Undefined") }

        DisposableEffect(Unit) {
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

            onDispose {
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            RectangularOverlay(modifier = Modifier.fillMaxSize()) { rect, orientation ->
                currentRect = rect
                currentOrientation = orientation
                onRectChanged(rect, orientation)
            }
            CaptureButton(
                onClick = {
                    takePhoto(context, onPhotoTaken, currentRect)
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }



    @Composable
    fun CaptureButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
        Button(onClick = onClick, modifier = modifier) {
            Text("Capture")
        }
    }

    private fun takePhoto(
        context: Context,
        onPhotoTaken: (Uri) -> Unit,
        rect: Rect,
    ) {
        val photoFile = CameraUtils.createFile(context)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val croppedBitmap = CameraUtils.cropImage(savedUri, rect, context)
                    if (croppedBitmap != null) {
                        val croppedFile = CameraUtils.saveBitmapToFile(croppedBitmap, context)
                        onPhotoTaken(Uri.fromFile(croppedFile))
                        CameraUtils.showToast(context, "Photo saved: $savedUri")
                    } else {
                        CameraUtils.showToast(context, "Error cropping photo: cropped bitmap is null")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    CameraUtils.showToast(context, "Error saving photo: ${exception.message}")
                }
            }
        )
    }

    @Composable
    fun imageRecognitionScreen(textRecognizer: TextRecognizer, photoUri: Uri?, onTakePhoto: () -> Unit) {
        var recognizedText by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var isTextRecognized by remember { mutableStateOf(false) }
        var recognitionTimeSeconds by remember { mutableStateOf(0L) }
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }

        val context = LocalContext.current as ImageRecognizerActivity

        // Загрузка изображения по Uri
        if (photoUri != null && bitmap == null) {
            bitmap = CameraUtils.loadBitmapFromUri(context, photoUri)
        }

        LaunchedEffect(isLoading) {
            if (isLoading) {
                val startTime = System.currentTimeMillis()
                recognizedText = withContext(Dispatchers.IO) {
                    bitmap?.let { textRecognizer.recognizeText(it) } ?: ""
                }
                val endTime = System.currentTimeMillis()
                val recognitionTimeMillis = endTime - startTime
                recognitionTimeSeconds = recognitionTimeMillis / 1000
                isLoading = false
                isTextRecognized = true
            }
        }

        LaunchedEffect(isLoading) {
            while (isLoading) {
                delay(1000)
                recognitionTimeSeconds++
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Время распознавания: $recognitionTimeSeconds сек",
                    style = TextStyle(fontSize = 14.sp),
                    modifier = Modifier.weight(1f)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    bitmap?.let {
                        ImageSection(it)
                    }
                }
                item {
                    RecognizeTextButton {
                        isLoading = true
                        recognitionTimeSeconds = 0 // Сброс времени до начала распознавания
                    }
                }
                if (isLoading) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "Распознавание текста...",
                                style = TextStyle(fontSize = 14.sp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        loadingIndicator()
                    }
                }
                if (isTextRecognized) {
                    item {
                        Text(
                            text = "Распознанный текст:",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Text(
                            text = recognizedText,
                            style = TextStyle(fontSize = 14.sp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        Box(
        ) {
            CameraMenu(onTakePhoto)
        }
    }

    @Composable
    fun CameraMenu(onTakePhoto: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp), // Добавляем отступы
            verticalArrangement = Arrangement.Bottom, // Размещаем элементы внизу
            horizontalAlignment = Alignment.Start // Размещаем элементы слева
        ) {
            Button(
                onClick = onTakePhoto,
                modifier = Modifier
                    .wrapContentWidth() // Подгоняет ширину под содержимое
                    .padding(end = 16.dp) // Добавляем отступ справа
            ) {
                Text("Назад")
            }
        }
    }


    @Composable
    fun ImageSection(bitmap: Bitmap) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            elevation = CardDefaults.elevatedCardElevation()
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    @Composable
    fun RecognizeTextButton(onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp) // Добавляем отступ сверху
        ) {
            Text("Распознать текст")
        }
    }

    @Composable
    fun loadingIndicator() {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .padding(8.dp)
            )
        }
    }
}