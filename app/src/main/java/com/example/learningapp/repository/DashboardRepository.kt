package com.example.learningapp.repository

import com.example.learningapp.data.dao.CourseDao
import com.example.learningapp.data.entity.CourseEntity
import com.example.learningapp.data.network.CourseDto
import com.example.learningapp.data.network.CoursesService
import com.example.learningapp.utils.ApiException
import com.example.learningapp.utils.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** What the UI works with (independent of Room and of the JSON format). */
data class Course(
    val id: Int,
    val title: String,
    val instructor: String,
    val progress: Int,
    val lessons: Int,
)

interface CourseRepository {
    /** Always emits what is in the local DB. The DB is the single source of truth. */
    fun observeCourses(): Flow<List<Course>>

    /**
     * Fetches from the API. Only an HTTP 200 replaces the DB contents.
     * On any failure the DB is left untouched, so previously saved courses stay visible.
     */
    suspend fun refresh(): Result<Unit>

    /** Removes locally saved courses (e.g. on logout, since progress is per user). */
    suspend fun clearCache()
}

@Singleton
class CourseRepositoryImpl @Inject constructor(
    private val service: CoursesService,
    private val dao: CourseDao,
) : CourseRepository {

    override fun observeCourses(): Flow<List<Course>> =
        dao.observeAll().map { list -> list.map { it.toCourse() } }

    override suspend fun refresh(): Result<Unit> =
        try {
            val response = apiCall { service.getCourses() }

            // Success is exactly HTTP 200. Anything else is treated as a failure.
            if (response.code() != SUCCESS_CODE) {
                throw ApiException.Http(response.code(), response.message())
            }
            val body = response.body()
                ?: throw ApiException.Unknown(IllegalStateException("Empty response body"))

            dao.replaceAll(body.mapNotNull { it.toEntity() })
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e) // ApiException.Network / Http / Unknown, or a DB error
        }

    override suspend fun clearCache() = dao.clear()

    private companion object {
        const val SUCCESS_CODE = 200
    }
}

/* ---------- Mappers ---------- */

/** Returns null for malformed entries (missing id/title/instructor) so one bad item can't break the list. */
private fun CourseDto.toEntity(): CourseEntity? {
    val courseId = id ?: return null
    val courseTitle = title?.takeIf { it.isNotBlank() } ?: return null
    val courseInstructor = instructor?.takeIf { it.isNotBlank() } ?: return null
    return CourseEntity(
        id = courseId,
        title = courseTitle,
        instructor = courseInstructor,
        progress = (progress ?: 0).coerceIn(0, 100),
        lessons = (lessons ?: 0).coerceAtLeast(0),
    )
}

fun CourseEntity.toCourse() = Course(
    id = id,
    title = title,
    instructor = instructor,
    progress = progress,
    lessons = lessons,
)