package com.jetbrains.kmpapp

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import com.jetbrains.kmpapp.notifications.AndroidNotificationsEngine
import com.jetbrains.kmpapp.widget.ScheduleWidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Разрешение POST_NOTIFICATIONS (API 33+) просится только в момент,
        // когда пользователь включает тумблер напоминаний в настройках.
        AndroidNotificationsEngine.permissionRequester = {
            if (Build.VERSION.SDK_INT >= 33 &&
                !NotificationManagerCompat.from(this).areNotificationsEnabled()
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

    /**
     * Снапшот для виджета публикуется при возврате и уходе в фон — так же,
     * как iOS зовёт `WidgetSync.push()` по `scenePhase`.
     */
    private fun publishWidgetSnapshot() {
        val appContext = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            ScheduleWidgetStore.publish(appContext)
        }
    }
}
