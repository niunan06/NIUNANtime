package com.example.niunantime.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TodoDao {
    @Insert
    void insert(Todo todo);

    @Update
    void update(Todo todo);

    @Delete
    void delete(Todo todo);

    @Query("SELECT * FROM todos WHERE id = :id")
    Todo getById(int id);

    @Query("SELECT * FROM todos WHERE completed = 0")
    List<Todo> getUncompletedTodos();

    @Query("SELECT * FROM todos WHERE completedTime >= :dayStart AND completedTime < :dayEnd ORDER BY completedTime DESC")
    List<Todo> getTodosCompletedBetween(long dayStart, long dayEnd);

    @Query("SELECT * FROM todos ORDER BY completed ASC, timestamp DESC")
    List<Todo> getAllTodos();

    @Query("DELETE FROM todos")
    void deleteAll();
}
