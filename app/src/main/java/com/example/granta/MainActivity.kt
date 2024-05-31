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
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var sumHourTextView: TextView
    private lateinit var sumMoneyTextView: TextView

    private lateinit var buttonToImageRecognizer: Button
    private lateinit var buttonToSettings: Button

    private lateinit var sharedPreferences: SharedPreferences
    private var sumHour: Int = 0 // Общее количество часов
    private var sumMoney: Int = 0 // Общее количество денег

    private var moneyPer24Hours: Int = 4000 // Деньги за 24 часа
    private val moneyPer12Hours get() = moneyPer24Hours / 2 // Деньги за 12 часов

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttonToSettings = findViewById(R.id.buttonToSettings)
        buttonToSettings.setOnClickListener {
            showSettingsDialog()
        }

        sumMoneyTextView = findViewById(R.id.sumMoneyTextView)
        sumHourTextView = findViewById(R.id.sumHourTextView)

        buttonToImageRecognizer = findViewById(R.id.buttonToImageRecognizer)
        val calendarGrid: GridLayout = findViewById(R.id.calendarGrid)

        val currentDate = LocalDate.now()
        val countDaysOfMonth = currentDate.lengthOfMonth()

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        val buttonsPerRow = 7
        val buttonSize = (screenWidth / buttonsPerRow) - 30
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
            val button = createDayButton(dayOfMonth, buttonSize, buttonMargin)
            calendarGrid.addView(button)
        }

        restoreButtonValues()

        buttonToImageRecognizer.setOnClickListener {
            val intent = Intent(this, ImageRecognizerActivity::class.java)
            startActivity(intent)
        }

        // Получение сохраненного значения общего количества часов и денег
        sumHour = sharedPreferences.getInt("sum_hour", 0)
        sumMoney = sharedPreferences.getInt("sum_money", 0)

        // Отображение общего количества часов и денег
        sumHourTextView.text = sumHour.toString()
        sumMoneyTextView.text = sumMoney.toString()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun restoreButtonValues() {
        val calendarGrid: GridLayout = findViewById(R.id.calendarGrid)
        val countDaysOfMonth = getCurrentMonthLength()

        for (dayOfMonth in 1..countDaysOfMonth) {
            val buttonTag = "button_$dayOfMonth"
            val savedOption = sharedPreferences.getString("day_$dayOfMonth", null)
            val savedColor = sharedPreferences.getInt("day_color_$dayOfMonth", Color.parseColor("#CCC5B9"))

            calendarGrid.findViewWithTag<Button>(buttonTag)?.let { button ->
                if (savedOption != null) {
                    button.text = savedOption
                    button.setTextColor(Color.WHITE)
                    button.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(savedColor)
                    }
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
        button.setTextColor(Color.parseColor("#333333"))
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#d1d1d1"))
        }

        // Получение сохраненной опции перед удалением
        val savedOption = sharedPreferences.getString("day_$dayOfMonth", null)
        Log.d("MainActivity", "clearButton: savedOption = $savedOption for day $dayOfMonth") // Логирование считанного значения

        // Удаление опций после получения сохраненной опции
        clearSelectedOption(dayOfMonth)

        if (savedOption != null) {
            val oldMoneyPer12Hours = sharedPreferences.getInt("money_per_12_hours", moneyPer12Hours)
            val oldMoneyPer24Hours = sharedPreferences.getInt("money_per_24_hours", moneyPer24Hours)

            sumHour -= when (savedOption) {
                "12н", "12д" -> 12
                "24" -> 24
                else -> 0
            }

            sumMoney -= when (savedOption) {
                "12н", "12д" -> oldMoneyPer12Hours
                "24" -> oldMoneyPer24Hours
                else -> 0
            }

            // Сохранение обновленного значения часов и денег
            with(sharedPreferences.edit()) {
                putInt("sum_hour", sumHour)
                putInt("sum_money", sumMoney)
                apply()
            }

            // Обновление отображения общего количества часов и денег
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

    private fun updateButton(dayOfMonth: Int, button: Button, newOption: String, newColor: Int) {
        // Получение текущей сохраненной опции
        val currentOption = sharedPreferences.getString("day_$dayOfMonth", null)

        // Логирование текущей сохраненной опции
        Log.d("MainActivity", "updateButton: current option = $currentOption for day $dayOfMonth")

        // Обновление кнопки
        button.text = newOption
        button.setTextColor(Color.WHITE)
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(newColor)
        }
        saveSelectedOption(dayOfMonth, newOption, newColor)

        val currentMoneyPer12Hours = moneyPer12Hours
        val currentMoneyPer24Hours = moneyPer24Hours

        val currentHours = when (currentOption) {
            "12н", "12д" -> 12
            "24" -> 24
            else -> 0
        }

        val newHours = when (newOption) {
            "12н", "12д" -> 12
            "24" -> 24
            else -> 0
        }

        val currentMoney = when (currentOption) {
            "12н", "12д" -> currentMoneyPer12Hours
            "24" -> currentMoneyPer24Hours
            else -> 0
        }

        val newMoney = when (newOption) {
            "12н", "12д" -> currentMoneyPer12Hours
            "24" -> currentMoneyPer24Hours
            else -> 0
        }

        // Обновление общего количества часов и денег
        sumHour += newHours - currentHours
        sumMoney += newMoney - currentMoney

        // Сохранение обновленного значения часов и денег
        with(sharedPreferences.edit()) {
            putInt("sum_hour", sumHour)
            putInt("sum_money", sumMoney)
            apply()
        }

        // Обновление отображения общего количества часов и денег
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

    private fun createDayButton(dayOfMonth: Int, buttonSize: Int, buttonMargin: Int): Button {
        return Button(this).apply {
            text = dayOfMonth.toString()
            setTextColor(Color.parseColor("#333333"))
            layoutParams = GridLayout.LayoutParams().apply {
                width = buttonSize
                height = buttonSize
                setMargins(buttonMargin, buttonMargin, buttonMargin, buttonMargin)
            }
            tag = "button_$dayOfMonth"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#d1d1d1"))
            }
            setOnClickListener {
                showOptionsDialog(dayOfMonth, this)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val moneyPer24HoursEditText = dialogView.findViewById<EditText>(R.id.moneyPer24HoursEditText)
        val saveButton = dialogView.findViewById<Button>(R.id.saveButton)

        // Получение текущего значения moneyPer24Hours
        val currentMoneyPer24Hours = sharedPreferences.getInt("money_per_24_hours", 4000)
        moneyPer24HoursEditText.setText(currentMoneyPer24Hours.toString())

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        saveButton.setOnClickListener {
            val newMoneyPer24Hours = moneyPer24HoursEditText.text.toString().toInt()

            // Обновить переменную moneyPer24Hours
            moneyPer24Hours = newMoneyPer24Hours

            // Сохранение новых значений денег за 12 и 24 часа
            with(sharedPreferences.edit()) {
                putInt("money_per_24_hours", newMoneyPer24Hours)
                putInt("money_per_12_hours", newMoneyPer24Hours / 2)
                apply()
            }

            // Пересчитать сумму денег на основе нового значения moneyPer24Hours
            recalculateSumMoney()

            dialog.dismiss()
        }

        dialog.show()
    }



    @RequiresApi(Build.VERSION_CODES.O)
    private fun recalculateSumMoney() {
        sumMoney = 0
        sumHour = 0

        val currentMoneyPer12Hours = moneyPer12Hours
        val currentMoneyPer24Hours = moneyPer24Hours

        for (dayOfMonth in 1..getCurrentMonthLength()) {
            val savedOption = sharedPreferences.getString("day_$dayOfMonth", null)
            sumMoney += when (savedOption) {
                "12н", "12д" -> currentMoneyPer12Hours
                "24" -> currentMoneyPer24Hours
                else -> 0
            }
            sumHour += when (savedOption) {
                "12н", "12д" -> 12
                "24" -> 24
                else -> 0
            }
        }

        // Сохранение обновленного значения часов и денег
        with(sharedPreferences.edit()) {
            putInt("sum_money", sumMoney)
            putInt("sum_hour", sumHour)
            apply()
        }

        // Обновление отображения общего количества часов и денег
        sumMoneyTextView.text = sumMoney.toString()
        sumHourTextView.text = sumHour.toString()
    }
}
