package com.example.learningapp.ui.screens.dashboard

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learningapp.BuildConfig
import com.example.learningapp.data.network.FakeApiConfig
import com.example.learningapp.data.network.FakeApiScenario
import com.example.learningapp.repository.Course

/** Stateful entry point. [onContinueCourse] is the hook for the future lesson screen. */
@Composable
fun DashboardScreen(
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    onContinueCourse: (Course) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        onRefresh = viewModel::refresh,
        onLogout = { viewModel.logout(onLoggedOut) },
        onContinueCourse = onContinueCourse,
        onSimulate = { scenario ->
            FakeApiConfig.scenario = scenario // debug menu only; see FakeCoursesService
            viewModel.refresh()
        },
        modifier = modifier,
    )
}

/** Stateless UI: easy to preview and test. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onContinueCourse: (Course) -> Unit,
    onSimulate: (FakeApiScenario) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("My Courses") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (BuildConfig.DEBUG) {
                            DropdownMenuItem(
                                text = { Text("Simulate: success (200)") },
                                onClick = { menuOpen = false; onSimulate(FakeApiScenario.SUCCESS) },
                            )
                            DropdownMenuItem(
                                text = { Text("Simulate: server error (500)") },
                                onClick = { menuOpen = false; onSimulate(FakeApiScenario.SERVER_ERROR) },
                            )
                            DropdownMenuItem(
                                text = { Text("Simulate: network error") },
                                onClick = { menuOpen = false; onSimulate(FakeApiScenario.NETWORK_ERROR) },
                            )
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text("Log out") },
                            onClick = { menuOpen = false; onLogout() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                // First load, nothing saved yet
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                // Failed and nothing saved to fall back on
                state.courses.isEmpty() && state.errorMessage != null -> ErrorState(
                    message = state.errorMessage,
                    onRetry = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.courses.isEmpty() -> Text(
                    text = "No courses yet",
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> CourseList(state, onRefresh, onContinueCourse)
            }
        }
    }
}

@Composable
private fun CourseList(
    state: HomeUiState,
    onRetry: () -> Unit,
    onContinueCourse: (Course) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (state.isRefreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        // Refresh failed but saved courses exist: keep showing them, with a notice
        state.errorMessage?.let {
            ErrorBanner(message = "$it Showing saved courses.", onRetry = onRetry)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.courses, key = { it.id }) { course ->
                CourseCard(course = course, onContinue = { onContinueCourse(course) })
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: Course,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = course.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = course.instructor,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { course.progress / 100f },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${course.progress}%",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (course.lessons == 1) "1 lesson" else "${course.lessons} lessons",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onContinue) { Text("Continue") }
            }
        }
    }
}

@Composable
internal fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
internal fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/* ---------- Previews ---------- */

private val previewCourses = listOf(
    Course(1, "Python Programming", "John Smith", 65, 20),
    Course(2, "Generative AI", "Sarah Williams", 40, 16),
    Course(3, "Full Stack Development", "David Brown", 25, 28),
)

private const val PREVIEW_NETWORK_ERROR = "Network error. Check your connection and try again."

@Preview(showBackground = true, name = "Courses")
@Composable
private fun HomePreviewCourses() = MaterialTheme {
    HomeContent(HomeUiState(courses = previewCourses, isLoading = false), {}, {}, {}, {})
}

@Preview(showBackground = true, name = "Network error, saved courses shown")
@Composable
private fun HomePreviewOffline() = MaterialTheme {
    HomeContent(
        HomeUiState(courses = previewCourses, isLoading = false, errorMessage = PREVIEW_NETWORK_ERROR),
        {}, {}, {}, {},
    )
}

@Preview(showBackground = true, name = "Network error, nothing saved")
@Composable
private fun HomePreviewErrorEmpty() = MaterialTheme {
    HomeContent(HomeUiState(isLoading = false, errorMessage = PREVIEW_NETWORK_ERROR), {}, {}, {}, {})
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun HomePreviewLoading() = MaterialTheme {
    HomeContent(HomeUiState(), {}, {}, {}, {})
}