package com.dualsub.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.dualsub.player.ui.DualSubTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Start screen: pick a video with the system file picker. */
class MainActivity : ComponentActivity() {

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) openVideo(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DualSubTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("DualSub Player", style = MaterialTheme.typography.headlineMedium)
                        Button(
                            modifier = Modifier.padding(top = 24.dp),
                            onClick = { picker.launch(arrayOf("video/*")) }
                        ) {
                            Text("Open video")
                        }
                    }
                }
            }
        }
    }

    private fun openVideo(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }
}
