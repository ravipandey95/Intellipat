package com.example.learningapp.di

import android.content.Context
import androidx.room.Room
import com.example.learningapp.data.AppDatabase
import com.example.learningapp.data.dao.CourseDao
import com.example.learningapp.data.dao.LoginDao
import com.example.learningapp.data.network.AuthApi
import com.example.learningapp.data.network.FakeAuthApi
import com.example.learningapp.data.network.RemoteAuthApi
import com.example.learningapp.repository.CourseDetailsRepository
import com.example.learningapp.repository.CourseDetailsRepositoryImpl
import com.example.learningapp.repository.CourseRepository
import com.example.learningapp.repository.CourseRepositoryImpl
import com.example.learningapp.repository.LoginRepository
import com.example.learningapp.repository.LoginRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Interface -> implementation bindings. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: LoginRepositoryImpl): LoginRepository

    @Binds
    @Singleton
    abstract fun bindCourseRepository(impl: CourseRepositoryImpl): CourseRepository

    @Binds
    @Singleton
    abstract fun bindCourseDetailsRepository(impl: CourseDetailsRepositoryImpl): CourseDetailsRepository

    /** Retrofit-backed API. To run without a backend, bind FakeAuthApi here instead. */
    @Binds
    @Singleton
    abstract fun bindAuthApi(impl: FakeAuthApi): AuthApi
}

/** Things Hilt can't construct itself (third-party builders, framework objects). */
@Module
@InstallIn(SingletonComponent::class)
object ProvidesModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "app.db").build()

    @Provides
    fun provideSessionDao(db: AppDatabase): LoginDao = db.loginDao()

    @Provides
    fun provideCourseDao(db: AppDatabase): CourseDao = db.courseDao()

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}