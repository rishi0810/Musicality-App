package com.proj.Musicality

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.ui.theme.MusicAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            )
        )

        // Request notification permission for media controls
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        Log.d(TAG, "onCreate: starting VisitorManager.initialize()")
        Log.d(TAG, "onCreate: current browseVisitorId='${VisitorManager.browseVisitorId}' (from prefs)")
        Log.d(TAG, "onCreate: current streamVisitorId='${VisitorManager.streamVisitorId}' (from prefs)")

        lifecycleScope.launch {
            try {
                VisitorManager.initialize(applicationContext)
                Log.d(TAG, "onCreate: VisitorManager.initialize() COMPLETED")
                Log.d(TAG, "onCreate: browseVisitorId='${VisitorManager.browseVisitorId.take(20)}...'")
                Log.d(TAG, "onCreate: streamVisitorId='${VisitorManager.streamVisitorId.take(20)}...'")
            } catch (e: Exception) {
                Log.e(TAG, "onCreate: VisitorManager.initialize() FAILED", e)
            }
        }

        setContent {
            MusicAppTheme {
                MusicApp()
            }
        }
    }
}
