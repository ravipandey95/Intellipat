package com.example.learningapp.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.learningapp.data.dao.CourseDao
import com.example.learningapp.data.dao.LoginDao
import com.example.learningapp.data.entity.CourseEntity
import com.example.learningapp.data.entity.LessonEntity
import com.example.learningapp.data.entity.LoginEntity

@Database(entities = [LoginEntity::class, CourseEntity::class, LessonEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun loginDao(): LoginDao
    abstract fun courseDao(): CourseDao
}