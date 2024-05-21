package com.example.granta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun imageRecognitionScreen(textRecognizer: TextRecognizer, photoUri: Uri?, onTakePhoto: () -> Unit) {
    var recognizedText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isTextRecognized by remember { mutableStateOf(false) }
    var recognitionTimeSeconds by remember { mutableStateOf(0L) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    val context = LocalContext.current as MainActivity

    // Загрузка изображения по Uri
    if (photoUri != null && bitmap == null) {
        bitmap = loadBitmapFromUri(context, photoUri)
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                bitmap?.let {
                    ImageSection(it)
                }
            }
            item {
                CameraScreen(onTakePhoto)
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
}

fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        // Получаем ориентацию изображения из EXIF-метаданных
        val exifInputStream = context.contentResolver.openInputStream(uri)
        val exif = ExifInterface(exifInputStream!!)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        exifInputStream.close()

        // Поворачиваем изображение в соответствии с ориентацией
        val rotatedBitmap = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }

        rotatedBitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

@Composable
fun CameraScreen(onTakePhoto: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) {
            Text("Сделать фотографию")
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
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("Распознать текст")
    }
}

@Composable
fun imageRecognitionMenu(onTakePhoto: () -> Unit, onRecognizeText: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onTakePhoto) {
            Text("Сделать фотографию")
        }
        Button(onClick = onRecognizeText) {
            Text("Распознать текст")
        }
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
