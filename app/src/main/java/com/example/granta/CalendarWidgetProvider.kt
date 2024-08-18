package com.example.granta

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import java.time.LocalDate

class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "my_preferences"

        private fun getSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)
            val sharedPreferences = getSharedPreferences(context)
            val currentDate = LocalDate.now()

            val datesToShow = listOf(
                currentDate.minusDays(2),
                currentDate.minusDays(1),
                currentDate,
                currentDate.plusDays(1),
                currentDate.plusDays(2)
            )

            val ids = listOf(R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5)

            for ((index, date) in datesToShow.withIndex()) {
                val savedOption = sharedPreferences.getString("${date.year}_${date.monthValue}_day_${date.dayOfMonth}", null)
                Log.d("CalendarWidgetProvider", "Option for ${date}: $savedOption")

                val savedColor = sharedPreferences.getInt("${date.year}_${date.monthValue}_day_color_${date.dayOfMonth}", Color.parseColor("#CCC5B9"))
                Log.d("CalendarWidgetProvider", "Color for ${date}: $savedColor")

                val text = if (savedOption != null) {
                    "${date.dayOfMonth} | $savedOption"
                } else {
                    date.dayOfMonth.toString()
                }

                Log.d("CalendarWidgetProvider", "Option for ${date}: $savedOption")

                views.setTextViewText(ids[index], text)
                views.setTextColor(ids[index], if (savedOption == "12н") Color.WHITE else Color.BLACK)

                // Устанавливаем фоновый ресурс в зависимости от текущей даты
                val backgroundResource = if (date == currentDate) R.drawable.current_day_circle_shape else R.drawable.circle_shape
                views.setInt(ids[index], "setBackgroundResource", backgroundResource)

                // Установка цвета фона - временно уберите это, чтобы проверить
                // views.setInt(ids[index], "setBackgroundColor", savedColor)
            }

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widgetLayout, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
