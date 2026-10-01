package com.example.learningapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.learningapp.data.entity.LoginEntity

@Dao
abstract class LoginDao {

    @Query("SELECT * FROM sessions WHERE isActive = 1 LIMIT 1")
    abstract suspend fun getActive(): LoginEntity?

    @Query("SELECT * FROM sessions WHERE email = :email")
    abstract suspend fun getByEmail(email: String): LoginEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsert(entity: LoginEntity)

    @Query("UPDATE sessions SET isActive = 0")
    abstract suspend fun deactivateAll()

    @Query("DELETE FROM sessions WHERE email = :email")
    abstract suspend fun delete(email: String)

    /** Makes [entity] the single active session, atomically. */
    @Transaction
    open suspend fun activate(entity: LoginEntity) {
        deactivateAll()
        upsert(entity.copy(isActive = true))
    }
}