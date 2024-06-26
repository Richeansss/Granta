package com.example.granta.recognition

import android.content.Context
import android.graphics.*
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class TextRecognizer(private val context: Context) {

    private val tessBaseAPI: TessBaseAPI = TessBaseAPI()

    init {
        val tessDataPath = getTessDataPath()
        val trainedDataFile = File(tessDataPath, "tessdata/rus.traineddata")
        if (!trainedDataFile.exists()) {
            Log.e("TextRecognizer", "Trained data file does not exist at ${trainedDataFile.absolutePath}")
            throw IllegalArgumentException("Data file not found at ${trainedDataFile.absolutePath}")
        }
        tessBaseAPI.init(tessDataPath, "rus")
    }

    private fun getTessDataPath(): String {
        val tessDataDir = File(context.filesDir, "tessdata")
        if (!tessDataDir.exists()) {
            tessDataDir.mkdir()
        }

        copyTrainedDataToFilesDir("rus.traineddata", tessDataDir)

        return context.filesDir.absolutePath
    }

    private fun copyTrainedDataToFilesDir(fileName: String, tessDataDir: File) {
        try {
            val assetManager = context.assets
            val outFile = File(tessDataDir, fileName)
            if (!outFile.exists()) {
                assetManager.open("tessdata/$fileName").use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("TextRecognizer", "Copied $fileName to ${outFile.absolutePath}")
            }
        } catch (e: IOException) {
            Log.e("TextRecognizer", "Error copying tessdata files", e)
        }
    }

    suspend fun recognizeText(bitmap: Bitmap, whitelist: String = ""): String {
        return coroutineScope {
            // Выполняем увеличение размера изображения асинхронно
            val scaledBitmap = async(Dispatchers.Default) {
                Bitmap.createScaledBitmap(bitmap, bitmap.width * 2, bitmap.height * 2, false)
            }.await()

            // Предобрабатываем изображение и распознаем текст
            val preprocessedText = withContext(Dispatchers.Default) {
                val preprocessedBitmap = preprocessBitmap(scaledBitmap)
                tessBaseAPI.setImage(preprocessedBitmap)
                if (whitelist.isNotEmpty()) {
                    tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, whitelist)
                }
                filterText(tessBaseAPI.utF8Text)
            }

            // Возвращаем результат
            preprocessedText
        }
    }



    private fun filterText(text: String): String {
        val unwantedSymbolsRegex = (
                "[|_—{}\\[\\]~<>\\\\/\"\\-*:…°&`;!'\"№‘’“”„‟‹›«»《》„‚„©®™.,iЁ" + // Все добавленные символы
                        "′″“”’‹›+=@#$%^&*()£€¥¢₹∞§¶÷×±√∫≈∑∆∇" +
                        "µ∏π∂⊥∩∪∈∉⊂⊃⊆⊇∀∃∄∅∫∬∮∞ℵℶℷℸℏℑ℘ℜ" +
                        "ℶℷℸ↔↕↖↗↘↙↚↛↮↯↲↳↴↵↶↷⇌⇎⇕⇖⇗⇘⇙⇚⇛⇦⇧⇨⇩⇪⌅⌆⌛⌜⌝⌞⌟" +
                        "⌠⌡⍇⍈⍍⍎⍏⍐⍑⍒⍓⍔⍕⍖⍗⍘⍙⍚⍛⍜⍝⍞⍟⍠⍡⍢⍣⍤⍥⍦⍧⍨" +
                        "⍩⍪⍫⍬⍭⍮⍯⍰⍱⍲⍳⍴⍵⍶⍷⍸⍹⍺⎈⎉⎊⎋⎌⎍⎎⎏⎐⎑⎒⎓" +
                        "⎔⎕⎖⎗⎘⎙⎚⎛⎜⎝⎞⎟⎠⎡⎢⎣⎤⎥⎦⎧⎨⎩⎪⎫⎬⎭⎮⎯⏐⏑⏒⏓" +
                        "⏔⏕⏖⏗⏘⏙⏚⏛⏜⏝⏞⏟⏠⏡⏢⏣⏤⏥⏦⏧⏨⏩⏪⏫⏬⏭⏮⏯" +
                        "⏰⏱⏲⏳⏴⏵⏶⏷⏸⏹⏺⏻⏼⏽⏾⏿]+" // исключены символы, недопустимые для Android SDK
                ).toRegex()
        return text.replace(unwantedSymbolsRegex, "").trim()
    }

    private suspend fun preprocessBitmap(bitmap: Bitmap): Bitmap = coroutineScope {
        val denoisedBitmap = async(Dispatchers.Default) { applyFastMedianFilter(bitmap, 3) }
        val bwBitmap = async(Dispatchers.Default) { convertToBlackWhite(denoisedBitmap.await()) }
        val enhancedBitmap = async(Dispatchers.Default) { enhanceContrast(bwBitmap.await()) }
        enhancedBitmap.await()
    }

    private fun applyFastMedianFilter(bitmap: Bitmap, windowSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val filteredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Размер окна должен быть нечетным, чтобы у нас был центральный пиксель
        val halfWindowSize = windowSize / 2

        // Проходим по каждому пикселю изображения
        for (x in 0 until width) {
            for (y in 0 until height) {
                val medianColor = getFastMedianColor(bitmap, x, y, halfWindowSize)
                filteredBitmap.setPixel(x, y, medianColor)
            }
        }

        return filteredBitmap
    }

    private fun getFastMedianColor(bitmap: Bitmap, x: Int, y: Int, halfWindowSize: Int): Int {
        val colors = mutableListOf<Int>()

        // Заполняем список цветов из окрестности пикселя (окно размером windowSize x windowSize)
        for (i in -halfWindowSize..halfWindowSize) {
            for (j in -halfWindowSize..halfWindowSize) {
                val neighborX = x + i
                val neighborY = y + j
                if (neighborX in 0 until bitmap.width && neighborY in 0 until bitmap.height) {
                    colors.add(bitmap.getPixel(neighborX, neighborY))
                }
            }
        }

        // Сортируем список цветов и возвращаем медианный цвет
        colors.sort()
        val medianIndex = colors.size / 2
        return colors[medianIndex]
    }

    private fun convertToBlackWhite(bitmap: Bitmap): Bitmap {
        val bwBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bwBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        val filter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return bwBitmap
    }

    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val threshold = getOtsuThreshold(bitmap)
        val binaryBitmap = applyAdaptiveThreshold(bitmap, threshold)
        return binaryBitmap
    }

    private fun getOtsuThreshold(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val histData = IntArray(256)
        for (pixel in pixels) {
            val red = pixel shr 16 and 0xFF
            val green = pixel shr 8 and 0xFF
            val blue = pixel and 0xFF
            val luminance = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
            histData[luminance]++
        }

        val total = bitmap.width * bitmap.height
        var sum = 0
        for (t in 0..255) sum += t * histData[t]

        var sumB = 0
        var wB = 0
        var wF: Int
        var mB: Double
        var mF: Double
        var max = 0.0
        var between = 0.0
        var threshold1 = 0
        var threshold2 = 0

        for (t in 0..255) {
            wB += histData[t]
            if (wB == 0) continue
            wF = total - wB
            if (wF == 0) break
            sumB += t * histData[t]
            mB = sumB.toDouble() / wB
            mF = (sum - sumB).toDouble() / wF
            between = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            if (between >= max) {
                threshold1 = t
                if (t >= threshold1) {
                    threshold2 = t
                    max = between
                }
            }
        }
        return (threshold1 + threshold2) / 2
    }

    private fun applyAdaptiveThreshold(bitmap: Bitmap, threshold: Int): Bitmap {
        val binaryBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in 0 until bitmap.width * bitmap.height) {
            val color = if ((pixels[i] and 0xFF) > threshold) {
                0xFFFFFFFF.toInt() // белый
            } else {
                0xFF000000.toInt() // черный
            }
            pixels[i] = color
        }
        binaryBitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return binaryBitmap
    }

    fun release() {
        tessBaseAPI.end()
    }

}


