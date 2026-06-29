package com.example.habittracker;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY id ASC")
    List<HabitEntity> getAllHabits();

    @Query("SELECT * FROM habit_entries WHERE day = :day")
    List<HabitEntryEntity> getEntriesForDay(long day);

    @Query("SELECT * FROM habit_entries WHERE day >= :startDay")
    List<HabitEntryEntity> getEntriesFromDay(long startDay);

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND day >= :startDay")
    List<HabitEntryEntity> getEntriesForHabitFromDay(long habitId, long startDay);

    @Query("SELECT * FROM habits WHERE id = :habitId LIMIT 1")
    HabitEntity getHabitById(long habitId);

    @Query("SELECT * FROM habits WHERE remindersEnabled = 1")
    List<HabitEntity> getHabitsWithRemindersEnabled();

    @Insert
    long insertHabit(HabitEntity habit);

    @Query("UPDATE habits SET name = :habitName, color = :color, remindersEnabled = :remindersEnabled, reminderHour = :reminderHour, reminderMinute = :reminderMinute WHERE id = :habitId")
    void updateHabitDetails(long habitId, String habitName, int color, boolean remindersEnabled, int reminderHour, int reminderMinute);

    @Query("UPDATE habits SET createdAt = :createdAt WHERE id = :habitId")
    void updateHabitCreatedAt(long habitId, long createdAt);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertEntry(HabitEntryEntity entry);

    @Query("DELETE FROM habit_entries WHERE habitId = :habitId AND day = :day")
    void deleteEntryForDay(long habitId, long day);

    @Query("DELETE FROM habits WHERE id = :habitId")
    void deleteHabit(long habitId);
}
