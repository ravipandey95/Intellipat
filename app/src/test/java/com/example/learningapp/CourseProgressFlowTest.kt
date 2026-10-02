package com.example.learningapp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.learningapp.data.AppDatabase
import com.example.learningapp.data.network.CourseDetailsDto
import com.example.learningapp.data.network.CourseDto
import com.example.learningapp.data.network.CoursesService
import com.example.learningapp.data.network.LessonDto
import com.example.learningapp.repository.CourseDetailsRepositoryImpl
import com.example.learningapp.repository.CourseRepositoryImpl
import com.example.learningapp.utils.ApiException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

/**
 * Protects the app's central rule: the user's lesson ticks are the source of truth for progress.
 *
 *  - ticking / unticking a lesson changes the course progress on BOTH screens (home list + details)
 *  - a refresh from the server must never undo the user's choices or pull progress back
 *  - when the network fails, saved data stays as it was and the failure is reported as a network error
 *
 * Why one scenario test on a real (in-memory) Room database and not several mocked ones:
 * progress is computed in SQL and the merge with local choices is a DAO transaction, so a mocked DAO
 * would only test the mock. Only the network is faked here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CourseProgressFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var service: StubCoursesService
    private lateinit var courses: CourseRepositoryImpl
    private lateinit var details: CourseDetailsRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        service = StubCoursesService()
        courses = CourseRepositoryImpl(service, db.courseDao())
        details = CourseDetailsRepositoryImpl(service, db.courseDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `local lesson toggles update progress and survive server refreshes and offline failures`() =
        runBlocking<Unit> {
            // 1. First load. The server says lessons 101 and 102 are done: 2 of 5 = 40%.
            assertTrue(courses.refresh().isSuccess)
            assertTrue(details.refresh(COURSE_ID).isSuccess)
            assertEquals("progress after first load", 40, homeProgress())
            assertEquals(setOf(101, 102), completedLessons())

            // 2. The user ticks lesson 103: 3 of 5 = 60%.
            //    The HOME list must reflect it too, not only the details screen.
            details.setLessonCompleted(COURSE_ID, 103, completed = true)
            assertEquals("home progress after ticking 103", 60, homeProgress())
            assertEquals("details progress after ticking 103", 60, detailsProgress())
            assertEquals(setOf(101, 102, 103), completedLessons())

            // 3. Ticks 104 (4 of 5 = 80%), then unticks 101 (3 of 5 = 60%). Unticking must work too.
            details.setLessonCompleted(COURSE_ID, 104, completed = true)
            assertEquals("progress after ticking 104", 80, homeProgress())
            details.setLessonCompleted(COURSE_ID, 101, completed = false)
            assertEquals("progress after unticking 101", 60, homeProgress())
            assertEquals(setOf(102, 103, 104), completedLessons())

            // 4. The server still only knows its original state (101 + 102 done, 40%).
            //    Refreshing both screens must NOT overwrite the user's choices or pull progress back to 40.
            assertTrue(courses.refresh().isSuccess)
            assertTrue(details.refresh(COURSE_ID).isSuccess)
            assertEquals("home progress after server refresh", 60, homeProgress())
            assertEquals("details progress after server refresh", 60, detailsProgress())
            assertEquals(setOf(102, 103, 104), completedLessons())

            // 5. Offline: refresh fails with a network error and leaves the saved data untouched.
            service.isOnline = false
            assertTrue(courses.refresh().exceptionOrNull() is ApiException.Network)
            assertTrue(details.refresh(COURSE_ID).exceptionOrNull() is ApiException.Network)
            assertEquals("progress while offline", 60, homeProgress())
            assertEquals(setOf(102, 103, 104), completedLessons())
        }

    /* ---------- What each screen would currently show (read from the DB, like the UI does) ---------- */

    private suspend fun homeProgress(): Int =
        courses.observeCourses().first().single { it.id == COURSE_ID }.progress

    private suspend fun detailsProgress(): Int =
        details.observeDetails(COURSE_ID).first()!!.course.progress

    private suspend fun completedLessons(): Set<Int> =
        details.observeDetails(COURSE_ID).first()!!
            .lessons.filter { it.isCompleted }.map { it.id }.toSet()

    private companion object {
        const val COURSE_ID = 1
    }
}

/**
 * A server that never learns about the user's local ticks (they are device-only):
 * it always reports lessons 101 and 102 as completed. Can be switched offline.
 */
private class StubCoursesService : CoursesService {

    var isOnline = true

    override suspend fun getCourses(): Response<List<CourseDto>> {
        if (!isOnline) throw IOException("offline")
        return Response.success(
            listOf(
                CourseDto(id = 1, title = "Python Programming", instructor = "John Smith", progress = 40, lessons = 5),
            ),
        )
    }

    override suspend fun getCourseDetails(courseId: Int): Response<CourseDetailsDto> {
        if (!isOnline) throw IOException("offline")
        return Response.success(
            CourseDetailsDto(
                id = 1,
                title = "Python Programming",
                progress = 40,
                lessons = (101..105).map { lessonId ->
                    LessonDto(id = lessonId, title = "Lesson ${lessonId - 100}", completed = lessonId in setOf(101, 102))
                },
            ),
        )
    }
}