package com.example.gestura

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AvatarFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var input: EditText? = null
    private var submit: TextView? = null
    private var statusText: TextView? = null
    private var placeholder: View? = null
    private var loadingProgress: ProgressBar? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_avatar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerView = view.findViewById(R.id.dictionaryPlayerView)
        input = view.findViewById(R.id.etAvatarInput)
        submit = view.findViewById(R.id.btnSubmitAvatar)
        statusText = view.findViewById(R.id.txtAvatarStatus)
        placeholder = view.findViewById(R.id.avatarPlaceholder)
        loadingProgress = view.findViewById(R.id.avatarLoadingProgress)

        setupPlayer()

        submit?.setOnClickListener {
            val text = input?.text?.toString()?.trim() ?: ""
            if (text.isEmpty()) {
                input?.error = "Please enter text"
                return@setOnClickListener
            }
            processInput(text)
        }
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(requireContext()).build()
        playerView?.player = player
        playerView?.useController = false // Hide controls for "animation" feel

        player?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Update text when the next word starts playing
                val currentWord = mediaItem?.mediaMetadata?.title?.toString()
                if (currentWord != null) {
                    statusText?.text = "Signing: $currentWord"
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    statusText?.text = "Finished signing sentence"
                    setLoading(false)
                }
            }
        })
    }

    private fun processInput(input: String) {
        val words = input.lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (words.isEmpty()) return

        lifecycleScope.launch {
            setLoading(true)
            statusText?.text = "Processing sentence..."
            
            val mediaItems = mutableListOf<MediaItem>()

            for (word in words) {
                try {
                    val docId = word.replace("[^a-z0-9_]".toRegex(), "")
                    
                    // Try curated reference first
                    var doc = db.collection("asl_reference").document(docId).get().await()
                    var path: String? = doc.getString("storagepath")

                    if (path == null) {
                        // Fallback to community accepted
                        val query = db.collection("asl_accepted")
                            .whereEqualTo("word", word)
                            .limit(1)
                            .get()
                            .await()
                        path = query.documents.firstOrNull()?.getString("videoStoragePath")
                    }

                    if (!path.isNullOrEmpty()) {
                        val url = storage.getReference(path).downloadUrl.await()
                        val mediaItem = MediaItem.Builder()
                            .setUri(url)
                            .setMediaId(word)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(word)
                                    .build()
                            )
                            .build()
                        mediaItems.add(mediaItem)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ExoPlayer", "Error loading $word: ${e.message}")
                }
            }

            if (mediaItems.isNotEmpty()) {
                placeholder?.isVisible = false
                playerView?.isVisible = true
                player?.setMediaItems(mediaItems)
                player?.prepare()
                player?.play()
            } else {
                showError("No signs found in dictionary")
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        loadingProgress?.isVisible = isLoading
        submit?.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        statusText?.text = message
        placeholder?.isVisible = true
        playerView?.isVisible = false
        player?.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
    }
}
