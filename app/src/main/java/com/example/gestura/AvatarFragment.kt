package com.example.gestura

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.gestura.ui.AvatarViewModel
import kotlinx.coroutines.flow.collectLatest

class AvatarFragment : Fragment(R.layout.fragment_avatar) {

    private val vm: AvatarViewModel by viewModels()

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var input: EditText
    private lateinit var btn: Button
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        input = view.findViewById(R.id.inputText)
        btn = view.findViewById(R.id.generateBtn)
        progress = view.findViewById(R.id.progress)
        errorText = view.findViewById(R.id.errorText)
        playerView = view.findViewById(R.id.playerView)

        btn.setOnClickListener {
            vm.generate(input.text.toString())
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.state.collectLatest { s ->
                progress.visibility = if (s.loading) View.VISIBLE else View.GONE

                if (s.error != null) {
                    errorText.text = s.error
                    errorText.visibility = View.VISIBLE
                } else {
                    errorText.visibility = View.GONE
                }

                s.videoUrl?.let { url ->
                    ensurePlayer()
                    player?.setMediaItem(MediaItem.fromUri(url))
                    player?.prepare()
                    player?.playWhenReady = true
                }
            }
        }
    }

    private fun ensurePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(requireContext()).build()
            playerView.player = player
        }
    }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
        player?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playerView.player = null
        player?.release()
        player = null
    }
}
