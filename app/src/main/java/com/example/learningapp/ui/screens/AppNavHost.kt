package com.example.learningapp.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.learningapp.ui.screens.ScreenRoutes.ROUTE_COURSE
import com.example.learningapp.ui.screens.ScreenRoutes.ROUTE_LOGIN
import com.example.learningapp.ui.screens.ScreenRoutes.ROUTE_HOME
import com.example.learningapp.ui.screens.ScreenRoutes.courseRoute
import com.example.learningapp.ui.screens.loginscreen.LoginScreen
import com.example.learningapp.ui.screens.dashboard.DashboardScreen
import com.example.learningapp.ui.screens.detailsscreen.ARG_COURSE_ID
import com.example.learningapp.ui.screens.detailsscreen.CourseDetailsScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_LOGIN) {
        composable(ROUTE_LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(ROUTE_HOME) {
                        popUpTo(ROUTE_LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(ROUTE_HOME) {
            DashboardScreen(
                onLoggedOut = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_HOME) { inclusive = true }
                    }
                },
                onContinueCourse = { course ->
                    navController.navigate(courseRoute(course.id))
                },
            )
        }
        composable(
            route = ROUTE_COURSE,
            arguments = listOf(navArgument(ARG_COURSE_ID) { type = NavType.IntType }),
        ) {
            CourseDetailsScreen(onBack = { navController.popBackStack() })
        }
    }
}