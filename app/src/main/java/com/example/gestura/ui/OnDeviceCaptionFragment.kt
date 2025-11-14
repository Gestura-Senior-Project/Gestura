package com.example.gestura.ui

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.gestura.R
import com.example.gestura.model.AslVideoPredictor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class Caption(
    val timeSec: Float,
    val text: String,
    val confidence: Float
)

/**
 * On-device ASL video → caption UI fragment.
 */
class OnDeviceCaptionFragment : Fragment() {

    private var videoView: VideoView? = null
    private var emptyState: View? = null
    private var captionOverlay: View? = null
    private var captionText: TextView? = null
    private var confidenceChip: TextView? = null
    private var buttonClear: View? = null

    private var buttonUploadPrimary: View? = null
    private var buttonUploadSecondary: View? = null
    private var buttonPlayPause: TextView? = null
    private var buttonSpeak: View? = null
    private var buttonSave: View? = null

    private var currentTranslationGroup: View? = null
    private var textCurrentTranslation: TextView? = null
    private var historyGroup: View? = null
    private var historyContainer: LinearLayout? = null

    private var videoUri: Uri? = null
    private var isPlaying: Boolean = false
    private var currentCaption: Caption? = null
    private val history = mutableListOf<String>()

    private var tts: TextToSpeech? = null

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    onVideoSelected(uri)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(requireContext()) {
            if (it == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_compose_host, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        videoView = view.findViewById(R.id.videoView)
        emptyState = view.findViewById(R.id.emptyState)
        captionOverlay = view.findViewById(R.id.captionOverlay)
        captionText = view.findViewById(R.id.captionText)
        confidenceChip = view.findViewById(R.id.confidenceChip)
        buttonClear = view.findViewById(R.id.buttonClear)

        buttonUploadPrimary = view.findViewById(R.id.buttonUploadPrimary)
        buttonUploadSecondary = view.findViewById(R.id.buttonUploadSecondary)
        buttonPlayPause = view.findViewById(R.id.buttonPlayPause) as TextView
        buttonSpeak = view.findViewById(R.id.buttonSpeak)
        buttonSave = view.findViewById(R.id.buttonSave)

        currentTranslationGroup = view.findViewById(R.id.currentTranslationGroup)
        textCurrentTranslation = view.findViewById(R.id.textCurrentTranslation)
        historyGroup = view.findViewById(R.id.historyGroup)
        historyContainer = view.findViewById(R.id.historyContainer)

        setupListeners()
    }

    private fun setupListeners() {
        buttonUploadPrimary?.setOnClickListener { launchVideoPicker() }
        buttonUploadSecondary?.setOnClickListener { launchVideoPicker() }

        buttonPlayPause?.setOnClickListener {
            togglePlayPause()
        }

        buttonClear?.setOnClickListener {
            clearVideo()
        }

        buttonSpeak?.setOnClickListener {
            speakCurrent()
        }

        buttonSave?.setOnClickListener {
            saveToHistory()
        }

        videoView?.setOnPreparedListener { mp: MediaPlayer ->
            mp.isLooping = false
        }

        videoView?.setOnCompletionListener {
            isPlaying = false
            updatePlayPauseUi()
        }
    }

    private fun launchVideoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        pickVideoLauncher.launch(intent)
    }

    private fun onVideoSelected(uri: Uri) {
        videoUri = uri
        videoView?.apply {
            visibility = View.VISIBLE
            setVideoURI(uri)
        }
        emptyState?.visibility = View.GONE
        buttonClear?.visibility = View.VISIBLE
        currentCaption = null
        updateCaptionUi()
        currentTranslationGroup?.visibility = View.GONE

        // Run ASL model in background
        classifyVideo(uri)
    }

    private fun classifyVideo(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val predictor = AslVideoPredictor(requireContext())
                val result = predictor.predictFromVideo(uri)
                predictor.close()

                val caption = Caption(
                    timeSec = 0.5f,
                    text = result.label,
                    confidence = result.confidence * 100f
                )

                withContext(Dispatchers.Main) {
                    currentCaption = caption
                    updateCaptionUi()
                    updateCurrentTranslationUi()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to run ASL model: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun togglePlayPause() {
        val vv = videoView ?: return
        if (videoUri == null) {
            Toast.makeText(requireContext(), "Select a video first", Toast.LENGTH_SHORT).show()
            return
        }

        if (isPlaying) {
            vv.pause()
            isPlaying = false
        } else {
            vv.start()
            isPlaying = true
        }
        updatePlayPauseUi()
        updateCaptionUi()
    }

    private fun updatePlayPauseUi() {
        buttonPlayPause?.text = if (isPlaying) "Pause" else "Play"
    }

    private fun clearVideo() {
        videoView?.stopPlayback()
        videoView?.visibility = View.GONE
        emptyState?.visibility = View.VISIBLE
        buttonClear?.visibility = View.GONE
        isPlaying = false
        videoUri = null
        currentCaption = null
        updatePlayPauseUi()
        updateCaptionUi()
        currentTranslationGroup?.visibility = View.GONE
    }

    private fun updateCaptionUi() {
        val caption = currentCaption
        if (caption != null && isPlaying) {
            captionOverlay?.visibility = View.VISIBLE
            captionText?.text = caption.text
            confidenceChip?.text = String.format(Locale.US, "%.1f%% confident", caption.confidence)
        } else {
            captionOverlay?.visibility = View.GONE
        }
    }

    private fun updateCurrentTranslationUi() {
        val caption = currentCaption
        if (caption != null) {
            currentTranslationGroup?.visibility = View.VISIBLE
            textCurrentTranslation?.text = caption.text +
                    "\n(" + String.format(Locale.US, "%.1f%% confident", caption.confidence) + ")"
        } else {
            currentTranslationGroup?.visibility = View.GONE
        }
    }

    private fun speakCurrent() {
        val caption = currentCaption ?: return
        val engine = tts ?: return
        engine.speak(caption.text, TextToSpeech.QUEUE_FLUSH, null, "asl-caption")
    }

    private fun saveToHistory() {
        val caption = currentCaption ?: return
        if (history.contains(caption.text)) {
            Toast.makeText(requireContext(), "Already in history", Toast.LENGTH_SHORT).show()
            return
        }
        history.add(0, caption.text)
        updateHistoryUi()
        Toast.makeText(requireContext(), "Saved translation", Toast.LENGTH_SHORT).show()
    }

    private fun updateHistoryUi() {
        val container = historyContainer ?: return
        container.removeAllViews()

        if (history.isEmpty()) {
            historyGroup?.visibility = View.GONE
            return
        }

        historyGroup?.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(requireContext())

        history.take(5).forEach { text ->
            val tv = inflater.inflate(
                android.R.layout.simple_list_item_1,
                container,
                false
            ) as TextView
            tv.text = text
            container.addView(tv)
        }
    }
}
