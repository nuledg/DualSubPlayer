package com.dualsub.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.dualsub.player.ui.DualSubTheme
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dualsub.player.player.DeviceControls
import com.dualsub.player.player.PlayerViewModel
import com.dualsub.player.ui.PlayerScreen

/** Plays a video. Also the target of "Open with" (ACTION_VIEW). */
class PlayerActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()
    private val deviceControls by lazy { DeviceControls(this) }
    private var pendingUri: Uri? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val uri = pendingUri
            pendingUri = null
            if (uri != null) {
                if (granted) vm.open(uri) else vm.error = "Storage permission is needed for this file."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = AudioManager.STREAM_MUSIC // hardware keys change media volume
        hideSystemBars()

        setContent {
            DualSubTheme {
                PlayerScreen(vm, deviceControls)
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onActivityResume()
    }

    override fun onStop() {
        super.onStop()
        vm.onActivityStop()
    }

    override fun onDestroy() {
        // Brightness goes back to the system value when we leave the player.
        deviceControls.resetBrightness()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        if (uri == vm.currentUri) return // same video (for example after rotation)

        // Try to keep access to the file. Not every provider allows it, so errors are fine.
        if (uri.scheme == "content") {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
        }

        // file:// URIs need the storage permission. content:// URIs come with their own grant.
        val permission = storagePermission()
        if (uri.scheme == "file" &&
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingUri = uri
            permissionLauncher.launch(permission)
        } else {
            vm.open(uri)
        }
    }

    private fun storagePermission() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
