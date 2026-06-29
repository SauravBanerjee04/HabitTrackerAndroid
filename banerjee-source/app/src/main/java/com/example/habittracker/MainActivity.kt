package com.example.habittracker

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import android.os.Build
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    companion object {
        private const val seedPreferencesName = "habit_tracker_prefs"
        private const val sampleHabitsSeededKey = "sample_habits_seeded"
        private const val sampleHistorySeededKey = "sample_history_seeded"
        private const val historyWindowSize = 3L
        private const val historyLookbackDays = 30L
    }

    private data class HabitScreenData(
        val habits: List<HabitUiModel>,
        val earliestAvailableDay: Long,
        val visibleHistoryDayCount: Int
    )

    private data class HabitColorOption(
        val radioButtonId: Int,
        val colorResId: Int
    )

    private data class ReminderSelection(
        var enabled: Boolean,
        var hour: Int,
        var minute: Int
    )

    private val habits: MutableList<HabitUiModel> = mutableListOf()
    private val databaseExecutor = Executors.newSingleThreadExecutor()
    private lateinit var habitAdapter: HabitAdapter
    private lateinit var database: HabitDatabase
    private lateinit var emptyStateText: TextView
    private lateinit var trackerHeaderRow: LinearLayout
    private lateinit var historyBackButton: TextView
    private lateinit var historyForwardButton: TextView
    private var historyWindowEndDay: Long = 0L
    private var earliestAvailableDay: Long = 0L
    private var visibleHistoryDayCount: Int = historyWindowSize.toInt()
    private val notificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.notifications_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = HabitDatabase.getInstance(this)
        HabitReminderScheduler.createNotificationChannel(this)
        historyWindowEndDay = getTodayEpochDay()
        earliestAvailableDay = historyWindowEndDay
        emptyStateText = findViewById(R.id.emptyStateText)
        trackerHeaderRow = findViewById(R.id.trackerHeaderRow)
        historyBackButton = findViewById(R.id.historyBackButton)
        historyForwardButton = findViewById(R.id.historyForwardButton)
        val habitRecyclerView: RecyclerView = findViewById(R.id.habitRecyclerView)
        val addHabitButton: MaterialButton = findViewById(R.id.addHabitButton)
        val tipsButton: MaterialButton = findViewById(R.id.tipsButton)
        val emailProgressButton: MaterialButton = findViewById(R.id.emailProgressButton)

        habitAdapter = HabitAdapter(
            habits = habits,
            onHabitCheckedChanged = { position, isChecked ->
                saveHabitCompletion(habits[position], isChecked)
            },
            onHabitDeleteClicked = { position ->
                deleteHabit(habits[position])
            },
            onHabitEditClicked = { position ->
                showEditHabitDialog(habits[position])
            },
            onHistoryShiftRequested = { dayShift ->
                shiftHistoryWindow(dayShift)
            }
        )

        habitRecyclerView.layoutManager = LinearLayoutManager(this)
        habitRecyclerView.adapter = habitAdapter

        addHabitButton.setOnClickListener {
            showAddHabitDialog()
        }
        tipsButton.setOnClickListener {
            startActivity(Intent(this, TipsActivity::class.java))
        }
        emailProgressButton.setOnClickListener {
            exportProgressToGmail()
        }

        historyBackButton.setOnClickListener {
            shiftHistoryWindow(-1)
        }
        historyForwardButton.setOnClickListener {
            shiftHistoryWindow(1)
        }

        val headerGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onFling(
                event1: MotionEvent?,
                event2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val deltaX = event2.x - (event1?.x ?: 0f)
                val deltaY = event2.y - (event1?.y ?: 0f)
                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 80f && abs(velocityX) > 100f) {
                    if (deltaX < 0) {
                        shiftHistoryWindow(-1)
                    } else {
                        shiftHistoryWindow(1)
                    }
                    return true
                }
                return false
            }
        })
        trackerHeaderRow.setOnTouchListener { _, motionEvent ->
            headerGestureDetector.onTouchEvent(motionEvent)
        }

        setupHeaderLabels()
        updateEmptyState()
        seedSampleDataIfNeeded()
    }

    override fun onDestroy() {
        super.onDestroy()
        databaseExecutor.shutdown()
    }

    private fun showAddHabitDialog() {
        showHabitDialog(
            titleResId = R.string.add_habit_title,
            positiveButtonResId = R.string.save,
            initialName = "",
            initialColor = getNextHabitColor(),
            initialRemindersEnabled = false,
            initialReminderHour = 20,
            initialReminderMinute = 0,
            onSave = { habitName, color, reminderSelection, onSaved ->
                saveHabit(habitName, color, reminderSelection, onSaved)
            }
        )
    }

    private fun showEditHabitDialog(habit: HabitUiModel) {
        showHabitDialog(
            titleResId = R.string.edit_habit_title,
            positiveButtonResId = R.string.update,
            initialName = habit.name,
            initialColor = habit.color,
            initialRemindersEnabled = habit.remindersEnabled,
            initialReminderHour = habit.reminderHour,
            initialReminderMinute = habit.reminderMinute,
            onSave = { habitName, color, reminderSelection, onSaved ->
                updateHabit(habit.id, habitName, color, reminderSelection, onSaved)
            }
        )
    }

    private fun showHabitDialog(
        titleResId: Int,
        positiveButtonResId: Int,
        initialName: String,
        initialColor: Int,
        initialRemindersEnabled: Boolean,
        initialReminderHour: Int,
        initialReminderMinute: Int,
        onSave: (habitName: String, color: Int, reminderSelection: ReminderSelection, onSaved: () -> Unit) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_habit, null)
        val habitNameInput: TextInputEditText = dialogView.findViewById(R.id.habitNameInput)
        val colorRadioGroup: RadioGroup = dialogView.findViewById(R.id.colorRadioGroup)
        val reminderSwitch: MaterialSwitch = dialogView.findViewById(R.id.reminderSwitch)
        val reminderTimeButton: MaterialButton = dialogView.findViewById(R.id.reminderTimeButton)
        val colorOptions = getHabitColorOptions()
        val reminderSelection = ReminderSelection(
            enabled = initialRemindersEnabled,
            hour = initialReminderHour,
            minute = initialReminderMinute
        )
        habitNameInput.setText(initialName)
        colorRadioGroup.check(getRadioButtonIdForColor(initialColor, colorOptions))
        reminderSwitch.isChecked = reminderSelection.enabled
        reminderTimeButton.text = getReminderTimeLabel(reminderSelection.hour, reminderSelection.minute)
        reminderTimeButton.visibility = if (reminderSelection.enabled) View.VISIBLE else View.GONE

        reminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            reminderSelection.enabled = isChecked
            reminderTimeButton.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        reminderTimeButton.setOnClickListener {
            showReminderTimePickerDialog(reminderSelection) { hourOfDay, minute ->
                reminderSelection.hour = hourOfDay
                reminderSelection.minute = minute
                reminderTimeButton.text = getReminderTimeLabel(hourOfDay, minute)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(titleResId)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(positiveButtonResId, null)
            .show()
            .also { dialog ->
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val habitName = habitNameInput.text?.toString()?.trim().orEmpty()
                    if (habitName.isBlank()) {
                        Toast.makeText(this, R.string.habit_name_required, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val selectedColor = getSelectedColor(colorRadioGroup.checkedRadioButtonId, colorOptions)
                    if (reminderSelection.enabled) {
                        ensureNotificationPermissionIfNeeded()
                    }
                    onSave(habitName, selectedColor, reminderSelection) {
                        dialog.dismiss()
                    }
                }
            }
    }

    private fun getNextHabitColor(): Int {
        val colors = listOf(
            R.color.habit_red,
            R.color.habit_yellow,
            R.color.habit_green,
            R.color.habit_blue,
            R.color.habit_orange
        )
        val nextColorRes = colors[habits.size % colors.size]
        return ContextCompat.getColor(this, nextColorRes)
    }

    private fun saveHabit(habitName: String, color: Int, reminderSelection: ReminderSelection, onSaved: () -> Unit) {
        databaseExecutor.execute {
            try {
                val habitEntity = HabitEntity().apply {
                    name = habitName
                    this.color = color
                    createdAt = System.currentTimeMillis()
                    remindersEnabled = reminderSelection.enabled
                    reminderHour = reminderSelection.hour
                    reminderMinute = reminderSelection.minute
                }
                val habitId = database.habitDao().insertHabit(habitEntity)
                habitEntity.id = habitId
                if (habitEntity.remindersEnabled) {
                    HabitReminderScheduler.scheduleHabitReminder(this, habitEntity, allowSameDayCatchUp = true)
                }
                val screenData = loadHabitsFromDatabase()
                runOnUiThread {
                    applyLoadedData(screenData)
                    onSaved()
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.save_habit_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateHabit(
        habitId: Long,
        habitName: String,
        color: Int,
        reminderSelection: ReminderSelection,
        onSaved: () -> Unit
    ) {
        databaseExecutor.execute {
            try {
                database.habitDao().updateHabitDetails(
                    habitId,
                    habitName,
                    color,
                    reminderSelection.enabled,
                    reminderSelection.hour,
                    reminderSelection.minute
                )
                val updatedHabit = database.habitDao().getHabitById(habitId)
                if (updatedHabit != null && updatedHabit.remindersEnabled) {
                    HabitReminderScheduler.scheduleHabitReminder(this, updatedHabit, allowSameDayCatchUp = true)
                } else {
                    HabitReminderScheduler.cancelHabitReminder(this, habitId)
                }
                val screenData = loadHabitsFromDatabase()
                runOnUiThread {
                    applyLoadedData(screenData)
                    onSaved()
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.update_habit_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveHabitCompletion(habit: HabitUiModel, isChecked: Boolean) {
        databaseExecutor.execute {
            try {
                val today = getTodayEpochDay()
                if (isChecked) {
                    val habitEntry = HabitEntryEntity().apply {
                        habitId = habit.id
                        day = today
                    }
                    database.habitDao().upsertEntry(habitEntry)
                } else {
                    database.habitDao().deleteEntryForDay(habit.id, today)
                }

                val screenData = loadHabitsFromDatabase()
                runOnUiThread {
                    applyLoadedData(screenData)
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.update_habit_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteHabit(habit: HabitUiModel) {
        databaseExecutor.execute {
            try {
                HabitReminderScheduler.cancelHabitReminder(this, habit.id)
                database.habitDao().deleteHabit(habit.id)
                val screenData = loadHabitsFromDatabase()
                runOnUiThread {
                    applyLoadedData(screenData)
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.delete_habit_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadHabits() {
        databaseExecutor.execute {
            try {
                val screenData = loadHabitsFromDatabase()
                runOnUiThread {
                    applyLoadedData(screenData)
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.load_habits_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun seedSampleDataIfNeeded() {
        val preferences = getSharedPreferences(seedPreferencesName, MODE_PRIVATE)
        databaseExecutor.execute {
            try {
                if (!preferences.getBoolean(sampleHabitsSeededKey, false)) {
                    val existingHabitNames = database.habitDao()
                        .getAllHabits()
                        .map { it.name.trim().lowercase() }
                        .toSet()

                    if (!existingHabitNames.contains("play guitar")) {
                        insertSampleHabit("Play Guitar", ContextCompat.getColor(this, R.color.habit_blue))
                    }
                    if (!existingHabitNames.contains("running")) {
                        insertSampleHabit("Running", ContextCompat.getColor(this, R.color.habit_green))
                    }

                    preferences.edit().putBoolean(sampleHabitsSeededKey, true).apply()
                }

                if (!preferences.getBoolean(sampleHistorySeededKey, false)) {
                    seedSampleHistory()
                    preferences.edit().putBoolean(sampleHistorySeededKey, true).apply()
                }

                ensureSampleHabitsLookOldEnoughForDemo()

                val screenData = loadHabitsFromDatabase()
                runOnUiThread {
                    applyLoadedData(screenData)
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.load_habits_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun seedSampleHistory() {
        val habitsByName = database.habitDao()
            .getAllHabits()
            .associateBy { it.name.trim().lowercase() }

        val today = getTodayEpochDay()

        habitsByName["play guitar"]?.let { habit ->
            seedHistoryForHabitIfEmpty(
                habitId = habit.id,
                days = listOf(today - 4, today - 2, today)
            )
        }

        habitsByName["running"]?.let { habit ->
            seedHistoryForHabitIfEmpty(
                habitId = habit.id,
                days = listOf(today - 5, today - 3, today - 1)
            )
        }
    }

    private fun seedHistoryForHabitIfEmpty(habitId: Long, days: List<Long>) {
        val existingEntries = database.habitDao().getEntriesForHabitFromDay(habitId, getTodayEpochDay() - 29)
        if (existingEntries.isNotEmpty()) {
            return
        }

        days.forEach { day ->
            val habitEntry = HabitEntryEntity().apply {
                this.habitId = habitId
                this.day = day
            }
            database.habitDao().upsertEntry(habitEntry)
        }
    }

    private fun ensureSampleHabitsLookOldEnoughForDemo() {
        val habitsByName = database.habitDao()
            .getAllHabits()
            .associateBy { it.name.trim().lowercase() }
        val today = getTodayEpochDay()

        habitsByName["play guitar"]?.let { habit ->
            ensureDemoHistoryForHabit(
                habitId = habit.id,
                targetCreatedDay = today - 12,
                days = listOf(today - 12, today - 9, today - 6, today - 4, today - 2, today)
            )
        }

        habitsByName["running"]?.let { habit ->
            ensureDemoHistoryForHabit(
                habitId = habit.id,
                targetCreatedDay = today - 11,
                days = listOf(today - 11, today - 8, today - 5, today - 3, today - 1)
            )
        }
    }

    private fun ensureDemoHistoryForHabit(habitId: Long, targetCreatedDay: Long, days: List<Long>) {
        val habit = database.habitDao().getAllHabits().firstOrNull { it.id == habitId } ?: return
        val currentCreatedDay = createdAtMillisToEpochDay(habit.createdAt)
        if (currentCreatedDay > targetCreatedDay) {
            database.habitDao().updateHabitCreatedAt(habitId, epochDayToStartOfDayMillis(targetCreatedDay))
        }

        val existingDays = database.habitDao()
            .getEntriesForHabitFromDay(habitId, getTodayEpochDay() - (historyLookbackDays - 1))
            .map { it.day }
            .toSet()

        days.forEach { day ->
            if (!existingDays.contains(day)) {
                val habitEntry = HabitEntryEntity().apply {
                    this.habitId = habitId
                    this.day = day
                }
                database.habitDao().upsertEntry(habitEntry)
            }
        }
    }

    private fun insertSampleHabit(habitName: String, color: Int) {
        val habitEntity = HabitEntity().apply {
            name = habitName
            this.color = color
            createdAt = System.currentTimeMillis()
        }
        database.habitDao().insertHabit(habitEntity)
    }

    private fun loadHabitsFromDatabase(): HabitScreenData {
        val today = getTodayEpochDay()
        val lookbackStartDay = today - (historyLookbackDays - 1)
        val habitEntities = database.habitDao().getAllHabits()
        val completedEntries = database.habitDao().getEntriesFromDay(lookbackStartDay)
        val completedDaysByHabitId = completedEntries
            .groupBy { it.habitId }
            .mapValues { entry -> entry.value.map { it.day }.toSet() }
        val createdDayByHabitId = habitEntities.associate { habitEntity ->
            habitEntity.id to createdAtMillisToEpochDay(habitEntity.createdAt)
        }
        val earliestHabitDay = createdDayByHabitId.values.minOrNull()
        val earliestCompletedDay = completedEntries.minOfOrNull { it.day }
        val earliestKnownDay = listOfNotNull(earliestHabitDay, earliestCompletedDay)
            .minOrNull()
            ?.coerceAtLeast(lookbackStartDay)
            ?: today
        val totalAvailableHistoryDays = ((today - earliestKnownDay) + 1).toInt().coerceAtLeast(1)
        val availableVisibleDays = totalAvailableHistoryDays.coerceAtMost(historyWindowSize.toInt())
        val clampedHistoryWindowEndDay = historyWindowEndDay.coerceIn(
            earliestKnownDay + (availableVisibleDays - 1),
            today
        )
        val historyStartDay = clampedHistoryWindowEndDay - (availableVisibleDays - 1)

        val habitUiModels = habitEntities.map { habitEntity ->
            val createdDay = createdDayByHabitId[habitEntity.id] ?: today
            val completedDays = completedDaysByHabitId[habitEntity.id].orEmpty()
            val recentDays = (historyStartDay..clampedHistoryWindowEndDay).toList()
            val weekWindowStart = maxOf(createdDay, today - 6)
            val monthWindowStart = maxOf(createdDay, today - 29)
            val fullMonthWindowStart = today - 29
            val weekDaysTracked = (weekWindowStart..today).count()
            val monthDaysTracked = (monthWindowStart..today).count()
            val weekCompletedCount = (weekWindowStart..today).count { day -> completedDays.contains(day) }
            val monthCompletedCount = (monthWindowStart..today).count { day -> completedDays.contains(day) }
            val monthHistory = (fullMonthWindowStart..today).map { day ->
                MonthHistoryDay(
                    day = day,
                    state = when {
                        day < createdDay -> HistoryCellState.NOT_CREATED
                        completedDays.contains(day) -> HistoryCellState.COMPLETED
                        else -> HistoryCellState.MISSED
                    }
                )
            }
            HabitUiModel(
                id = habitEntity.id,
                name = habitEntity.name,
                color = habitEntity.color,
                createdDay = createdDay,
                remindersEnabled = habitEntity.remindersEnabled,
                reminderHour = habitEntity.reminderHour,
                reminderMinute = habitEntity.reminderMinute,
                doneToday = completedDays.contains(today),
                recentDays = recentDays.map { day ->
                    when {
                        day < createdDay -> HistoryCellState.NOT_CREATED
                        completedDays.contains(day) -> HistoryCellState.COMPLETED
                        else -> HistoryCellState.MISSED
                    }
                },
                weekCompletedCount = weekCompletedCount,
                weekDaysTracked = weekDaysTracked,
                weekPercent = if (weekDaysTracked == 0) 0 else (weekCompletedCount * 100) / weekDaysTracked,
                monthHistory = monthHistory,
                monthCompletedCount = monthCompletedCount,
                monthDaysTracked = monthDaysTracked,
                monthPercent = if (monthDaysTracked == 0) 0 else (monthCompletedCount * 100) / monthDaysTracked
            )
        }

        return HabitScreenData(
            habits = habitUiModels,
            earliestAvailableDay = earliestKnownDay,
            visibleHistoryDayCount = availableVisibleDays
        )
    }

    private fun getTodayEpochDay(): Long = LocalDate.now().toEpochDay()

    private fun createdAtMillisToEpochDay(createdAtMillis: Long): Long {
        return Instant.ofEpochMilli(createdAtMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
    }

    private fun epochDayToStartOfDayMillis(epochDay: Long): Long {
        return LocalDate.ofEpochDay(epochDay)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun getHabitColorOptions(): List<HabitColorOption> {
        return listOf(
            HabitColorOption(R.id.redColorRadio, R.color.habit_red),
            HabitColorOption(R.id.yellowColorRadio, R.color.habit_yellow),
            HabitColorOption(R.id.greenColorRadio, R.color.habit_green),
            HabitColorOption(R.id.blueColorRadio, R.color.habit_blue),
            HabitColorOption(R.id.orangeColorRadio, R.color.habit_orange)
        )
    }

    private fun getSelectedColor(checkedRadioButtonId: Int, colorOptions: List<HabitColorOption>): Int {
        val selectedOption = colorOptions.firstOrNull { it.radioButtonId == checkedRadioButtonId }
            ?: colorOptions.first()
        return ContextCompat.getColor(this, selectedOption.colorResId)
    }

    private fun getReminderTimeLabel(hour: Int, minute: Int): String {
        val formattedTime = LocalTime.of(hour, minute)
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
        return getString(R.string.reminder_time_format, formattedTime)
    }

    private fun showReminderTimePickerDialog(
        reminderSelection: ReminderSelection,
        onTimeSelected: (hour: Int, minute: Int) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reminder_time, null)
        val timePicker: TimePicker = dialogView.findViewById(R.id.reminderTimePicker)
        timePicker.setIs24HourView(false)
        timePicker.hour = reminderSelection.hour
        timePicker.minute = reminderSelection.minute

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_reminder_time)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                onTimeSelected(timePicker.hour, timePicker.minute)
            }
            .show()
    }

    private fun getRadioButtonIdForColor(color: Int, colorOptions: List<HabitColorOption>): Int {
        return colorOptions.firstOrNull { ContextCompat.getColor(this, it.colorResId) == color }
            ?.radioButtonId
            ?: colorOptions.first().radioButtonId
    }

    private fun ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun exportProgressToGmail() {
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_progress_subject))
            putExtra(Intent.EXTRA_TEXT, buildProgressSummary())
            setPackage("com.google.android.gm")
        }

        try {
            startActivity(emailIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.gmail_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildProgressSummary(): String {
        if (habits.isEmpty()) {
            return getString(R.string.progress_summary_empty)
        }

        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        val dateText = dateFormatter.format(LocalDate.now())
        val today = getTodayEpochDay()
        val monthStartDay = today - 29
        val lines = mutableListOf<String>()
        lines += getString(R.string.progress_summary_title)
        lines += getString(R.string.progress_summary_generated_on, dateText)
        lines += ""
        lines += getString(R.string.progress_summary_monthly_overview_header)

        habits.forEach { habit ->
            val startDateText = dateFormatter.format(LocalDate.ofEpochDay(habit.createdDay))
            val monthMissedCount = habit.monthHistory.count { it.state == HistoryCellState.MISSED }
            val monthNotStartedCount = habit.monthHistory.count { it.state == HistoryCellState.NOT_CREATED }
            lines += getString(
                R.string.progress_summary_monthly_overview_line,
                habit.name,
                startDateText,
                habit.monthCompletedCount,
                habit.monthDaysTracked,
                habit.monthPercent,
                monthMissedCount,
                monthNotStartedCount
            )
        }
        lines += ""
        lines += getString(R.string.progress_summary_daily_history_header)

        (monthStartDay..today).forEach { day ->
            val dayText = dateFormatter.format(LocalDate.ofEpochDay(day))
            lines += dayText
            habits.forEach { habit ->
                val state = habit.monthHistory.firstOrNull { it.day == day }?.state ?: HistoryCellState.NOT_CREATED
                val statusText = when (state) {
                    HistoryCellState.COMPLETED -> getString(R.string.progress_summary_status_completed)
                    HistoryCellState.MISSED -> getString(R.string.progress_summary_status_missed)
                    HistoryCellState.NOT_CREATED -> getString(R.string.progress_summary_status_not_started)
                }
                lines += getString(R.string.progress_summary_daily_habit_line, habit.name, statusText)
            }
            lines += ""
        }

        return lines.joinToString(separator = "\n").trimEnd()
    }

    private fun applyLoadedData(screenData: HabitScreenData) {
        earliestAvailableDay = screenData.earliestAvailableDay
        visibleHistoryDayCount = screenData.visibleHistoryDayCount
        historyWindowEndDay = historyWindowEndDay.coerceIn(
            getEarliestHistoryWindowEndDay(),
            getTodayEpochDay()
        )
        habitAdapter.replaceHabits(screenData.habits)
        setupHeaderLabels()
        updateEmptyState()
    }

    private fun setupHeaderLabels() {
        val dayLabels = listOf(
            findViewById<TextView>(R.id.dayOneHeaderText),
            findViewById<TextView>(R.id.dayTwoHeaderText),
            findViewById<TextView>(R.id.dayThreeHeaderText)
        )
        val dates = (historyWindowEndDay - (visibleHistoryDayCount - 1)..historyWindowEndDay)
            .map { epochDay -> LocalDate.ofEpochDay(epochDay) }

        dayLabels.forEachIndexed { index, textView ->
            if (index < dates.size) {
                val date = dates[index]
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                textView.text = "$dayName\n${date.dayOfMonth}"
                textView.visibility = View.VISIBLE
            } else {
                textView.visibility = View.INVISIBLE
            }
        }

        val canGoBackward = historyWindowEndDay > getEarliestHistoryWindowEndDay()
        val canGoForward = historyWindowEndDay < getTodayEpochDay()
        historyBackButton.isEnabled = canGoBackward
        historyBackButton.alpha = if (canGoBackward) 1f else 0.35f
        historyForwardButton.isEnabled = canGoForward
        historyForwardButton.alpha = if (canGoForward) 1f else 0.35f
    }

    private fun shiftHistoryWindow(dayShift: Int) {
        val updatedEndDay = (historyWindowEndDay + dayShift)
            .coerceIn(getEarliestHistoryWindowEndDay(), getTodayEpochDay())
        if (updatedEndDay == historyWindowEndDay) {
            return
        }

        historyWindowEndDay = updatedEndDay
        setupHeaderLabels()
        loadHabits()
    }

    private fun getEarliestHistoryWindowEndDay(): Long {
        return earliestAvailableDay + (visibleHistoryDayCount - 1)
    }

    private fun updateEmptyState() {
        emptyStateText.visibility = if (habits.isEmpty()) View.VISIBLE else View.GONE
        trackerHeaderRow.visibility = if (habits.isEmpty()) View.GONE else View.VISIBLE
    }
}
