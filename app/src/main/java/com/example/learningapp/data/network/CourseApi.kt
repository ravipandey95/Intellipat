package com.example.learningapp.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Returns the raw [Response] (not just the body) so repositories can check the HTTP status code themselves.
 */
interface CoursesService {

    @GET("courses")
    suspend fun getCourses(): Response<List<CourseDto>>

    @GET("courses/{courseId}")
    suspend fun getCourseDetails(@Path("courseId") courseId: Int): Response<CourseDetailsDto>

    /** Returns the updated course (new progress + lessons) so the client can store it as-is. */
    @POST("courses/{courseId}/lessons/{lessonId}/complete")
    suspend fun completeLesson(
        @Path("courseId") courseId: Int,
        @Path("lessonId") lessonId: Int,
    ): Response<CourseDetailsDto>
}

/* Nullable fields: Gson ignores Kotlin nullability, so repositories validate before saving. */

data class CourseDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("instructor") val instructor: String?,
    @SerializedName("progress") val progress: Int?,
    @SerializedName("lessons") val lessons: Int?,
)

data class CourseDetailsDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("progress") val progress: Int?,
    @SerializedName("lessons") val lessons: List<LessonDto>?,
)

data class LessonDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("completed") val completed: Boolean?,
)