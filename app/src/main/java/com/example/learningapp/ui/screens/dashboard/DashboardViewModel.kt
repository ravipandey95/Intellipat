package com.example.learningapp.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learningapp.repository.Course
import com.example.learningapp.repository.CourseRepository
import com.example.learningapp.repository.LoginRepository
import com.example.learningapp.utils.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val courses: List<Course> = emptyList(),
    /** First load: nothing saved yet and a request is running. */
    val isLoading: Boolean = true,
    /** Refreshing while saved courses are already on screen. */
    val isRefreshing: Boolean = false,
    /** Last refresh failed. Shown full-screen if there are no courses, as a banner otherwise. */
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val authRepository: LoginRepository,
) : ViewModel() {

    private data class RefreshState(val inProgress: Boolean = false, val error: String? = null)

    private val refreshState = MutableStateFlow(RefreshState())
    private var refreshJob: Job? = null

    // The DB drives the list; the refresh state only adds the loading/error overlay.
    val uiState: StateFlow<HomeUiState> =
        combine(courseRepository.observeCourses(), refreshState) { courses, refresh ->
            HomeUiState(
                courses = courses,
                isLoading = courses.isEmpty() && refresh.inProgress,
                isRefreshing = courses.isNotEmpty() && refresh.inProgress,
                errorMessage = refresh.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (refreshState.value.inProgress) return
        refreshState.value = RefreshState(inProgress = true) // also clears the previous error

        refreshJob = viewModelScope.launch {
            val result = courseRepository.refresh()
            refreshState.value = RefreshState(
                inProgress = false,
                error = result.exceptionOrNull()?.toUserMessage(),
            )
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            refreshJob?.cancel() // a late response must not re-fill the cache we are about to clear
            authRepository.logout()
            courseRepository.clearCache()
            onDone()
        }
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is ApiException.Network -> "Network error. Check your connection and try again."
        is ApiException.Http -> "Couldn't load courses (server error $code)."
        else -> "Something went wrong while loading courses."
    }
}