package com.example.learningapp.ui.screens

import com.example.learningapp.ui.screens.detailsscreen.ARG_COURSE_ID

object ScreenRoutes {
    const val ROUTE_LOGIN = "login"
    const val ROUTE_HOME = "dashboard"
    const val ROUTE_COURSE = "course/{$ARG_COURSE_ID}"
    fun courseRoute(courseId: Int) = "course/$courseId"
}