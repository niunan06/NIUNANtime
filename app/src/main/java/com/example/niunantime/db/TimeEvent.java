package com.example.niunantime.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "time_events")
public class TimeEvent {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String eventName;
    public long durationSeconds;
    public String type; // "stopwatch" 或 "countdown"
    public long timestamp; // 完成时的 System.currentTimeMillis()
}
