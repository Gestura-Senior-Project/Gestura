package com.example.gestura.ui

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.gestura.R
import com.example.gestura.contribute.AslSamplePipeline
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

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

class OnDeviceCaptionFragment : Fragment() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }

    private companion object {
        const val REJECT_THRESHOLD = 85f
        const val REJECT_COLLECTION = "asl_review"
        const val REJECT_VIDEO_FOLDER = "asl_rejected_videos"
        const val MAX_RECENT_VIDEOS = 20
    }

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

    private var recyclerRecentVideos: RecyclerView? = null
    private lateinit var recentVideosAdapter: RecentVideosAdapter
    private val snapHelper = LinearSnapHelper()
    private var currentAutoPlayPosition = RecyclerView.NO_POSITION

    private var videoUri: Uri? = null
    private var isPlaying: Boolean = false
    private var currentCaption: Caption? = null
    private val history = mutableListOf<String>()

    private var tts: TextToSpeech? = null

    private val requestVideoPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loadRecentVideos()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permission denied. Cannot load recent videos.",
                    Toast.LENGTH_SHORT
                ).show()
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

    private fun setupRecentVideosRecycler() {
        recentVideosAdapter = RecentVideosAdapter(
            onClick = { item ->
                onVideoSelected(item.uri)
            }
        )

        recyclerRecentVideos?.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        recyclerRecentVideos?.adapter = recentVideosAdapter
        snapHelper.attachToRecyclerView(recyclerRecentVideos)

        recyclerRecentVideos?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    autoPlayCenteredItem()
                }
            }
        })
    }

    private fun setupListeners() {
        buttonUploadPrimary?.visibility = View.GONE
        buttonUploadSecondary?.visibility = View.GONE

        buttonPlayPause?.setOnClickListener {
            if (videoUri == null) {
                Toast.makeText(
                    requireContext(),
                    "Select a video from the recent videos row below",
                    Toast.LENGTH_SHORT
                ).show()
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

        videoView?.setOnPreparedListener { mp: MediaPlayer ->
            mp.isLooping = false
        }

        videoView?.setOnCompletionListener {
            isPlaying = false
            updatePlayPauseUi()
            updateCaptionUi()
        }
    }

    private fun loadRecentVideosWithPermission() {
        val permission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                Manifest.permission.READ_MEDIA_VIDEO
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                Manifest.permission.READ_EXTERNAL_STORAGE
            else -> null
        }

        if (permission == null) {
            loadRecentVideos()
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            loadRecentVideos()
        } else {
            requestVideoPermissionLauncher.launch(permission)
        }
    }

    private fun loadRecentVideos() {
        lifecycleScope.launch(Dispatchers.IO) {
            val videos = mutableListOf<RecentVideoItem>()

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED
            )

            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            val cursor = requireContext().contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                var count = 0
                while (it.moveToNext() && count < MAX_RECENT_VIDEOS) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "video.mp4"
                    val duration = it.getLong(durationCol)

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    videos.add(
                        RecentVideoItem(
                            uri = uri,
                            name = name,
                            durationMs = duration
                        )
                    )
                    count++
                }
            }

            withContext(Dispatchers.Main) {
                recentVideosAdapter.submitList(videos)
                recyclerRecentVideos?.post {
                    autoPlayCenteredItem()
                }

                if (videos.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "No recent videos found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun onVideoSelected(uri: Uri) {
        videoUri = uri

        videoView?.apply {
            visibility = View.VISIBLE
            setVideoURI(uri)
            seekTo(1)
        }

        emptyState?.visibility = View.GONE
        buttonClear?.visibility = View.VISIBLE
        currentCaption = null
        isPlaying = false

        updatePlayPauseUi()
        updateCaptionUi()
        currentTranslationGroup?.visibility = View.GONE

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

                val caption = Caption(
                    timeSec = 0.5f,
                    text = sample.predictedLabel,
                    confidence = sample.confidence
                )

                if (sample.confidence < REJECT_THRESHOLD) {
                    saveRejectedSample(sample)
                }

                withContext(Dispatchers.Main) {
                    currentCaption = caption
                    updateCaptionUi()
                    updateCurrentTranslationUi()

                    if (sample.confidence < REJECT_THRESHOLD) {
                        Toast.makeText(
                            requireContext(),
                            "Low confidence sample saved to rejected table",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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

    private suspend fun saveRejectedSample(sample: AslSamplePipeline.SampleResult) {
        val (storagePath, downloadUrl) = uploadRejectedVideo(sample.videoUri)

        val rejectDoc = hashMapOf(
            "word" to sample.word,
            "predictedLabel" to sample.predictedLabel,
            "confidence" to sample.confidence,
            "videoUrl" to downloadUrl,
            "videoStoragePath" to storagePath,
            "keypoints" to sample.keypoints.toList(),
            "status" to "rejected",
            "reason" to "LOW_CONFIDENCE",
            "createdAt" to FieldValue.serverTimestamp()
        )

        firestore.collection(REJECT_COLLECTION)
            .add(rejectDoc)
            .await()
    }

    private suspend fun uploadRejectedVideo(videoUri: Uri): Pair<String, String> {
        val ext = requireContext().contentResolver.getType(videoUri)
            ?.substringAfterLast("/")
            ?.ifBlank { "mp4" }
            ?: "mp4"

        val fileName = "${UUID.randomUUID()}.$ext"
        val storagePath = "$REJECT_VIDEO_FOLDER/$fileName"
        val ref = storage.reference.child(storagePath)

        ref.putFile(videoUri).await()
        val downloadUrl = ref.downloadUrl.await().toString()

        return storagePath to downloadUrl
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
            confidenceChip?.text =
                String.format(Locale.US, "%.1f%% confident", caption.confidence)
        } else {
            captionOverlay?.visibility = View.GONE
        }
    }

    private fun updateCurrentTranslationUi() {
        val caption = currentCaption
        if (caption != null) {
            currentTranslationGroup?.visibility = View.VISIBLE
            textCurrentTranslation?.text =
                caption.text + "\n(" +
                        String.format(Locale.US, "%.1f%% confident", caption.confidence) + ")"
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

    private fun autoPlayCenteredItem() {
        val recycler = recyclerRecentVideos ?: return
        val layoutManager = recycler.layoutManager ?: return
        val snapView = snapHelper.findSnapView(layoutManager) ?: return
        val position = recycler.getChildAdapterPosition(snapView)

        if (position == RecyclerView.NO_POSITION) return
        if (currentAutoPlayPosition == position) return

        recentVideosAdapter.stopAllPlayersExcept(recycler, position)
        currentAutoPlayPosition = position
        recentVideosAdapter.playAtPosition(recycler, position)
    }

    override fun onPause() {
        super.onPause()
        recyclerRecentVideos?.let {
            recentVideosAdapter.stopAllPlayersExcept(it, RecyclerView.NO_POSITION)
        }
        videoView?.pause()
        isPlaying = false
        updatePlayPauseUi()
    }

    override fun onDestroyView() {
        recyclerRecentVideos?.let {
            recentVideosAdapter.stopAllPlayersExcept(it, RecyclerView.NO_POSITION)
        }
        videoView?.stopPlayback()
        super.onDestroyView()
    }

    class RecentVideosAdapter(
        private val onClick: (RecentVideoItem) -> Unit
    ) : RecyclerView.Adapter<RecentVideosAdapter.VideoViewHolder>() {

        private val items = mutableListOf<RecentVideoItem>()

        fun submitList(newItems: List<RecentVideoItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recent_video, parent, false)
            return VideoViewHolder(view)
        }

        override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
            holder.bind(items[position], onClick)
        }

        override fun onViewRecycled(holder: VideoViewHolder) {
            holder.stopPreview()
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int = items.size

        fun playAtPosition(recyclerView: RecyclerView, position: Int) {
            val holder = recyclerView.findViewHolderForAdapterPosition(position) as? VideoViewHolder
            holder?.startPreview()
        }

        fun stopAllPlayersExcept(recyclerView: RecyclerView, keepPosition: Int) {
            for (i in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(i)
                val holder = recyclerView.getChildViewHolder(child) as? VideoViewHolder ?: continue
                if (holder.bindingAdapterPosition != keepPosition) {
                    holder.stopPreview()
                }
            }
        }

        inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val itemVideoView: VideoView = itemView.findViewById(R.id.itemVideoView)
            private val itemVideoOverlay: View = itemView.findViewById(R.id.itemVideoOverlay)
            private val textName: TextView = itemView.findViewById(R.id.textName)
            private val textDuration: TextView = itemView.findViewById(R.id.textDuration)

            private var boundItem: RecentVideoItem? = null
            private var isPrepared = false

            fun bind(item: RecentVideoItem, onClick: (RecentVideoItem) -> Unit) {
                boundItem = item
                isPrepared = false

                textName.text = item.name
                textDuration.text = formatDuration(item.durationMs)

                itemVideoView.stopPlayback()
                itemVideoView.setVideoURI(item.uri)

                itemVideoView.setOnPreparedListener { mp ->
                    isPrepared = true
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    itemVideoView.seekTo(1)
                }

                itemVideoView.setOnClickListener {
                    onClick(item)
                }

                itemView.setOnClickListener {
                    onClick(item)
                }

                itemVideoOverlay.visibility = View.VISIBLE
            }

            fun startPreview() {
                val item = boundItem ?: return

                if (!isPrepared) {
                    itemVideoView.setVideoURI(item.uri)
                    itemVideoView.setOnPreparedListener { mp ->
                        isPrepared = true
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                        itemVideoOverlay.visibility = View.GONE
                        itemVideoView.start()
                    }
                } else {
                    itemVideoOverlay.visibility = View.GONE
                    itemVideoView.start()
                }
            }

            fun stopPreview() {
                try {
                    if (itemVideoView.isPlaying) {
                        itemVideoView.pause()
                    }
                    itemVideoView.seekTo(1)
                    itemVideoOverlay.visibility = View.VISIBLE
                } catch (_: Exception) {
                }
            }

            private fun formatDuration(durationMs: Long): String {
                val totalSeconds = durationMs / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                return String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
        }
    }
}