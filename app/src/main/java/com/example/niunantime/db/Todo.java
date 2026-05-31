package com.example.niunantime.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "todos")
public class Todo {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public boolean completed;
    public long timestamp;
    public Long completedTime; // null=未完成，完成时设为完成时刻
    public Long reminderTime; // null or 0 = no reminder, otherwise alarm timestamp in millis
    public boolean reminded;  // whether notification has been sent
}
