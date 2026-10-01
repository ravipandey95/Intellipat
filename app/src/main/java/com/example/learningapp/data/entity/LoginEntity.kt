package com.example.learningapp.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per account that has logged in on this device.
 * We never store the raw password: only a salted PBKDF2 hash (used to verify
 * the password when the API is unreachable) and the auth token from the server.
 */

@Entity(tableName = "sessions")
data class LoginEntity(
    @PrimaryKey val email: String,
    val token: String,
    val passwordHash: ByteArray,
    val salt: ByteArray,
    val isActive: Boolean,      // true = this is the currently logged-in user
    val updatedAt: Long,
)