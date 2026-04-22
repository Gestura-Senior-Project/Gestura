package com.example.gestura.ui

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.gestura.R
import com.example.gestura.contribute.AslSamplePipeline
import com.example.gestura.networking.LiveAslServerClient
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class Caption(
    val timeSec: Float,
    val text: String,
    val confidence: Float
)

data class RecentVideoItem(
    val uri: Uri,
    val name: String,
    val durationMs: Long
)

data class HistoryItem(
    val text: String,
    val videoUri: Uri?
)

class OnDeviceCaptionFragment : Fragment() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }
    private val liveServerClient = LiveAslServerClient()

    private companion object {
        const val MAX_RECENT_VIDEOS = 20
    }

    private var videoView: VideoView? = null
    private var previewView: PreviewView? = null
    private var emptyState: View? = null
    private var cardCamera: View? = null
    private var captionOverlay: View? = null
    private var captionText: TextView? = null
    private var confidenceChip: TextView? = null
    private var buttonClear: View? = null
    private var btnStartLive: TextView? = null

    private var buttonUploadPrimary: View? = null
    private var buttonUploadSecondary: View? = null
    private var buttonPlayPause: TextView? = null
    private var buttonSpeak: View? = null
    private var buttonSave: View? = null

    private var currentTranslationGroup: View? = null
    private var textCurrentTranslation: TextView? = null
    private var historyGroup: View? = null
    private var historyContainer: LinearLayout? = null

    private var recyclerRecentVideos: RecyclerView? = null
    private lateinit var recentVideosAdapter: RecentVideosAdapter
    private val snapHelper = LinearSnapHelper()

    private var videoUri: Uri? = null
    private var isPlaying: Boolean = false
    private var currentCaption: Caption? = null
    private val history = mutableListOf<HistoryItem>()

    private var tts: TextToSpeech? = null
    private var isLiveMode = false
    private var cameraExecutor: ExecutorService? = null
    private val liveGlosses = mutableListOf<String>()

    private val requestVideoPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loadRecentVideos()
            } else {
                Toast.makeText(requireContext(), "Permission denied. Cannot load recent videos.", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Camera permission required for live translation", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        tts = TextToSpeech(requireContext()) {
            if (it == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
        cameraExecutor?.shutdown()
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
        previewView = view.findViewById(R.id.previewView)
        emptyState = view.findViewById(R.id.emptyState)
        cardCamera = view.findViewById(R.id.cardCamera)
        captionOverlay = view.findViewById(R.id.captionOverlay)
        captionText = view.findViewById(R.id.captionText)
        confidenceChip = view.findViewById(R.id.confidenceChip)
        buttonClear = view.findViewById(R.id.buttonClear)
        btnStartLive = view.findViewById(R.id.btnStartLive)

        buttonUploadPrimary = view.findViewById(R.id.buttonUploadPrimary)
        buttonUploadSecondary = view.findViewById(R.id.buttonUploadSecondary)

        buttonPlayPause = view.findViewById(R.id.buttonPlayPause)
        buttonSpeak = view.findViewById(R.id.buttonSpeak)
        buttonSave = view.findViewById(R.id.buttonSave)

        currentTranslationGroup = view.findViewById(R.id.currentTranslationGroup)
        textCurrentTranslation = view.findViewById(R.id.textCurrentTranslation)
        historyGroup = view.findViewById(R.id.historyGroup)
        historyContainer = view.findViewById(R.id.historyContainer)

        recyclerRecentVideos = view.findViewById(R.id.recyclerRecentVideos)

        setupRecentVideosRecycler()
        setupListeners()
        updatePlayPauseUi()
        loadRecentVideosWithPermission()
    }

    private fun setupListeners() {
        cardCamera?.setOnClickListener {
            if (!isLiveMode) {
                checkCameraPermission()
            }
        }

        btnStartLive?.setOnClickListener {
            if (isLiveMode) {
                stopLiveMode()
            } else {
                checkCameraPermission()
            }
        }

        buttonPlayPause?.setOnClickListener {
            if (videoUri == null) {
                Toast.makeText(requireContext(), "Select a video below", Toast.LENGTH_SHORT).show()
            } else {
                togglePlayPause()
            }
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

        videoView?.setOnCompletionListener {
            isPlaying = false
            updatePlayPauseUi()
            updateCaptionUi()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        isLiveMode = true
        btnStartLive?.text = "Stop Live Mode"
        emptyState?.visibility = View.GONE
        videoView?.visibility = View.GONE
        previewView?.visibility = View.VISIBLE
        liveGlosses.clear()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView?.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
                if (!isLiveMode) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                
                val bitmap = previewView?.bitmap
                if (bitmap != null) {
                    liveServerClient.streamFrame(bitmap) { gloss ->
                        if (!gloss.isNullOrBlank() && (liveGlosses.isEmpty() || liveGlosses.last() != gloss)) {
                            liveGlosses.add(gloss)
                            lifecycleScope.launch(Dispatchers.Main) {
                                renderLiveTranscript()
                            }
                        }
                    }
                }
                imageProxy.close()
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun stopLiveMode() {
        isLiveMode = false
        btnStartLive?.text = "Start Live Translation"
        previewView?.visibility = View.GONE
        emptyState?.visibility = View.VISIBLE
        
        if (liveGlosses.isNotEmpty()) {
            Toast.makeText(requireContext(), "Translating...", Toast.LENGTH_SHORT).show()
            liveServerClient.finalizeSentence(liveGlosses) { sentence ->
                lifecycleScope.launch(Dispatchers.Main) {
                    currentCaption = Caption(0f, sentence, 100f)
                    updateCurrentTranslationUi()
                }
            }
        }
    }

    private fun renderLiveTranscript() {
        currentTranslationGroup?.visibility = View.VISIBLE
        textCurrentTranslation?.text = "Streaming: " + liveGlosses.joinToString(" ")
    }

    private fun togglePlayPause() {
        val vv = videoView ?: return
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
        buttonPlayPause?.text = when {
            videoUri == null -> "Select Video Below"
            isPlaying -> "Pause"
            else -> "Play"
        }
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
            textCurrentTranslation?.text = caption.text
        }
    }

    private fun speakCurrent() {
        val caption = currentCaption ?: return
        tts?.speak(caption.text, TextToSpeech.QUEUE_FLUSH, null, "asl-caption")
    }

    private fun saveToHistory() {
        val caption = currentCaption ?: return
        if (history.none { it.text == caption.text }) {
            history.add(0, HistoryItem(caption.text, videoUri))
            updateHistoryUi()
            Toast.makeText(requireContext(), "Saved to history", Toast.LENGTH_SHORT).show()
        }
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
        history.take(5).forEach { item ->
            val tv = inflater.inflate(android.R.layout.simple_list_item_1, container, false) as TextView
            tv.text = if (item.videoUri != null) "🎬 " + item.text else "🎤 " + item.text
            tv.setOnClickListener {
                if (item.videoUri != null) {
                    onVideoSelected(item.videoUri)
                }
            }
            container.addView(tv)
        }
    }

    private fun setupRecentVideosRecycler() {
        recentVideosAdapter = RecentVideosAdapter(
            onClick = { item ->
                onVideoSelected(item.uri)
            }
        )
        recyclerRecentVideos?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        recyclerRecentVideos?.adapter = recentVideosAdapter
        snapHelper.attachToRecyclerView(recyclerRecentVideos)
    }

    private fun onVideoSelected(uri: Uri) {
        stopLiveMode()
        videoUri = uri
        isPlaying = false
        updatePlayPauseUi()
        videoView?.apply {
            visibility = View.VISIBLE
            setVideoURI(uri)
            setOnPreparedListener { mp ->
                mp.seekTo(1)
            }
        }
        emptyState?.visibility = View.GONE
        buttonClear?.visibility = View.VISIBLE
        classifyVideo(uri)
    }

    private fun classifyVideo(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pipeline = AslSamplePipeline(requireContext())
                val sample = try {
                    pipeline.run(word = "unknown", videoUri = uri)
                } finally {
                    pipeline.close()
                }

                val caption = Caption(timeSec = 0.5f, text = sample.predictedLabel, confidence = sample.confidence)

                withContext(Dispatchers.Main) {
                    currentCaption = caption
                    updateCurrentTranslationUi()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadRecentVideosWithPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            loadRecentVideos()
        } else {
            requestVideoPermissionLauncher.launch(permission)
        }
    }

    private fun loadRecentVideos() {
        lifecycleScope.launch(Dispatchers.IO) {
            val videos = mutableListOf<RecentVideoItem>()
            val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION)
            val cursor = requireContext().contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                var count = 0
                while (it.moveToNext() && count < MAX_RECENT_VIDEOS) {
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it.getLong(idCol))
                    videos.add(RecentVideoItem(uri, it.getString(nameCol), it.getLong(durationCol)))
                    count++
                }
            }
            withContext(Dispatchers.Main) {
                recentVideosAdapter.submitList(videos)
            }
        }
    }

    class RecentVideosAdapter(private val onClick: (RecentVideoItem) -> Unit) : RecyclerView.Adapter<RecentVideosAdapter.VideoViewHolder>() {
        private val items = mutableListOf<RecentVideoItem>()
        fun submitList(newItems: List<RecentVideoItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VideoViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_recent_video, parent, false))
        override fun onBindViewHolder(holder: VideoViewHolder, position: Int) = holder.bind(items[position], onClick)
        override fun getItemCount() = items.size
        class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            fun bind(item: RecentVideoItem, onClick: (RecentVideoItem) -> Unit) {
                itemView.findViewById<TextView>(R.id.textName).text = item.name
                itemView.setOnClickListener { onClick(item) }
                
                val videoView = itemView.findViewById<VideoView>(R.id.itemVideoView)
                videoView.setVideoURI(item.uri)
                videoView.seekTo(1)
            }
        }
    }
}
