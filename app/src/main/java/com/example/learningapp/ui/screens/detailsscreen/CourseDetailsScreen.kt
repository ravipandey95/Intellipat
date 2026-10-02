package com.example.learningapp.ui.screens.detailsscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learningapp.repository.Course
import com.example.learningapp.repository.Lesson
import com.example.learningapp.ui.screens.dashboard.ErrorBanner
import com.example.learningapp.ui.screens.dashboard.ErrorState
import androidx.compose.material3.RadioButton

/** Stateful entry point. */
@Composable
fun CourseDetailsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CourseDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    CourseDetailsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onCompleteLesson = viewModel::onCompleteLesson,
        modifier = modifier,
    )
}

/** Stateless UI: easy to preview and test. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailsContent(
    state: CourseDetailsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCompleteLesson: (lessonId: Int, isCompleted: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Course Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (state.isRefreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            // Refresh failed but saved lessons exist: keep showing them, with a notice
            if (state.lessons.isNotEmpty()) {
                state.errorMessage?.let {
                    ErrorBanner(message = "$it Showing saved lessons.", onRetry = onRefresh)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.course?.let { course ->
                    item(key = "header") { CourseHeader(course, state.lessons) }
                }

                when {
                    state.isLoading -> item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }

                    state.course == null -> item(key = "missing") {
                        Text("Course not found", Modifier.padding(16.dp))
                    }

                    // Failed and no saved lessons to fall back on
                    state.lessons.isEmpty() && state.errorMessage != null -> item(key = "error") {
                        ErrorState(
                            message = state.errorMessage,
                            onRetry = onRefresh,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    state.lessons.isEmpty() -> item(key = "empty") {
                        Text("No lessons yet", Modifier.padding(16.dp))
                    }

                    else -> items(state.lessons, key = { it.id }) { lesson ->
                        LessonRow(
                            lesson = lesson,
                            isUpdating = state.updatingLessonId == lesson.id,
                            canComplete = state.updatingLessonId == null,
                            onComplete = { isCompleted ->
                                onCompleteLesson(lesson.id, isCompleted) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseHeader(course: Course, lessons: List<Lesson>) {
    val completed = lessons.count { it.isCompleted }

    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            text = course.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = course.instructor,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { course.progress / 100f },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${course.progress}%",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (lessons.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$completed of ${lessons.size} lessons completed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Lessons",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LessonRow(
    lesson: Lesson,
    isUpdating: Boolean,
    canComplete: Boolean,
    onComplete: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = lesson.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (lesson.isCompleted) "✓ Completed" else "○ Pending",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (lesson.isCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            when {
                isUpdating -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
                else -> RadioButton(
                    selected = lesson.isCompleted,
                    onClick = { onComplete(!lesson.isCompleted) },
                    enabled = canComplete, )
            }
        }
    }
}

/* ---------- Previews ---------- */

private val previewCourse = Course(1, "Python Programming", "John Smith", progress = 60, lessons = 5)

private val previewLessons = listOf(
    Lesson(101, "Introduction", true),
    Lesson(102, "Variables & Data Types", true),
    Lesson(103, "Control Flow", true),
    Lesson(104, "Functions", false),
    Lesson(105, "Object-Oriented Programming", false),
)

private const val PREVIEW_NETWORK_ERROR = "Network error. Check your connection and try again."

@Preview(showBackground = true, name = "Lessons")
@Composable
private fun DetailsPreviewLessons() = MaterialTheme {
    CourseDetailsContent(
        CourseDetailsUiState(course = previewCourse, lessons = previewLessons, isLoading = false),
        SnackbarHostState(), {}, {}, {_,_ ->},
    )
}

@Preview(showBackground = true, name = "Updating a lesson")
@Composable
private fun DetailsPreviewUpdating() = MaterialTheme {
    CourseDetailsContent(
        CourseDetailsUiState(
            course = previewCourse, lessons = previewLessons, isLoading = false, updatingLessonId = 104,
        ),
        SnackbarHostState(), {}, {}, {_,_ ->},
    )
}

@Preview(showBackground = true, name = "Offline, saved lessons shown")
@Composable
private fun DetailsPreviewOffline() = MaterialTheme {
    CourseDetailsContent(
        CourseDetailsUiState(
            course = previewCourse, lessons = previewLessons, isLoading = false, errorMessage = PREVIEW_NETWORK_ERROR,
        ),
        SnackbarHostState(), {}, {}, {_,_ ->},
    )
}

@Preview(showBackground = true, name = "Offline, nothing saved")
@Composable
private fun DetailsPreviewErrorEmpty() = MaterialTheme {
    CourseDetailsContent(
        CourseDetailsUiState(course = previewCourse, isLoading = false, errorMessage = PREVIEW_NETWORK_ERROR),
        SnackbarHostState(), {}, {}, {_,_ ->},
    )
}