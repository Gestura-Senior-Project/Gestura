package com.example.gestura.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.gestura.BuildConfig
import com.example.gestura.network.ApiClient
import com.example.gestura.repo.LandmarkRepo
import com.example.gestura.model.AslClassifier
import com.example.gestura.ui.compose.CaptionScreen
import com.example.gestura.ui.vm.CaptionVm
import com.example.gestura.ui.vm.CaptionVmFactory

class OnDeviceCaptionFragment : Fragment() {

    // NOTE: point this at your running server
    private val serverBaseUrl = "http://10.0.2.2:5050/" // emulator → host; phone on Wi-Fi: use your LAN IP

    private val vm: CaptionVm by viewModels {
        // Simple manual DI using a factory so the VM works across config changes
        val api = ApiClient.landmarks(serverBaseUrl)
        val repo = LandmarkRepo(api)

        // Load labels once from assets
        val labels = requireContext().assets.open("labels/gloss_labels.txt")
            .bufferedReader().readLines()

        val labelFor: (Int) -> String = { id -> labels.getOrElse(id) { "UNK" } }
        val classifierProvider = { AslClassifier(requireContext(), windowT = 32) }

        CaptionVmFactory(
            repo = repo,
            openAiKey = { BuildConfig.OPENAI_API_KEY },
            labelFor = labelFor,
            classifierProvider = classifierProvider
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    Surface {
                        CaptionTab(vm = vm, serverBaseUrl = serverBaseUrl.removeSuffix("/"))
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionTab(vm: CaptionVm, serverBaseUrl: String) {
    // This composable just hosts the screen UI for the tab
    CaptionScreen(vm = vm, serverBaseUrl = serverBaseUrl)
}
