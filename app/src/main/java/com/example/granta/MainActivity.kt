package com.example.granta

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.util.DisplayMetrics
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.granta.databinding.ActivityMainBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var sumHour: Int = 0
    private var sumMoney: Int = 0
    private var moneyPer24Hours: Int = 4000
    private var currentDate = LocalDate.now()
    private var monthlySums: MutableMap<String, Int> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        loadMonthlySums()

        with(binding) {
            buttonToSettings.setOnClickListener {
                showSettingsDialog()
            }

            buttonPrevMonth.setOnClickListener {
                changeMonth(-1)
            }

            buttonNextMonth.setOnClickListener {
                changeMonth(1)
            }

            buttonToImageRecognizer.setOnClickListener {
                val intent = Intent(this@MainActivity, ImageRecognizerActivity::class.java)
                startActivity(intent)
            }

            updateCalendar()
        }
    }

    private fun updateCalendar() {
        // Загрузка данных о суммах часов и денег перед обновлением календаря
        sumHour = sharedPreferences.getInt("${currentDate.year}_${currentDate.monthValue}_sum_hour", 0)
        sumMoney = sharedPreferences.getInt("${currentDate.year}_${currentDate.monthValue}_sum_money", 0)

        with(binding) {
            currentMonthTextView.text = currentDate.month.getDisplayName(TextStyle.FULL, Locale("ru")) + " " + currentDate.year
            val calendarGrid = calendarGrid
            calendarGrid.removeAllViews()

            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val screenWidth = displayMetrics.widthPixels

            val buttonsPerRow = 7
            val buttonMargin = 11 // Маржа кнопок в dp

            // Рассчитываем размер кнопок на основе ширины экрана и количества кнопок в строке
            val buttonSize = ((screenWidth - (buttonsPerRow + 1) * convertDpToPixel(buttonMargin.toFloat(), this@MainActivity)) / buttonsPerRow).toInt()

            val firstDayOfMonth = LocalDate.of(currentDate.year, currentDate.month, 1)
            val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value

            addDayOfWeekHeaders(calendarGrid, buttonSize, buttonMargin)
            addEmptySpaces(calendarGrid, buttonSize, buttonMargin, firstDayOfWeek)
            addDayButtons(calendarGrid, buttonSize, buttonMargin)

            restoreButtonValues()
        }

        // Обновление TextView после восстановления значений кнопок
        binding.sumHourTextView.text = sumHour.toString()
        binding.sumMoneyTextView.text = sumMoney.toString()
    }


    private fun addDayOfWeekHeaders(grid: GridLayout, buttonSize: Int, buttonMargin: Int) {
        val daysOfWeek = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale("ru")) }
        for (dayOfWeek in daysOfWeek) {
            val dayTextView = TextView(this).apply {
                text = dayOfWeek
                setTextColor(Color.BLACK)
                textSize = 14f // размер текста в sp
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = buttonSize
                    height = buttonSize
                    setMargins(buttonMargin, buttonMargin, buttonMargin, buttonMargin)
                }
            }
            grid.addView(dayTextView)
        }
    }

    private fun addEmptySpaces(grid: GridLayout, buttonSize: Int, buttonMargin: Int, firstDayOfWeek: Int) {
        for (i in 1 until firstDayOfWeek) {
            val emptyView = Space(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = buttonSize
                    height = buttonSize
                    setMargins(buttonMargin, buttonMargin, buttonMargin, buttonMargin)
                }
            }
            grid.addView(emptyView)
        }
    }

    private fun addDayButtons(grid: GridLayout, buttonSize: Int, buttonMargin: Int) {
        val countDaysOfMonth = currentDate.lengthOfMonth()
        val displayedMonth = currentDate.monthValue - 1 // Преобразование от 1-12 к 0-11
        val displayedYear = currentDate.year
        for (dayOfMonth in 1..countDaysOfMonth) {
            val button = createDayButton(dayOfMonth, buttonSize, buttonMargin, displayedMonth, displayedYear)
            grid.addView(button)
        }
    }

    private fun changeMonth(amount: Int) {
        currentDate = currentDate.plusMonths(amount.toLong())

        // Загрузка кол-ва денег за 24 часа сдледующего месяца
        moneyPer24Hours = sharedPreferences.getInt("${currentDate.year}_${currentDate.monthValue}_money_per_24_hours", 4000)

        updateCalendar()
    }

    private fun restoreButtonValues() {
        with(binding) {
            val countDaysOfMonth = getCurrentMonthLength()

            for (dayOfMonth in 1..countDaysOfMonth) {
                val buttonTag = "button_$dayOfMonth"
                val savedOption = sharedPreferences.getString("${currentDate.year}_${currentDate.monthValue}_day_$dayOfMonth", null)
                val savedColor = sharedPreferences.getInt("${currentDate.year}_${currentDate.monthValue}_day_color_$dayOfMonth", Color.parseColor("#CCC5B9"))

                calendarGrid.findViewWithTag<Button>(buttonTag)?.let { button ->
                    if (savedOption != null) {
                        // Создаем два отдельных текстовых элемента для дня и сохраненной опции
                        val dayText = "$dayOfMonth"
                        val optionText = savedOption

                        // Определение размера шрифта на основе размера экрана
                        val displayMetrics = DisplayMetrics()
                        windowManager.defaultDisplay.getMetrics(displayMetrics)
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels
                        val buttonSize = minOf(screenWidth, screenHeight) / 45 // Например, размер шрифта 1/45 ширины экрана

                        val dayTextSize = buttonSize * 1.2f // Размер шрифта для числа дня
                        val optionTextSize = buttonSize * 0.8f // Размер шрифта для сохраненной опции

                        // Устанавливаем текст кнопки с учетом размера шрифта
                        button.text = SpannableStringBuilder().apply {
                            append(dayText)
                            append("\n")
                            append(optionText)
                            setSpan(AbsoluteSizeSpan(dayTextSize.toInt()), 0, dayText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            setSpan(AbsoluteSizeSpan(optionTextSize.toInt()), dayText.length, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }

                        button.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(savedColor)
                        }

                        button.setTextColor(
                            when (savedOption) {
                                "12н" -> Color.WHITE
                                "12д" -> Color.parseColor("#333333")
                                else -> Color.WHITE
                            }
                        )
                    } else {
                        button.text = dayOfMonth.toString()
                        button.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#d1d1d1"))
                        }
                        button.setTextColor(Color.parseColor("#333333"))
                    }
                }
            }
        }
    }


    private fun convertDpToPixel(dp: Float, context: Context): Float {
        return dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }


    private fun showOptionsDialog(dayOfMonth: Int, displayedMonth: Int, displayedYear: Int, button: Button) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_layout, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.option_1).setOnClickListener {
            updateButton(dayOfMonth, button, "12н", Color.parseColor("#161b33"))
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.option_2).setOnClickListener {
            updateButton(dayOfMonth, button, "12д", Color.parseColor("#ffc43d"))
            button.setTextColor(Color.parseColor("#333333"))
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.option_3).setOnClickListener {
            updateButton(dayOfMonth, button, "24", Color.parseColor("#023E8A"))
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.clear_button).setOnClickListener {
            clearButton(dayOfMonth, displayedMonth, displayedYear,  button)
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.cancel_button).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun clearButton(dayOfMonth: Int, displayedMonth: Int, displayedYear: Int, button: Button) {
        // Определение текущей даты
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        // Цвет и форматирование текста для текущего дня
        if  (dayOfMonth == currentDay && displayedMonth == currentMonth && displayedYear == currentYear) {
            button.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F27BBD")) // Цвет фона для текущего дня
            }
        } else {
            button.setTextColor(Color.parseColor("#333333")) // Цвет текста для остальных дней
            button.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#d1d1d1")) // Цвет фона для остальных дней
            }
        }

        // Установка текста кнопки
        button.text = dayOfMonth.toString()

        // Логирование сохраненной опции (если необходимо)
        val savedOption = sharedPreferences.getString("${currentDate.year}_${currentDate.monthValue}_day_$dayOfMonth", null)
        Log.d("MainActivity", "clearButton: savedOption = $savedOption for day $dayOfMonth")

        // Очистка данных из SharedPreferences
        clearDayInSharedPreferences(dayOfMonth)

        // Обновление суммы часов и денег
        updateSumHourAndMoney()
    }


    private fun createDayButton(dayOfMonth: Int, buttonSize: Int, buttonMargin: Int, displayedMonth: Int, displayedYear: Int): Button {
        val button = Button(this)
        button.text = dayOfMonth.toString()
        button.tag = "button_$dayOfMonth"

        // Определение текущей даты
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        // Цвет фона кнопки
        val backgroundColor = if (dayOfMonth == currentDay && displayedMonth == currentMonth && displayedYear == currentYear) {
            Color.parseColor("#F27BBD") // Цвет для текущего дня

        } else {
            Color.parseColor("#d1d1d1") // Цвет для остальных дней
        }

        Log.d("Debug", "Button background color: $backgroundColor")


        // Увеличение размера кнопки, если это текущий день
        if (dayOfMonth == currentDay && displayedMonth == currentMonth && displayedYear == currentYear) {
            button.scaleX = 1.1f
            button.scaleY = 1.1f
            button.textSize = 16f // размер текста в sp
            button.setTypeface(button.typeface, Typeface.BOLD)
        }

        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(backgroundColor)
        }

        button.setTextColor(Color.parseColor("#333333"))

        val buttonParams = GridLayout.LayoutParams().apply {
            width = buttonSize
            height = buttonSize
            setMargins(buttonMargin, buttonMargin, buttonMargin, buttonMargin)
        }
        button.layoutParams = buttonParams

        button.setOnClickListener {
            showOptionsDialog(dayOfMonth, displayedMonth, displayedYear,  button)
        }
        return button
    }


    private fun updateButton(dayOfMonth: Int, button: Button, text: String, color: Int) {
        val dayText = "$dayOfMonth"
        val optionText = text

        // Определение размера шрифта для числа дня и времени на основе размера кнопки
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val buttonSize = minOf(screenWidth, screenHeight) / 45 // Размер кнопки, например, 1/50 ширины экрана

        val dayTextSize = buttonSize * 1.2f // Размер шрифта для числа дня
        val optionTextSize = buttonSize * 0.8f // Размер шрифта для времени

        // Обновление текста кнопки с учетом размера шрифта
        button.text = SpannableStringBuilder().apply {
            append(dayText)
            append("\n")
            append(optionText)
            setSpan(AbsoluteSizeSpan(dayTextSize.toInt()), 0, dayText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(optionTextSize.toInt()), dayText.length, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Обновление цвета фона кнопки
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

        // Обновление цвета текста кнопки
        button.setTextColor(
            when (text) {
                "12н" -> Color.WHITE
                "12д" -> Color.parseColor("#333333")
                else -> Color.WHITE
            }
        )

        // Сохранение данных в SharedPreferences
        saveDayInSharedPreferences(dayOfMonth, text, color)

        // Обновление суммы часов и денег
        updateSumHourAndMoney()

        // Исправление размеров кнопки
        button.requestLayout()
    }



    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)

        val moneyPer24HoursEditText = dialogView.findViewById<EditText>(R.id.moneyPer24HoursEditText)
        val saveButton = dialogView.findViewById<Button>(R.id.saveButton)

        // Установка значения EditText в текущее значение moneyPer24Hours
        moneyPer24HoursEditText.setText(moneyPer24Hours.toString())

        // Создание диалогового окна
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        saveButton.setOnClickListener {
            // Получение нового значения из EditText и попытка преобразовать его в Int
            val newMoneyPer24Hours = moneyPer24HoursEditText.text.toString().toIntOrNull()

            // Проверка на успешное преобразование и сохранение нового значения
            if (newMoneyPer24Hours != null) {
                moneyPer24Hours = newMoneyPer24Hours

                with(sharedPreferences.edit()) {
                    putInt("${currentDate.year}_${currentDate.monthValue}_money_per_24_hours", moneyPer24Hours)
                    apply()
                }

                updateSumHourAndMoney()
            } else {
                // Если введенное значение неверно, показать сообщение об ошибке
                Toast.makeText(this, "Ошибка: Неверное значение", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        dialog.show()
    }


    private fun updateSumHourAndMoney() {
        var newSumHour = 0

        val countDaysOfMonth = getCurrentMonthLength()
        for (dayOfMonth in 1..countDaysOfMonth) {
            val savedOption = sharedPreferences.getString("${currentDate.year}_${currentDate.monthValue}_day_$dayOfMonth", null)
            if (savedOption != null) {
                when (savedOption) {
                    "12н", "12д" -> newSumHour += 12
                    "24" -> newSumHour += 24
                }
            }
        }

        val newSumMoney = (newSumHour / 24.0 * moneyPer24Hours).toInt()

        sumHour = newSumHour
        sumMoney = newSumMoney

        // Используем привязку для обновления TextView
        binding.sumHourTextView.text = newSumHour.toString()
        binding.sumMoneyTextView.text = newSumMoney.toString()

        // Обновляем данные в SharedPreferences
        with(sharedPreferences.edit()) {
            putInt("${currentDate.year}_${currentDate.monthValue}_sum_hour", newSumHour)
            putInt("${currentDate.year}_${currentDate.monthValue}_sum_money", newSumMoney)
            apply()
        }
    }


    private fun saveDayInSharedPreferences(dayOfMonth: Int, option: String, color: Int) {
        with(sharedPreferences.edit()) {
            putString("${currentDate.year}_${currentDate.monthValue}_day_$dayOfMonth", option)
            putInt("${currentDate.year}_${currentDate.monthValue}_day_color_$dayOfMonth", color)
            apply()
        }
    }

    private fun clearDayInSharedPreferences(dayOfMonth: Int) {
        with(sharedPreferences.edit()) {
            remove("${currentDate.year}_${currentDate.monthValue}_day_$dayOfMonth")
            remove("${currentDate.year}_${currentDate.monthValue}_day_color_$dayOfMonth")
            apply()
        }
    }

    private fun getCurrentMonthLength(): Int {
        return currentDate.lengthOfMonth()
    }

    private fun loadMonthlySums() {
        val gson = Gson()
        val json = sharedPreferences.getString("monthly_sums", null)
        if (json != null) {
            val type = object : TypeToken<MutableMap<String, Int>>() {}.type
            monthlySums = gson.fromJson(json, type)
        }
    }

    private fun saveMonthlySums() {
        val gson = Gson()
        val json = gson.toJson(monthlySums)
        with(sharedPreferences.edit()) {
            putString("monthly_sums", json)
            apply()
        }
    }


    override fun onPause() {
        super.onPause()
        saveMonthlySums()
    }
}
