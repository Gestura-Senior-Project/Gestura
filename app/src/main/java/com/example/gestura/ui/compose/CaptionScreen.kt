package com.example.gestura.ui.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gestura.ui.vm.CaptionVm
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch

@Composable
fun CaptionScreen(vm: CaptionVm, serverBaseUrl: String) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var picked by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked = it }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ASL Caption (server landmarks → on-device model → GPT)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("Server: $serverBaseUrl", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch("video/*") }) { Text("Choose video") }
            Button(
                enabled = picked != null && !state.uploading,
                onClick = {
                    val uri = picked
                    if (uri == null) {
                        scope.launch { snack.showSnackbar("Pick a video first") }
                    } else {
                        vm.runPipeline(context, uri)
                    }
                }
            ) { Text(if (state.uploading) "Processing…" else "Caption") }
        }

        Spacer(Modifier.height(16.dp))
        state.error?.let {
            Text("Error: $it", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        if (state.glosses.isNotEmpty()) {
            Text("Glosses:", style = MaterialTheme.typography.titleSmall)
            Text(state.glosses.joinToString(" "))
            Spacer(Modifier.height(8.dp))
        }
        if (state.english.isNotBlank()) {
            Text("English:", style = MaterialTheme.typography.titleSmall)
            Text(state.english)
        }

        Spacer(Modifier.weight(1f))
        SnackbarHost(hostState = snack)
    }
}
