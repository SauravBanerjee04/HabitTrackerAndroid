package com.example.habittracker;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "habit_entries",
        foreignKeys = @ForeignKey(
                entity = HabitEntity.class,
                parentColumns = "id",
                childColumns = "habitId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index(value = {"habitId", "day"}, unique = true),
                @Index(value = {"habitId"})
        }
)
public class HabitEntryEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long habitId;

    public long day;
}
