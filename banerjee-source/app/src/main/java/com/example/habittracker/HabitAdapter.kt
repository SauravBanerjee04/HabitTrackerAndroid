package com.example.habittracker

import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class HabitAdapter(
    private val habits: MutableList<HabitUiModel>,
    private val onHabitCheckedChanged: (position: Int, isChecked: Boolean) -> Unit,
    private val onHabitDeleteClicked: (position: Int) -> Unit,
    private val onHabitEditClicked: (position: Int) -> Unit,
    private val onHistoryShiftRequested: (dayShift: Int) -> Unit
) : RecyclerView.Adapter<HabitAdapter.HabitViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_habit, parent, false)
        return HabitViewHolder(view)
    }

    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        holder.bind(habits[position])
    }

    override fun getItemCount(): Int = habits.size

    fun addHabit(habit: HabitUiModel) {
        habits.add(habit)
        notifyItemInserted(habits.lastIndex)
    }

    fun replaceHabits(newHabits: List<HabitUiModel>) {
        habits.clear()
        habits.addAll(newHabits)
        notifyDataSetChanged()
    }

    inner class HabitViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorIndicator: View = itemView.findViewById(R.id.colorIndicator)
        private val habitNameText: TextView = itemView.findViewById(R.id.habitNameText)
        private val habitStatusText: TextView = itemView.findViewById(R.id.habitStatusText)
        private val doneTodayCheckBox: CheckBox = itemView.findViewById(R.id.doneTodayCheckBox)
        private val recentDayOneView: View = itemView.findViewById(R.id.recentDayOneView)
        private val recentDayTwoView: View = itemView.findViewById(R.id.recentDayTwoView)
        private val recentDayThreeView: View = itemView.findViewById(R.id.recentDayThreeView)
        private val weekPercentText: TextView = itemView.findViewById(R.id.weekPercentText)
        private val monthPercentText: TextView = itemView.findViewById(R.id.monthPercentText)
        private val editHabitButton: TextView = itemView.findViewById(R.id.editHabitButton)
        private val deleteHabitButton: TextView = itemView.findViewById(R.id.deleteHabitButton)
        private val historyStripContainer: LinearLayout = itemView.findViewById(R.id.historyStripContainer)

        fun bind(habit: HabitUiModel) {
            colorIndicator.setBackgroundColor(habit.color)
            habitNameText.text = habit.name
            habitStatusText.text = if (habit.doneToday) {
                itemView.context.getString(R.string.done_today)
            } else {
                itemView.context.getString(R.string.not_completed_yet)
            }

            doneTodayCheckBox.setOnCheckedChangeListener(null)
            doneTodayCheckBox.isChecked = habit.doneToday
            doneTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onHabitCheckedChanged(adapterPosition, isChecked)
                }
            }

            val dayViews = listOf(recentDayOneView, recentDayTwoView, recentDayThreeView)
            dayViews.forEachIndexed { index, dayView ->
                if (index < habit.recentDays.size) {
                    dayView.visibility = View.VISIBLE
                    dayView.background = createDayCellDrawable(habit.color, habit.recentDays[index])
                } else {
                    dayView.visibility = View.INVISIBLE
                    dayView.background = null
                }
            }

            weekPercentText.text = itemView.context.getString(R.string.week_percent_format, habit.weekPercent)
            weekPercentText.setTextColor(habit.color)
            monthPercentText.text = itemView.context.getString(R.string.month_percent_format, habit.monthPercent)
            monthPercentText.setTextColor(habit.color)

            editHabitButton.setOnClickListener {
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onHabitEditClicked(adapterPosition)
                }
            }

            deleteHabitButton.setOnClickListener {
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onHabitDeleteClicked(adapterPosition)
                }
            }

            val historyGestureDetector = GestureDetector(itemView.context, object : GestureDetector.SimpleOnGestureListener() {
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
                            onHistoryShiftRequested(-1)
                        } else {
                            onHistoryShiftRequested(1)
                        }
                        return true
                    }
                    return false
                }
            })

            historyStripContainer.setOnTouchListener { _, motionEvent ->
                historyGestureDetector.onTouchEvent(motionEvent)
            }
        }

        private fun createDayCellDrawable(color: Int, state: HistoryCellState): GradientDrawable {
            val fillColor = when (state) {
                HistoryCellState.COMPLETED -> ColorUtils.setAlphaComponent(color, 220)
                HistoryCellState.MISSED -> itemView.context.getColor(R.color.tracker_cell_empty)
                HistoryCellState.NOT_CREATED -> itemView.context.getColor(R.color.tracker_cell_not_created)
            }

            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(fillColor)
                setStroke(1, itemView.context.getColor(R.color.tracker_cell_border))
            }
        }
    }
}
