package com.alex.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alex.app.data.ChatStore
import com.alex.app.data.update.ApkInstaller
import com.alex.app.data.update.UpdateCheckResult
import com.alex.app.data.update.UpdateChecker
import com.alex.app.ui.ChatViewModel
import com.alex.app.ui.ChatScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var store: ChatStore
    private var pendingUpdate: UpdateCheckResult.Available? = null

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ChatStore(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    val vm: ChatViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ChatViewModel(store) as T
                            }
                        }
                    )
                    ChatScreen(
                        state = vm.state.collectAsState().value,
                        onSend = vm::sendText,
                        onStartVoice = vm::startVoice,
                        onStopVoice = vm::stopVoice,
                        onSettings = vm::toggleSettings,
                        onSaveSettings = { url, tok ->
                            store.serverUrl = url
                            store.token = tok
                            vm.refreshApi()
                            vm.toggleSettings()
                        },
                        serverUrl = store.serverUrl,
                        token = store.token,
                    )
                }
            }
        }

        checkForUpdate()
    }

    private fun checkForUpdate() {
        CoroutineScope(Dispatchers.IO).launch {
            when (val result = UpdateChecker().check()) {
                is UpdateCheckResult.Available -> {
                    if (result.versionName != store.lastVersion) {
                        pendingUpdate = result
                        store.lastVersion = result.versionName
                        runOnUiThread { showUpdateDialog(result) }
                    }
                }
                else -> Unit
            }
        }
    }

    private fun showUpdateDialog(update: UpdateCheckResult.Available) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Доступна версия ${update.versionName}")
            .setMessage(update.notes)
            .setPositiveButton("Обновить") { _, _ -> downloadAndInstall(update) }
            .setNegativeButton("Позже", null)
            .show()
    }

    private fun downloadAndInstall(update: UpdateCheckResult.Available) {
        CoroutineScope(Dispatchers.IO).launch {
            val installer = ApkInstaller(this@MainActivity)
            val target = File(installer.updatesDir(), "update-${update.versionName}.apk")
            val ok = UpdateChecker().downloadApk(update.apkUrl, target)
            if (ok) {
                runOnUiThread { installer.install(target) }
            }
        }
    }
}
