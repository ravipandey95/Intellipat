package com.example.learningapp.data.entity

import androidx.room.Entity

@Entity(tableName = "lessons", primaryKeys = ["courseId", "id"])
data class LessonEntity(
    val id: Int,
    val courseId: Int,
    val title: String,
    val isCompleted: Boolean,
    val position: Int,
)