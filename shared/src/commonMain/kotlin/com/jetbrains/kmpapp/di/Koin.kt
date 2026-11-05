package com.jetbrains.kmpapp.di

import com.jetbrains.kmpapp.data.ScheduleRepository
import com.jetbrains.kmpapp.data.VkAvatarLoader
import com.jetbrains.kmpapp.data.api.MireaScheduleApi
import com.jetbrains.kmpapp.data.config.RemoteConfigLoader
import com.jetbrains.kmpapp.data.storage.GamesStorage
import com.jetbrains.kmpapp.data.storage.LessonNotesStorage
import com.jetbrains.kmpapp.data.storage.NotesFileStorage
import com.jetbrains.kmpapp.data.storage.PlatformStorage
import com.jetbrains.kmpapp.data.storage.ScheduleStorage
import com.jetbrains.kmpapp.data.update.AppUpdateChecker
import com.jetbrains.kmpapp.screens.other.OtherViewModel
import com.jetbrains.kmpapp.screens.schedule.ScheduleViewModel
import com.jetbrains.kmpapp.data.FreeRoomsRepository
import com.jetbrains.kmpapp.screens.rooms.FreeRoomsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import com.jetbrains.kmpapp.data.TaskRepository
import com.jetbrains.kmpapp.data.sync.UnifiedSyncManager
import com.jetbrains.kmpapp.screens.tasks.TasksViewModel
import com.jetbrains.kmpapp.screens.compare.CompareScheduleViewModel
import com.jetbrains.kmpapp.screens.games.GamesViewModel
import com.jetbrains.kmpapp.screens.notes.NotesViewModel
import com.jetbrains.kmpapp.screens.services.ServicesViewModel

import com.jetbrains.kmpapp.data.power.PlatformPowerManager
import io.ktor.client.plugins.HttpTimeout

val dataModule = module {
    single {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        HttpClient {
            install(ContentNegotiation) {
                json(json, contentType = ContentType.Any)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    singleOf(::PlatformPowerManager)
    singleOf(::PlatformStorage)
    // Заметки: PlatformStorage как legacy-источник (миграция старого
    // ключа) + файловый бэкенд как основное хранилище.
    single<com.jetbrains.kmpapp.data.storage.KeyValueStore> {
        val ps = get<PlatformStorage>()
        object : com.jetbrains.kmpapp.data.storage.KeyValueStore {
            override fun saveString(key: String, value: String) = ps.saveString(key, value)
            override fun getString(key: String): String? = ps.getString(key)
            override fun remove(key: String) = ps.remove(key)
            override fun clearAll() = ps.clearAll()
        }
    }
    single { com.jetbrains.kmpapp.data.storage.NotesFileStorage() }
    singleOf(::LessonNotesStorage)
    singleOf(::UnifiedSyncManager)
    singleOf(::MireaScheduleApi)
    singleOf(::VkAvatarLoader)
    singleOf(::ScheduleStorage)
    singleOf(::GamesStorage)
    singleOf(::ScheduleRepository)
    singleOf(::AppUpdateChecker)
    singleOf(::FreeRoomsRepository)
    singleOf(::TaskRepository)
    singleOf(::RemoteConfigLoader)
}

val viewModelModule = module {
    factoryOf(::ScheduleViewModel)
    factoryOf(::OtherViewModel)
    factoryOf(::FreeRoomsViewModel)
    factoryOf(::TasksViewModel)
    factoryOf(::CompareScheduleViewModel)
    factoryOf(::NotesViewModel)
    factoryOf(::ServicesViewModel)
    factoryOf(::GamesViewModel)
}

fun initKoin() {
    startKoin {
        modules(
            dataModule,
            viewModelModule,
        )
    }
}
