package com.example.granta

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var sumHourTextView: TextView
    private lateinit var sumMoneyTextView: TextView

    private lateinit var buttonToImageRecognizer: Button
    private lateinit var buttonToSettings: Button
    private lateinit var buttonPrevMonth: Button
    private lateinit var buttonNextMonth: Button
    private lateinit var currentMonthTextView: TextView

    private lateinit var sharedPreferences: SharedPreferences
    private var sumHour: Int = 0
    private var sumMoney: Int = 0

    private var moneyPer24Hours: Int = 4000
    private val moneyPer12Hours get() = moneyPer24Hours / 2

    private var currentDate = LocalDate.now()

    private var monthlySums: MutableMap<String, Int> = mutableMapOf()

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

        buttonPrevMonth = findViewById(R.id.buttonPrevMonth)
        buttonNextMonth = findViewById(R.id.buttonNextMonth)
        currentMonthTextView = findViewById(R.id.currentMonthTextView)

        buttonPrevMonth.setOnClickListener {
            changeMonth(-1)
        }

        buttonNextMonth.setOnClickListener {
            changeMonth(1)
        }

        sharedPreferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        loadMonthlySums()

        // Load money per 24 hours from shared preferences
        moneyPer24Hours = sharedPreferences.getInt("${currentDate.year}_${currentDate.monthValue}_money_per_24_hours", 4000)

        buttonToImageRecognizer.setOnClickListener {
            val intent = Intent(
                this@MainActivity,
                ImageRecognizerActivity::class.java
            )
            startActivity(intent)
        }

        updateCalendar()
    }

    private fun updateCalendar() {
        currentMonthTextView.text = currentDate.month.getDisplayName(TextStyle.FULL, Locale("ru")) + " " + currentDate.year

        val calendarGrid: GridLayout = findViewById(R.id.calendarGrid)
        calendarGrid.removeAllViews()

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        val buttonsPerRow = 7
        val buttonSize = (screenWidth / buttonsPerRow) - 30
        val buttonMargin = 8

        val firstDayOfMonth = LocalDate.of(currentDate.year, currentDate.month, 1)
        val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value

        addDayOfWeekHeaders(calendarGrid, buttonSize, buttonMargin)
        addEmptySpaces(calendarGrid, buttonSize, buttonMargin, firstDayOfWeek)
        addDayButtons(calendarGrid, buttonSize, buttonMargin)

        restoreButtonValues()

        sumHour = sharedPreferences.getInt("${currentDate.year}_${currentDate.monthValue}_sum_hour", 0)
        sumMoney = sharedPreferences.getInt("${currentDate.year}_${currentDate.monthValue}_sum_money", 0)

        sumHourTextView.text = sumHour.toString()
        sumMoneyTextView.text = sumMoney.toString()
    }

    private fun addDayOfWeekHeaders(grid: GridLayout, buttonSize: Int, buttonMargin: Int) {
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
        for (dayOfMonth in 1..countDaysOfMonth) {
            val button = createDayButton(dayOfMonth, buttonSize, buttonMargin)
            grid.addView(button)
        }
    }


    private fun changeMonth(amount: Int) {
        currentDate = currentDate.plusMonths(amount.toLong())
        updateCalendar()
    }

    private fun restoreButtonValues() {
        val calendarGrid: GridLayout = findViewById(R.id.calendarGrid)
        val countDaysOfMonth = getCurrentMonthLength()

        for (dayOfMonth in 1..countDaysOfMonth) {
            val buttonTag = "button_$dayOfMonth"
            val savedOption = sharedPreferences.getString("${currentDate.year}_${currentDate.monthValue}_day_$dayOfMonth", null)
            val savedColor = sharedPreferences.getInt("${currentDate.year}_${currentDate.monthValue}_day_color_$dayOfMonth", Color.parseColor("#CCC5B9"))

            calendarGrid.findViewWithTag<Button>(buttonTag)?.let { button ->
                if (savedOption != null) {
                    button.text = savedOption
                    button.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(savedColor)
                    }

                    // Установка цвета текста в зависимости от значения savedOption
                    button.setTextColor(
                        when (savedOption) {
                            "12н" -> Color.WHITE
                            "12д" -> Color.parseColor("#333333")
                            else -> Color.WHITE
                        }
                    )
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

        val savedOption = sharedPreferences.getString("${currentDate.year}_${currentDate.monthValue}_day_$dayOfMonth", null)
        Log.d("MainActivity", "clearButton: savedOption = $savedOption for day $dayOfMonth")

        clearSelectedOption(dayOfMonth)

        if (savedOption != null) {
            val oldMoneyPer12Hours = sharedPreferences.getInt("${currentDate.year}_${currentDate.monthValue}_money_per_12_hours", moneyPer12Hours)
            val oldMoneyPer24Hours = sharedPreferences.getInt("${currentDate.year}_${currentDate.monthValue}_money_per_24_hours", moneyPer24Hours)

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

            with(sharedPreferences.edit()) {
                putInt("${currentDate.year}_${currentDate.monthValue}_sum_hour", sumHour)
                putInt("${currentDate.year}_${currentDate.monthValue}_sum_money", sumMoney)
                apply()
            }

            sumHourTextView.text = sumHour.toString()
            sumMoneyTextView.text = sumMoney.toString()
        } else {
            Log.d("MainActivity", "clearButton: no savedOption found for day $dayOfMonth")
        }
    }


    private fun clearSelectedOption(dayOfMonth: Int) {
        with(sharedPreferences.edit()) {
            remove("${currentDate.year}_${currentDate.monthValue}_day_$dayOfMonth")
            remove("${currentDate.year}_${currentDate.monthValue}_day_color_$dayOfMonth")
            apply()
        }
    }

    private fun updateButton(dayOfMonth: Int, button: Button, newOption: String, newColor: Int) {
        val currentOption = sharedPreferences.getString("${currentDate.year}_${currentDate.monthValue}_day_$dayOfMonth", null)
        Log.d("MainActivity", "updateButton: current option = $currentOption for day $dayOfMonth")

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

        sumHour += newHours - currentHours
        sumMoney += newMoney - currentMoney

        with(sharedPreferences.edit()) {
            putInt("${currentDate.year}_${currentDate.monthValue}_sum_hour", sumHour)
            putInt("${currentDate.year}_${currentDate.monthValue}_sum_money", sumMoney)
            apply()
        }

        sumHourTextView.text = sumHour.toString()
        sumMoneyTextView.text = sumMoney.toString()

        monthlySums["${currentDate.year}_${currentDate.monthValue}"] = sumMoney
        saveMonthlySums()
    }

    private fun saveSelectedOption(dayOfMonth: Int, selectedOption: String, selectedColor: Int) {
        with(sharedPreferences.edit()) {
            putString("${currentDate.year}_${currentDate.monthValue}_day_$dayOfMonth", selectedOption)
            putInt("${currentDate.year}_${currentDate.monthValue}_day_color_$dayOfMonth", selectedColor)
            apply()
        }
    }

    private fun getCurrentMonthLength(): Int {
        return currentDate.lengthOfMonth()
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

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val moneyPer24HoursEditText = dialogView.findViewById<EditText>(R.id.moneyPer24HoursEditText)
        val saveButton = dialogView.findViewById<Button>(R.id.saveButton)

        moneyPer24HoursEditText.setText(moneyPer24Hours.toString())

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        saveButton.setOnClickListener {
            try {
                val newMoneyPer24Hours = moneyPer24HoursEditText.text.toString().toInt()
                moneyPer24Hours = newMoneyPer24Hours

                with(sharedPreferences.edit()) {
                    putInt("${currentDate.year}_${currentDate.monthValue}_money_per_24_hours", newMoneyPer24Hours)
                    putInt("${currentDate.year}_${currentDate.monthValue}_money_per_12_hours", newMoneyPer24Hours / 2)
                    apply()
                }

                recalculateSumMoney()

                dialog.dismiss()
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Введите корректное значение", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun recalculateSumMoney() {
        sumMoney = 0
        sumHour = 0

        val currentMoneyPer12Hours = moneyPer12Hours
        val currentMoneyPer24Hours = moneyPer24Hours

        for (dayOfMonth in 1..getCurrentMonthLength()) {
            val savedOption = sharedPreferences.getString("${currentDate.year}_${currentDate.monthValue}_day_$dayOfMonth", null)
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

        with(sharedPreferences.edit()) {
            putInt("${currentDate.year}_${currentDate.monthValue}_sum_money", sumMoney)
            putInt("${currentDate.year}_${currentDate.monthValue}_sum_hour", sumHour)
            apply()
        }

        sumMoneyTextView.text = sumMoney.toString()
        sumHourTextView.text = sumHour.toString()

        monthlySums["${currentDate.year}_${currentDate.monthValue}"] = sumMoney
        saveMonthlySums()
    }

    private fun loadMonthlySums() {
        val json = sharedPreferences.getString("monthly_sums", null)
        if (json != null) {
            val type = object : TypeToken<MutableMap<String, Int>>() {}.type
            monthlySums = Gson().fromJson(json, type)
        }
    }

    private fun saveMonthlySums() {
        val json = Gson().toJson(monthlySums)
        with(sharedPreferences.edit()) {
            putString("monthly_sums", json)
            apply()
        }
    }
}
