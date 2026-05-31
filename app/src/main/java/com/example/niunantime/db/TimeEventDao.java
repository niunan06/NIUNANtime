package com.example.niunantime.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TimeEventDao {
    @Insert
    void insert(TimeEvent event);

    @Query("SELECT * FROM time_events ORDER BY timestamp DESC")
    List<TimeEvent> getAllEvents();

    @Query("SELECT * FROM time_events WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    List<TimeEvent> getTodayEvents(long startOfDay);

    @Query("SELECT * FROM time_events WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    List<TimeEvent> getEventsSince(long startTime);

    @Query("SELECT * FROM time_events WHERE id = :id")
    TimeEvent getById(int id);

    @Query("SELECT * FROM time_events WHERE eventName = :name AND timestamp >= :startTime ORDER BY timestamp ASC")
    List<TimeEvent> getEventsByNameSince(String name, long startTime);

    @Query("SELECT * FROM time_events WHERE eventName = :name ORDER BY timestamp DESC LIMIT 1")
    TimeEvent getByName(String name);

    @Update
    void update(TimeEvent event);

    @Delete
    void delete(TimeEvent event);
}
