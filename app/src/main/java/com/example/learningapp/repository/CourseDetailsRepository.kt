package com.example.learningapp.repository

import com.example.learningapp.data.dao.CourseDao
import com.example.learningapp.data.entity.LessonEntity
import com.example.learningapp.data.network.CoursesService
import com.example.learningapp.data.network.LessonDto
import com.example.learningapp.utils.ApiException
import com.example.learningapp.utils.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

data class Lesson(
    val id: Int,
    val title: String,
    val isCompleted: Boolean,
)

/** Course header (name, progress) plus its lessons. */
data class CourseDetails(
    val course: Course,
    val lessons: List<Lesson>,
)

interface CourseDetailsRepository {
    /**
     * Always emits what is in the local DB (so it works offline once loaded).
     * Emits null if the course isn't in the DB.
     */
    fun observeDetails(courseId: Int): Flow<CourseDetails?>

    /**
     * Downloads the lessons. Only an HTTP 200 updates the DB; failures leave saved data untouched.
     * Lessons already on the device keep their local completed/pending state.
     */
    suspend fun refresh(courseId: Int): Result<Unit>

    /**
     * Marks a lesson completed or pending. Local only, no network involved.
     * The course progress is updated in the same transaction, so the details screen
     * and the home list both reflect the change.
     */
    suspend fun setLessonCompleted(courseId: Int, lessonId: Int, completed: Boolean): Result<Unit>
}

@Singleton
class CourseDetailsRepositoryImpl @Inject constructor(
    private val service: CoursesService,
    private val dao: CourseDao,
) : CourseDetailsRepository {

    override fun observeDetails(courseId: Int): Flow<CourseDetails?> =
        combine(dao.observeById(courseId), dao.observeLessons(courseId)) { course, lessons ->
            course?.let { CourseDetails(it.toCourse(), lessons.map { l -> l.toLesson() }) }
        }

    override suspend fun refresh(courseId: Int): Result<Unit> =
        try {
            val response = apiCall { service.getCourseDetails(courseId) }

            // Success is exactly HTTP 200. Anything else is treated as a failure.
            if (response.code() != SUCCESS_CODE) {
                throw ApiException.Http(response.code(), response.message())
            }
            val dto = response.body()
                ?: throw ApiException.Unknown(IllegalStateException("Empty response body"))

            // dto.progress is ignored on purpose: progress is derived from the lessons on the device.
            val lessons = dto.lessons.orEmpty().mapIndexedNotNull { index, lesson ->
                lesson.toEntity(courseId, position = index)
            }
            dao.saveDetails(courseId, lessons)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e) // ApiException.Network / Http / Unknown, or a DB error
        }

    override suspend fun setLessonCompleted(
        courseId: Int,
        lessonId: Int,
        completed: Boolean,
    ): Result<Unit> =
        try {
            dao.setLessonCompleted(courseId, lessonId, completed)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private companion object {
        const val SUCCESS_CODE = 200
    }
}

/* ---------- Mappers ---------- */

/** Returns null for malformed entries so one bad lesson can't break the whole screen. */
private fun LessonDto.toEntity(courseId: Int, position: Int): LessonEntity? {
    val lessonId = id ?: return null
    val lessonTitle = title?.takeIf { it.isNotBlank() } ?: return null
    return LessonEntity(
        id = lessonId,
        courseId = courseId,
        title = lessonTitle,
        isCompleted = completed ?: false,
        position = position,
    )
}

private fun LessonEntity.toLesson() = Lesson(
    id = id,
    title = title,
    isCompleted = isCompleted,
)