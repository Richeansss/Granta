package com.example.granta

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Button
import android.widget.GridLayout
import android.widget.Space
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var dateTextView: TextView
    private lateinit var countMonthTextView: TextView
    private lateinit var sumHourTextView: TextView
    private lateinit var sumMoneyTextView: TextView

    private lateinit var buttonToImageRecognizer: Button
    private lateinit var sharedPreferences: SharedPreferences
    private var sumHour: Int = 0 // Общее количество часов
    private var sumMoney: Int = 0 // Общее количество часов


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dateTextView = findViewById(R.id.dateTextView)
        sumMoneyTextView = findViewById(R.id.sumMoneyTextView)
        sumHourTextView = findViewById(R.id.sumHourTextView)
        countMonthTextView = findViewById(R.id.countMonthTextView)

        buttonToImageRecognizer = findViewById(R.id.buttonToImageRecognizer)
        val calendarGrid: GridLayout = findViewById(R.id.calendarGrid)

        val currentDate = LocalDate.now()
        val countDaysOfMonth = currentDate.lengthOfMonth()

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        val buttonsPerRow = 7
        val buttonSize = (screenWidth / buttonsPerRow) - 28
        val buttonMargin = 8

        val firstDayOfMonth = LocalDate.of(currentDate.year, currentDate.month, 1)
        val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value // Первый день недели месяца (1 - понедельник, 7 - воскресенье)

        sharedPreferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)

        val daysOfWeek = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale("ru")) }
        for (dayOfWeek in daysOfWeek) {
            val dayTextView = TextView(this).apply {
                text = dayOfWeek
                setTextColor(Color.BLACK)
                textSize = 22f
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = buttonSize
                    height = buttonSize
                    setMargins(buttonMargin, buttonMargin, buttonMargin, buttonMargin)
                }
            }
            calendarGrid.addView(dayTextView)
        }

        for (i in 1 until firstDayOfWeek) {
            val emptyView = Space(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = buttonSize
                    height = buttonSize
                    setMargins(buttonMargin, buttonMargin, buttonMargin, buttonMargin)
                }
            }
            calendarGrid.addView(emptyView)
        }

        for (dayOfMonth in 1..countDaysOfMonth) {
            val button = Button(this).apply {
                text = dayOfMonth.toString()
                setTextColor(Color.WHITE)
                val params = GridLayout.LayoutParams()
                params.width = buttonSize
                params.height = buttonSize
                params.setMargins(buttonMargin, buttonMargin, buttonMargin, buttonMargin)
                layoutParams = params
                tag = "button_$dayOfMonth"

                val color = Color.GRAY

                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }

                setOnClickListener {
                    showOptionsDialog(dayOfMonth, this)
                }
            }
            calendarGrid.addView(button)
        }

        restoreButtonValues()

        val formattedDate = currentDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        countMonthTextView.text = "$countDaysOfMonth"

        buttonToImageRecognizer.setOnClickListener {
            val intent = Intent(this, ImageRecognizerActivity::class.java)
            startActivity(intent)
        }

        // Получение сохраненного значения общего количества часов
        sumHour = sharedPreferences.getInt("sum_hour", 0)
        sumMoney = sharedPreferences.getInt("sum_money", 0)

        // Отображение общего количества часов в sumHourTextView
        sumHourTextView.text = sumHour.toString()
        sumMoneyTextView.text = sumMoney.toString()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun restoreButtonValues() {
        val calendarGrid: GridLayout = findViewById(R.id.calendarGrid)
        val countDaysOfMonth = getCurrentMonthLength()

        for (dayOfMonth in 1..countDaysOfMonth) {
            val button = calendarGrid.findViewWithTag<Button>("button_$dayOfMonth")
            val savedOption = sharedPreferences.getString("day_$dayOfMonth", null)
            val savedColor = sharedPreferences.getInt("day_color_$dayOfMonth", Color.GRAY)

            if (button != null && savedOption != null) {
                button.text = savedOption
                button.setTextColor(Color.WHITE)
                button.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(savedColor)
                }
            }
        }
    }

    private fun showOptionsDialog(dayOfMonth: Int, button: Button) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_layout, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.option_1).setOnClickListener {
            updateButton(dayOfMonth, button, "12н", Color.RED)
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.option_2).setOnClickListener {
            updateButton(dayOfMonth, button, "12д", Color.GREEN)
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.option_3).setOnClickListener {
            updateButton(dayOfMonth, button, "24", Color.BLUE)
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.clear_button).setOnClickListener {
            clearButton(dayOfMonth, button)
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.cancel_button).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun clearButton(dayOfMonth: Int, button: Button) {
        button.text = dayOfMonth.toString()
        button.setTextColor(Color.WHITE)
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.GRAY)
        }

        // Логирование удаления
        Log.d("MainActivity", "clearButton: called for day $dayOfMonth")

        // Получение сохраненной опции перед удалением
        val savedOption = sharedPreferences.getString("day_$dayOfMonth", null)
        Log.d("MainActivity", "clearButton: savedOption = $savedOption for day $dayOfMonth") // Логирование считанного значения

        // Удаление опций после получения сохраненной опции
        clearSelectedOption(dayOfMonth)

        if (savedOption != null) {
            sumHour -= when (savedOption) {
                "12н", "12д" -> 12
                "24" -> 24
                else -> 0
            }

            sumMoney -= when (savedOption) {
                "12н", "12д" -> 2
                "24" -> 4
                else -> 0
            }

            // Сохранение обновленного значения часов и денег
            with(sharedPreferences.edit()) {
                putInt("sum_hour", sumHour)
                putInt("sum_money", sumMoney)
                apply()
            }

            // Обновление отображения общего количества часов
            sumHourTextView.text = sumHour.toString()
            sumMoneyTextView.text = sumMoney.toString()
        } else {
            Log.d("MainActivity", "clearButton: no savedOption found for day $dayOfMonth")
        }
    }


    private fun clearSelectedOption(dayOfMonth: Int) {
        with(sharedPreferences.edit()) {
            remove("day_$dayOfMonth")
            remove("day_color_$dayOfMonth")
            apply()
        }
    }

    private fun updateButton(dayOfMonth: Int, button: Button, text: String, color: Int) {
        button.text = text
        button.setTextColor(Color.WHITE)
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        saveSelectedOption(dayOfMonth, text, color)


        // Обновление общего количества часов
        sumHour += when (text) {
            "12н", "12д" -> 12
            "24" -> 24
            else -> 0
        }

        // Обновление общего количества денег
        sumMoney += when (text) {
            "12н", "12д" -> 2
            "24" -> 4
            else -> 0
        }

        // Сохранение общего количества часов
        with(sharedPreferences.edit()) {
            putInt("sum_hour", sumHour)
            putInt("sum_money", sumMoney)
            apply()
        }

        // Обновление отображения общего количества часов
        sumHourTextView.text = sumHour.toString()
        sumMoneyTextView.text = sumMoney.toString()
    }

    private fun saveSelectedOption(dayOfMonth: Int, selectedOption: String, selectedColor: Int) {
        with(sharedPreferences.edit()) {
            putString("day_$dayOfMonth", selectedOption)
            putInt("day_color_$dayOfMonth", selectedColor)
            apply()
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCurrentMonthLength(): Int {
        return LocalDate.now().lengthOfMonth()
    }
}
