package com.example.habittracker

enum class HistoryCellState {
    NOT_CREATED,
    MISSED,
    COMPLETED
}

data class MonthHistoryDay(
    val day: Long,
    val state: HistoryCellState
)

data class HabitUiModel(
    val id: Long,
    val name: String,
    val color: Int,
    val createdDay: Long,
    val remindersEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    var doneToday: Boolean,
    val recentDays: List<HistoryCellState>,
    val weekCompletedCount: Int,
    val weekDaysTracked: Int,
    val weekPercent: Int,
    val monthHistory: List<MonthHistoryDay>,
    val monthCompletedCount: Int,
    val monthDaysTracked: Int,
    val monthPercent: Int
)
