package com.example.gestura

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.gestura.networking.AvatarService

class AvatarFragment : Fragment() {

    private val avatarService = AvatarService()
    private var videoView: VideoView? = null
    private var placeholderIcon: ImageView? = null
    private var statusText: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_avatar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val input = view.findViewById<EditText>(R.id.etAvatarInput)
        statusText = view.findViewById(R.id.txtAvatarStatus)
        val submit = view.findViewById<Button>(R.id.btnSubmitAvatar)
        
        // Setup VideoView in the layout (reusing existing FrameLayout)
        val previewBox = view.findViewById<ViewGroup>(R.id.avatarPreviewBox)
        placeholderIcon = view.findViewById(R.id.imgAvatarPreview)

        videoView = VideoView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVisible = false
        }
        previewBox.addView(videoView)

        submit.setOnClickListener {
            val text = input.text.toString().trim()

            if (text.isEmpty()) {
                input.error = "Please enter text"
                return@setOnClickListener
            }

            startGeneration(text)
        }
    }

    private fun startGeneration(text: String) {
        statusText?.text = "Generating ASL Avatar..."
        placeholderIcon?.isVisible = true
        videoView?.isVisible = false
        videoView?.stopPlayback()

        avatarService.generateAvatar(text, object : AvatarService.AvatarCallback {
            override fun onSuccess(videoUrl: String) {
                activity?.runOnUiThread {
                    statusText?.text = "Animation Ready"
                    playAvatarVideo(videoUrl)
                }
            }

            override fun onError(message: String) {
                activity?.runOnUiThread {
                    statusText?.text = "Error: $message"
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun playAvatarVideo(url: String) {
        placeholderIcon?.isVisible = false
        videoView?.apply {
            isVisible = true
            setVideoURI(Uri.parse(url))
            setOnPreparedListener { mp ->
                mp.isLooping = true
                start()
            }
            setOnErrorListener { _, _, _ ->
                statusText?.text = "Error playing video"
                placeholderIcon?.isVisible = true
                false
            }
        }
    }
}
