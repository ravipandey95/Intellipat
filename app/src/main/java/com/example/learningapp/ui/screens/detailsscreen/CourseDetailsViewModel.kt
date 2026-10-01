package com.example.learningapp.ui.screens.detailsscreen

import com.example.learningapp.repository.Course
import com.example.learningapp.repository.CourseDetailsRepository
import com.example.learningapp.repository.Lesson
import com.example.learningapp.utils.ApiException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Navigation argument name, shared with the NavHost in MainActivity. */
const val ARG_COURSE_ID = "courseId"

data class CourseDetailsUiState(
    /** Course header (name, progress). Null until the DB emits, or if the course doesn't exist. */
    val course: Course? = null,
    val lessons: List<Lesson> = emptyList(),
    /** First load: no saved lessons yet and a request is running. */
    val isLoading: Boolean = true,
    /** Refreshing while saved lessons are already on screen. */
    val isRefreshing: Boolean = false,
    /** The lesson currently being marked as completed (shows a spinner on that row). */
    val updatingLessonId: Int? = null,
    /** Last refresh failed. Full-screen if there are no lessons, a banner otherwise. */
    val errorMessage: String? = null,
)

@HiltViewModel
class CourseDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CourseDetailsRepository,
) : ViewModel() {

    private val courseId: Int = checkNotNull(savedStateHandle.get<Int>(ARG_COURSE_ID)) {
        "CourseDetailsViewModel needs the '$ARG_COURSE_ID' navigation argument"
    }

    private data class OpState(
        val refreshing: Boolean = false,
        val error: String? = null,
        val updatingLessonId: Int? = null,
    )

    private val opState = MutableStateFlow(OpState())

    // One-off messages (snackbar), e.g. "couldn't update the lesson"
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    // The DB drives the content; opState only adds loading / error / "updating" overlays.
    val uiState: StateFlow<CourseDetailsUiState> =
        combine(repository.observeDetails(courseId), opState) { details, op ->
            val lessons = details?.lessons.orEmpty()
            CourseDetailsUiState(
                course = details?.course,
                lessons = lessons,
                isLoading = lessons.isEmpty() && op.refreshing,
                isRefreshing = lessons.isNotEmpty() && op.refreshing,
                updatingLessonId = op.updatingLessonId,
                errorMessage = op.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseDetailsUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (opState.value.refreshing) return
        opState.update { it.copy(refreshing = true, error = null) }

        viewModelScope.launch {
            val result = repository.refresh(courseId)
            opState.update {
                it.copy(
                    refreshing = false,
                    error = result.exceptionOrNull()?.toUserMessage("load the lessons"),
                )
            }
        }
    }

    fun onCompleteLesson(lessonId: Int) {
        if (opState.value.updatingLessonId != null) return // one update at a time
        opState.update { it.copy(updatingLessonId = lessonId) }

        viewModelScope.launch {
            repository.markLessonCompleted(courseId, lessonId)
                .onFailure { _messages.send(it.toUserMessage("mark the lesson as completed")) }
            // On success nothing else is needed: the DB changed, so the lesson row, this screen's
            // progress and the home list all update themselves.
            opState.update { it.copy(updatingLessonId = null) }
        }
    }

    private fun Throwable.toUserMessage(action: String): String = when (this) {
        is ApiException.Network -> "Network error. Check your connection and try again."
        is ApiException.Http -> "Couldn't $action (server error $code)."
        else -> "Couldn't $action. Please try again."
    }
}