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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AvatarFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var videoView: VideoView? = null
    private var input: EditText? = null
    private var submit: Button? = null
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

        videoView = view.findViewById(R.id.dictionaryVideoView)
        input = view.findViewById(R.id.etAvatarInput)
        submit = view.findViewById(R.id.btnSubmitAvatar)
        statusText = view.findViewById(R.id.txtAvatarStatus)
        placeholder = view.findViewById(R.id.avatarPlaceholder)
        loadingProgress = view.findViewById(R.id.avatarLoadingProgress)

        submit?.setOnClickListener {
            val text = input?.text?.toString()?.trim() ?: ""
            if (text.isEmpty()) {
                input?.error = "Please enter text"
                return@setOnClickListener
            }
            processInput(text)
        }

        videoView?.setOnErrorListener { _, _, _ ->
            showError("Error playing video")
            true
        }
    }

    private fun processInput(input: String) {
        val words = input.lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (words.isEmpty()) return

        lifecycleScope.launch {
            setLoading(true)
            statusText?.text = "Processing dictionary lookup..."
            
            val videoUris = mutableListOf<Uri>()
            val foundWords = mutableListOf<String>()

            for (word in words) {
                try {
                    val docId = word.replace("[^a-z0-9_]".toRegex(), "")
                    
                    // 1. Try curated reference collection (based on your screenshot)
                    var doc = db.collection("asl_reference").document(docId).get().await()
                    var finalPath: String? = null

                    if (doc.exists()) {
                        finalPath = doc.getString("storagepath") // lowercase as seen in your screenshot
                    } else {
                        // 2. Fallback to community accepted signs
                        doc = db.collection("asl_accepted")
                            .whereEqualTo("word", word)
                            .limit(1)
                            .get()
                            .await()
                            .documents.firstOrNull()
                        
                        finalPath = doc?.getString("videoStoragePath")
                    }

                    if (!finalPath.isNullOrEmpty()) {
                        val ref = storage.getReference(finalPath)
                        videoUris.add(ref.downloadUrl.await())
                        foundWords.add(word)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Dictionary", "Lookup failed for $word: ${e.message}")
                }
            }

            if (videoUris.isNotEmpty()) {
                playVideoSequence(videoUris, foundWords)
            } else {
                showError("No signs found in dictionary for those words")
                setLoading(false)
            }
        }
    }

    private fun playVideoSequence(uris: List<Uri>, words: List<String>) {
        var currentIndex = 0
        
        fun playNext() {
            if (currentIndex < uris.size) {
                placeholder?.isVisible = false
                videoView?.apply {
                    isVisible = true
                    setVideoURI(uris[currentIndex])
                    statusText?.text = "Signing: ${words[currentIndex]}"
                    setOnPreparedListener { start() }
                    setOnCompletionListener {
                        currentIndex++
                        playNext()
                    }
                }
            } else {
                statusText?.text = "Finished signing sentence"
                setLoading(false)
            }
        }

        playNext()
    }

    private fun setLoading(isLoading: Boolean) {
        loadingProgress?.isVisible = isLoading
        submit?.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        statusText?.text = message
        placeholder?.isVisible = true
        videoView?.isVisible = false
        videoView?.stopPlayback()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        videoView?.stopPlayback()
        videoView = null
    }
}
