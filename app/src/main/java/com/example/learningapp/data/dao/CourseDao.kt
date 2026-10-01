package com.example.learningapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.learningapp.data.entity.CourseEntity
import com.example.learningapp.data.entity.LessonEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CourseDao {

    /* ---------- Courses (home list) ---------- */

    /** Room re-emits whenever the table changes, so the UI always reflects the DB. */
    @Query("SELECT * FROM courses ORDER BY id ASC")
    abstract fun observeAll(): Flow<List<CourseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(courses: List<CourseEntity>)

    @Query("DELETE FROM courses")
    abstract suspend fun clear()

    /**
     * Swaps the whole table with the server's list, then re-derives progress from any cached
     * lessons, so progress the user changed locally isn't overwritten by the server's value.
     */
    @Transaction
    open suspend fun replaceAll(courses: List<CourseEntity>) {
        clear()
        insertAll(courses)
        syncProgressFromLessons()
    }

    /* ---------- Course details ---------- */

    @Query("SELECT * FROM courses WHERE id = :courseId")
    abstract fun observeById(courseId: Int): Flow<CourseEntity?>

    @Query("SELECT * FROM lessons WHERE courseId = :courseId ORDER BY position ASC")
    abstract fun observeLessons(courseId: Int): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE courseId = :courseId")
    protected abstract suspend fun getLessons(courseId: Int): List<LessonEntity>

    @Query("DELETE FROM lessons WHERE courseId = :courseId")
    protected abstract suspend fun deleteLessons(courseId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertLessons(lessons: List<LessonEntity>)

    @Query("UPDATE lessons SET isCompleted = :completed WHERE courseId = :courseId AND id = :lessonId")
    protected abstract suspend fun updateLessonCompleted(courseId: Int, lessonId: Int, completed: Boolean)

    /**
     * progress = completed lessons * 100 / total lessons, for every course that has cached lessons.
     * Courses without cached lessons keep the progress they were given by the server.
     */
    @Query(
        "UPDATE courses SET progress = (" +
                "SELECT SUM(isCompleted) * 100 / COUNT(*) FROM lessons WHERE lessons.courseId = courses.id" +
                ") WHERE EXISTS (SELECT 1 FROM lessons WHERE lessons.courseId = courses.id)"
    )
    protected abstract suspend fun syncProgressFromLessons()

    /**
     * Saves the lessons downloaded from the server.
     * Lessons already on the device keep their local completed/pending state; the server's flag
     * is only used the first time a lesson is seen. Progress is then re-derived.
     */
    @Transaction
    open suspend fun saveDetails(courseId: Int, lessons: List<LessonEntity>) {
        val localState = getLessons(courseId).associate { it.id to it.isCompleted }
        val merged = lessons.map { it.copy(isCompleted = localState[it.id] ?: it.isCompleted) }
        deleteLessons(courseId)
        insertLessons(merged)
        syncProgressFromLessons()
    }

    /** Local toggle: updates the lesson and the course progress together, in one transaction. */
    @Transaction
    open suspend fun setLessonCompleted(courseId: Int, lessonId: Int, completed: Boolean) {
        updateLessonCompleted(courseId, lessonId, completed)
        syncProgressFromLessons()
    }

    /* ---------- Logout ---------- */

    @Query("DELETE FROM lessons")
    protected abstract suspend fun clearLessons()

    @Transaction
    open suspend fun clearAll() {
        clear()
        clearLessons()
    }
}