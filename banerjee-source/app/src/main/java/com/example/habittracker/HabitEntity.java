package com.example.habittracker;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "habits")
public class HabitEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    public int color;

    public long createdAt;

    public boolean remindersEnabled;

    public int reminderHour = 20;

    public int reminderMinute = 0;
}
