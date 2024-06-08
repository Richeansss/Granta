package com.example.granta.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.granta.R
import java.io.File
import java.io.FileOutputStream
import java.lang.Integer.max
import java.lang.Integer.min
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService

const val CAMERA_FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

object CameraUtils {
    private lateinit var cameraExecutor: ExecutorService

    const val REQUEST_CODE_PERMISSIONS = 10
    val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    fun stopCamera() {
        if (CameraUtils::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
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
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun allPermissionsGranted(activity: Activity) = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }

    fun cropImage(uri: Uri, rect: Rect, context: Context): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                Log.e("CropImage", "Failed to load the original image.")
                return null
            }

            val exif = context.contentResolver.openInputStream(uri)?.use { androidx.exifinterface.media.ExifInterface(it) }
            val rotationAngle = when (exif?.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            val matrix = Matrix().apply { postRotate(rotationAngle) }
            val rotatedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )

            val screenWidth = context.resources.displayMetrics.widthPixels
            val screenHeight = context.resources.displayMetrics.heightPixels
            val screenAspectRatio = screenWidth.toFloat() / screenHeight
            val imageAspectRatio = rotatedBitmap.width.toFloat() / rotatedBitmap.height
            val scaleFactor = if (screenAspectRatio > imageAspectRatio) {
                rotatedBitmap.width.toFloat() / screenWidth
            } else {
                rotatedBitmap.height.toFloat() / screenHeight
            }

            val scaledRectWidth = (rect.width() * scaleFactor).toInt()
            val scaledRectHeight = (rect.height() * scaleFactor).toInt()
            val centerX = rotatedBitmap.width / 2
            val centerY = rotatedBitmap.height / 2
            val correctedLeft = max(0, centerX - scaledRectWidth / 2)
            val correctedTop = max(0, centerY - scaledRectHeight / 2)
            val correctedRight = min(rotatedBitmap.width, centerX + scaledRectWidth / 2)
            val correctedBottom = min(rotatedBitmap.height, centerY + scaledRectHeight / 2)

            if (correctedTop >= correctedBottom || correctedLeft >= correctedRight) {
                Log.e("CropImage", "Incorrect crop coordinates: top >= bottom or left >= right")
                return null
            }

            Bitmap.createBitmap(rotatedBitmap, correctedLeft, correctedTop, correctedRight - correctedLeft, correctedBottom - correctedTop)
        } catch (e: Exception) {
            Log.e("CropImage", "Error cropping image: ${e.message}", e)
            null
        }
    }

    fun saveBitmapToFile(bitmap: Bitmap, context: Context): File {
        val photoFile = createFile(context, "_cropped")
        val outputStream = FileOutputStream(photoFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        return photoFile
    }

    fun createFile(context: Context, suffix: String = ""): File {
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, context.resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        val directory = mediaDir ?: context.filesDir
        return File(directory, SimpleDateFormat(CAMERA_FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + suffix + ".jpg")
    }

    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}