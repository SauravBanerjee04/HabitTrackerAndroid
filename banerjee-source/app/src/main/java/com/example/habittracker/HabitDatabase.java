package com.example.habittracker;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {HabitEntity.class, HabitEntryEntity.class}, version = 2, exportSchema = false)
public abstract class HabitDatabase extends RoomDatabase {
    public abstract HabitDao habitDao();

    private static volatile HabitDatabase INSTANCE;
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE habits ADD COLUMN remindersEnabled INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE habits ADD COLUMN reminderHour INTEGER NOT NULL DEFAULT 20");
            database.execSQL("ALTER TABLE habits ADD COLUMN reminderMinute INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static HabitDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (HabitDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    HabitDatabase.class,
                                    "habit_tracker.db"
                            )
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
