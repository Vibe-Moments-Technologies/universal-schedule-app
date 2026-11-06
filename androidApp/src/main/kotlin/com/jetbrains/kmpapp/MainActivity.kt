package com.jetbrains.kmpapp

import android.Manifest
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.LaunchedEffect
import com.jetbrains.kmpapp.data.files.FilePickersBridge
import com.jetbrains.kmpapp.data.files.PendingPick
import com.jetbrains.kmpapp.notifications.AndroidNotificationsEngine
import com.jetbrains.kmpapp.widget.ScheduleWidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Импорт JSON-семестра: результат уходит в колбэк выбора файла. */
    private val openJsonDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            val callback = PendingPick.holder
            PendingPick.holder = null
            if (uri == null) {
                callback?.invoke(null)
                return@registerForActivityResult
            }
            val text = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: Exception) {
                null
            }
            callback?.invoke(text)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Файловый обмен семестром: лаунчер должен жить в активности.
        FilePickersBridge.openDocument = openJsonDocument
        // Разрешение POST_NOTIFICATIONS (API 33+) просится только в момент,
        // когда пользователь включает тумблер напоминаний в настройках.
        // lifecycle-контекст обязателен: launch вне RESUMED кидает
        // IllegalStateException (краш «при первой установке»).
        AndroidNotificationsEngine.permissionRequester = {
            if (Build.VERSION.SDK_INT >= 33 &&
                !NotificationManagerCompat.from(this).areNotificationsEnabled() &&
                lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            App()
            // App() при композиции подключает WidgetBridge к репозиторию —
            // после этого можно отдать снапшот виджету (аналог iOS WidgetSync).
            LaunchedEffect(Unit) {
                ScheduleWidgetStore.publish(this@MainActivity)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        publishWidgetSnapshot()
    }

    override fun onPause() {
        super.onPause()
        publishWidgetSnapshot()
    }

    private fun publishWidgetSnapshot() {
        val appContext = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            ScheduleWidgetStore.publish(appContext)
        }
    }
}
