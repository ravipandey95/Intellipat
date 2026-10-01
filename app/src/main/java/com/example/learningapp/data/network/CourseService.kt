package com.example.learningapp.data.network

import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class FakeApiScenario { SUCCESS, SERVER_ERROR, NETWORK_ERROR }

/** Debug-only switch for the fake backend. Delete together with [FakeCoursesService]. */
object FakeApiConfig {
    @Volatile
    var scenario: FakeApiScenario = FakeApiScenario.SUCCESS
}

/* ---------- Seed data: 10 courses x 5 lessons ---------- */

private class SeedCourse(
    val id: Int,
    val title: String,
    val instructor: String,
    val lessonTitles: List<String>,
    /** The first N lessons start out completed. */
    val initiallyCompleted: Int,
)

private val SEED_COURSES = listOf(
    SeedCourse(
        1, "Python Programming", "John Smith",
        listOf("Introduction", "Variables & Data Types", "Control Flow", "Functions", "Object-Oriented Programming"),
        initiallyCompleted = 3,
    ),
    SeedCourse(
        2, "Generative AI", "Sarah Williams",
        listOf("Introduction to Generative AI", "How LLMs Work", "Prompt Engineering", "Building with APIs", "Responsible AI"),
        initiallyCompleted = 2,
    ),
    SeedCourse(
        3, "Full Stack Development", "David Brown",
        listOf("Web Fundamentals", "HTML & CSS", "JavaScript Essentials", "Backend with Node.js", "Databases & Deployment"),
        initiallyCompleted = 1,
    ),
    SeedCourse(
        4, "Data Structures & Algorithms", "Emily Johnson",
        listOf("Complexity Analysis", "Arrays & Linked Lists", "Stacks & Queues", "Trees & Graphs", "Sorting & Searching"),
        initiallyCompleted = 4,
    ),
    SeedCourse(
        5, "Machine Learning Basics", "Michael Lee",
        listOf("What is Machine Learning", "Data Preprocessing", "Regression", "Classification", "Model Evaluation"),
        initiallyCompleted = 0,
    ),
    SeedCourse(
        6, "Android Development with Kotlin", "Priya Patel",
        listOf("Kotlin Basics", "Project Setup", "Jetpack Compose UI", "State & ViewModel", "Networking & Room"),
        initiallyCompleted = 3,
    ),
    SeedCourse(
        7, "UI/UX Design Fundamentals", "Olivia Martinez",
        listOf("Design Principles", "Color & Typography", "Wireframing", "Prototyping", "Usability Testing"),
        initiallyCompleted = 5,
    ),
    SeedCourse(
        8, "Cloud Computing with AWS", "Daniel Garcia",
        listOf("Cloud Concepts", "IAM & Security", "EC2 & Compute", "S3 & Storage", "Networking with VPC"),
        initiallyCompleted = 2,
    ),
    SeedCourse(
        9, "Cybersecurity Essentials", "Aisha Khan",
        listOf("Security Fundamentals", "Network Security", "Cryptography Basics", "Threats & Vulnerabilities", "Incident Response"),
        initiallyCompleted = 0,
    ),
    SeedCourse(
        10, "DevOps and CI/CD", "Robert Wilson",
        listOf("DevOps Culture", "Version Control with Git", "CI Pipelines", "Containers & Docker", "Monitoring & Delivery"),
        initiallyCompleted = 4,
    ),
)

/**
 * Fake backend with in-memory state, so it behaves like a real server:
 * completing a lesson changes the progress that BOTH the list and details endpoints return.
 *
 * Every call first behaves like a real request:
 *  - device offline          -> IOException
 *  - scenario NETWORK_ERROR  -> IOException
 *  - scenario SERVER_ERROR   -> HTTP 500 (state is NOT changed)
 *  - scenario SUCCESS        -> HTTP 200 with the data below
 *
 * Note: the state lives in memory, so it resets to the seed data when the app process is killed.
 */
@Singleton
class FakeCoursesService @Inject constructor(
    private val networkChecker: NetworkChecker,
) : CoursesService {

    private val lock = Any()

    /** courseId -> ids of completed lessons */
    private val completedLessons: Map<Int, MutableSet<Int>> =
        SEED_COURSES.associate { seed ->
            seed.id to seed.lessonTitles.indices
                .take(seed.initiallyCompleted)
                .map { lessonIdFor(seed.id, it) }
                .toMutableSet()
        }

    override suspend fun getCourses(): Response<List<CourseDto>> {
        return respond {
            val courses = synchronized(lock) { SEED_COURSES.map { it.toCourseDto() } }
            Response.success(courses)
        }
    }

    override suspend fun getCourseDetails(courseId: Int): Response<CourseDetailsDto> {
        return respond {
            val dto = synchronized(lock) { SEED_COURSES.find { it.id == courseId }?.toDetailsDto() }
            if (dto != null) Response.success(dto) else notFound()
        }
    }

    override suspend fun completeLesson(courseId: Int, lessonId: Int): Response<CourseDetailsDto> {
        return respond {
            val dto = synchronized(lock) {
                val seed = SEED_COURSES.find { it.id == courseId }
                val lessonExists = seed != null &&
                        seed.lessonTitles.indices.any { lessonIdFor(seed.id, it) == lessonId }
                if (seed != null && lessonExists) {
                    completedLessons.getValue(courseId).add(lessonId) // idempotent
                    seed.toDetailsDto()
                } else {
                    null
                }
            }
            if (dto != null) Response.success(dto) else notFound()
        }
    }

    /* ---------- Simulated network behaviour ---------- */

    private suspend fun <T> respond(block: () -> Response<T>): Response<T> {
        delay(LATENCY_MS)
        if (!networkChecker.isOnline()) throw IOException("No internet connection")

        return when (FakeApiConfig.scenario) {
            FakeApiScenario.SUCCESS -> block()
            FakeApiScenario.SERVER_ERROR -> Response.error(500, emptyBody())
            FakeApiScenario.NETWORK_ERROR -> throw IOException("Simulated network failure")
        }
    }

    private fun <T> notFound(): Response<T> = Response.error(404, emptyBody())

    private fun emptyBody(): ResponseBody = "{}".toResponseBody("application/json".toMediaType())

    /* ---------- "Server side" mapping (call inside synchronized(lock)) ---------- */

    private fun progressOf(seed: SeedCourse): Int =
        completedLessons.getValue(seed.id).size * 100 / seed.lessonTitles.size

    private fun SeedCourse.toCourseDto() = CourseDto(
        id = id,
        title = title,
        instructor = instructor,
        progress = progressOf(this),
        lessons = lessonTitles.size,
    )

    private fun SeedCourse.toDetailsDto(): CourseDetailsDto {
        val done = completedLessons.getValue(id)
        return CourseDetailsDto(
            id = id,
            title = title,
            progress = progressOf(this),
            lessons = lessonTitles.mapIndexed { index, lessonTitle ->
                val lessonId = lessonIdFor(id, index)
                LessonDto(id = lessonId, title = lessonTitle, completed = lessonId in done)
            },
        )
    }

    private companion object {
        const val LATENCY_MS = 1000L

        /** Course 1 -> lessons 101..105, course 2 -> 201..205, ... */
        fun lessonIdFor(courseId: Int, index: Int): Int = courseId * 100 + index + 1
    }
}