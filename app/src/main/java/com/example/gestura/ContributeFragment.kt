package com.example.gestura

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.gestura.contribute.AslSamplePipeline
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

data class RecentVideoItem(
    val uri: Uri,
    val name: String,
    val durationMs: Long
)

class ContributeFragment : Fragment() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val functions by lazy {
        // same region as in index.ts
        FirebaseFunctions.getInstance("us-central1")
    }

    private val firestore by lazy { Firebase.firestore }
    private val storage by lazy { Firebase.storage }

    // --- UI refs ---
    private var editWord: EditText? = null
    private var buttonPickVideo: Button? = null
    private var buttonSubmit: Button? = null
    private var buttonShowReference: Button? = null
    private var videoView: VideoView? = null
    private var referenceVideoView: VideoView? = null
    private var statusText: TextView? = null
    private var progress: ProgressBar? = null

    // stats header
    private var txtTotalCount: TextView? = null
    private var txtApprovedCount: TextView? = null
    private var txtPendingCount: TextView? = null

    // contributions list
    private var contributionsContainer: LinearLayout? = null

    // Recent videos (same as ASL tab)
    private var recyclerRecentVideos: RecyclerView? = null
    private lateinit var recentVideosAdapter: RecentVideosAdapter
    private val snapHelper = LinearSnapHelper()

    private var selectedVideoUri: Uri? = null

    private companion object {
        const val MAX_RECENT_VIDEOS = 20
    }

    private val pickVideoLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    try {
                        requireContext().contentResolver
                            .takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                    } catch (e: SecurityException) {
                        Log.e("ContributeFragment", "Failed to take persistable URI permission", e)
                    }
                    onVideoSelected(uri)
                }
            }
        }

    private val requestVideoPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loadRecentVideos()
            } else {
                Toast.makeText(requireContext(), "Permission denied. Cannot load recent videos.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_contribute,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        // base UI
        editWord = view.findViewById(R.id.editWord)
        buttonPickVideo = view.findViewById(R.id.buttonPickVideo)
        buttonSubmit = view.findViewById(R.id.buttonSubmit)
        buttonShowReference = view.findViewById(R.id.buttonShowReference)
        videoView = view.findViewById(R.id.videoView)
        referenceVideoView = view.findViewById(R.id.referenceVideoView)
        statusText = view.findViewById(R.id.statusText)
        progress = view.findViewById(R.id.progressBar)

        // stats header
        txtTotalCount = view.findViewById(R.id.textTotalCount)
        txtApprovedCount = view.findViewById(R.id.textApprovedCount)
        txtPendingCount = view.findViewById(R.id.textPendingCount)

        // list container
        contributionsContainer = view.findViewById(R.id.contributionsContainer)

        // Recent videos
        recyclerRecentVideos = view.findViewById(R.id.recyclerRecentVideos)

        buttonPickVideo?.setOnClickListener { launchVideoPicker() }
        buttonSubmit?.setOnClickListener { submitSample() }
        buttonShowReference?.setOnClickListener {
            val word = editWord?.text?.toString()?.trim()
            if (word.isNullOrEmpty()) {
                editWord?.error = "Enter a word first"
            } else {
                loadReferenceVideoFor(word)
            }
        }

        setupRecentVideosRecycler()
        loadRecentVideosWithPermission()
        loadUserContributions()
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

    // --------------------------------------------------------------------
    //  Video picking / preview
    // --------------------------------------------------------------------
    private fun launchVideoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        pickVideoLauncher.launch(intent)
    }

    private fun onVideoSelected(uri: Uri) {
        selectedVideoUri = uri
        videoView?.apply {
            visibility = View.VISIBLE
            setVideoURI(uri)
            setOnPreparedListener { mp ->
                mp.seekTo(1)
            }
        }
        statusText?.text = "Video selected"
    }

    private fun setLoading(loading: Boolean) {
        progress?.visibility = if (loading) View.VISIBLE else View.GONE
        buttonSubmit?.isEnabled = !loading
        buttonPickVideo?.isEnabled = !loading
        buttonShowReference?.isEnabled = !loading
    }

    // --------------------------------------------------------------------
    //  Reference video
    // --------------------------------------------------------------------
    private fun normalizeDocId(word: String): String =
        word.trim()
            .lowercase()
            .replace("\\s+".toRegex(), "_")

    private fun loadReferenceVideoFor(displayWord: String) {
        val docId = normalizeDocId(displayWord)

        lifecycleScope.launch {
            setLoading(true)
            try {
                statusText?.text = "Loading reference for \"$displayWord\"…"

                val doc = firestore
                    .collection("asl_reference")
                    .document(docId)
                    .get()
                    .await()

                if (!doc.exists()) {
                    referenceVideoView?.apply {
                        stopPlayback()
                        visibility = View.GONE
                    }
                    statusText?.text =
                        "No reference video for \"$displayWord\" yet."
                    return@launch
                }

                val storagePath = doc.getString("storagePath")

                if (storagePath.isNullOrEmpty()) {
                    referenceVideoView?.apply {
                        stopPlayback()
                        visibility = View.GONE
                    }
                    statusText?.text =
                        "No reference video path configured for \"$displayWord\"."
                    return@launch
                }

                val ref = storage.getReference(storagePath)
                val url = ref.downloadUrl.await()

                referenceVideoView?.apply {
                    visibility = View.VISIBLE
                    setVideoURI(url)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        start()
                    }
                }

                statusText?.text =
                    "Showing reference for \"$displayWord\""

            } catch (e: Exception) {
                e.printStackTrace()
                referenceVideoView?.apply {
                    stopPlayback()
                    visibility = View.GONE
                }
                statusText?.text =
                    "Failed to load reference: ${e.localizedMessage}"
            } finally {
                setLoading(false)
            }
        }
    }

    // --------------------------------------------------------------------
    //  Submit sample
    // --------------------------------------------------------------------
    private fun normalizeWord(value: String): String {
        return value.trim().lowercase().replace("\\s+".toRegex(), "_")
    }

    private fun submitSample() {
        val email = auth.currentUser?.email
        val word = editWord?.text?.toString()?.trim()
        val videoUri = selectedVideoUri

        if (email.isNullOrBlank()) {
            Toast.makeText(requireContext(), "You must be logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (word.isNullOrEmpty()) {
            editWord?.error = "Enter a word"
            return
        }

        if (videoUri == null) {
            Toast.makeText(requireContext(), "Select a video first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                // pipeline.run sends the video to the extraction server (http://10.0.2.2:8000/extract)
                val pipeline = AslSamplePipeline(requireContext())
                val result = withContext(Dispatchers.IO) {
                    pipeline.run(word, videoUri)
                }
                pipeline.close()

                val typedWord = word
                val predictedWord = result.predictedLabel

                val typedNorm = normalizeWord(typedWord)
                val predictedNorm = normalizeWord(predictedWord)
                val isMismatch = typedNorm != predictedNorm

                // Standard contribution path: Upload the video to Firebase Storage
                val (storagePath, downloadUrl) = uploadContributionVideo(videoUri, typedWord)
                
                // Dev payload structure to match standard Contribute payload exactly (as seen in Screenshot 2)
                val payload = hashMapOf(
                    "word" to typedWord,
                    "typedWord" to typedWord,
                    "predictedLabel" to predictedWord,
                    "confidence" to result.confidence,
                    "keypoints" to result.keypoints.toList(),
                    "videoUrl" to downloadUrl,
                    "videoStoragePath" to storagePath,
                    "userEmail" to "admin@gmail.com",
                    "status" to "accepted",
                    "createdAt" to System.currentTimeMillis(),
                    "isMismatch" to isMismatch
                )

                val response = functions
                    .getHttpsCallable("submitAslSample")
                    .call(payload)
                    .await()

                val data = response.data as? Map<*, *>
                val status = data?.get("status") as? String
                val collection = data?.get("collection") as? String

                statusText?.text = "Submitted: $status → $collection"
                Toast.makeText(requireContext(), "Sample submitted: $status", Toast.LENGTH_SHORT).show()

                loadUserContributions()
            } catch (e: Exception) {
                e.printStackTrace()
                statusText?.text = "Error: ${e.localizedMessage}"
                Toast.makeText(requireContext(), "Submit failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }
    private suspend fun uploadContributionVideo(
        videoUri: Uri,
        word: String
    ): Pair<String, String> {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User is not logged in")

        val safeWord = word.trim().lowercase().replace("\\s+".toRegex(), "_")
        val fileName = "${System.currentTimeMillis()}_${safeWord}.mp4"
        val storagePath = "contributions/$uid/$fileName"

        val ref = storage.reference.child(storagePath)

        Log.d("UPLOAD", "Starting upload to $storagePath")

        withTimeout(30000) {
            ref.putFile(videoUri).await()
        }

        Log.d("UPLOAD", "Upload complete")

        val downloadUrl = ref.downloadUrl.await().toString()
        return storagePath to downloadUrl
    }

    // --------------------------------------------------------------------
    //  Real stats + “Your Contributions” list
    // --------------------------------------------------------------------

    private data class UserContribution(
        val word: String,
        val statusLabel: String,   // "approved" or "pending"
        val createdAt: Timestamp?,
        val isApproved: Boolean
    )

    private fun loadUserContributions() {
        val email = auth.currentUser?.email
        if (email.isNullOrBlank()) {
            txtTotalCount?.text = "0"
            txtApprovedCount?.text = "0"
            txtPendingCount?.text = "0"
            contributionsContainer?.removeAllViews()
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                // Check for both the logged in user and the dev admin email
                val emailQueryList = listOfNotNull(email, "admin@gmail.com").distinct()

                val acceptedSnap = firestore.collection("asl_accepted")
                    .whereIn("userEmail", emailQueryList)
                    .get()
                    .await()

                val pendingSnap = firestore.collection("asl_review")
                    .whereIn("userEmail", emailQueryList)
                    .get()
                    .await()

                val acceptedCount = acceptedSnap.size()
                val pendingCount = pendingSnap.size()
                val total = acceptedCount + pendingCount

                txtTotalCount?.text = total.toString()
                txtApprovedCount?.text = acceptedCount.toString()
                txtPendingCount?.text = pendingCount.toString()

                // Build list of individual contributions
                val allContrib = mutableListOf<UserContribution>()
                allContrib += mapSnapshotToContrib(acceptedSnap, true)
                allContrib += mapSnapshotToContrib(pendingSnap, false)

                // most recent first
                allContrib.sortByDescending { it.createdAt?.seconds ?: 0L }

                renderContributions(allContrib)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun mapSnapshotToContrib(
        snap: QuerySnapshot,
        approved: Boolean
    ): List<UserContribution> {
        val list = mutableListOf<UserContribution>()
        for (doc in snap.documents) {
            // Check both "word" and "Label" (screenshot uses Label)
            val word = doc.getString("word") ?: doc.getString("Label") ?: "(unknown)"
            val ts = doc.getTimestamp("createdAt")
            list += UserContribution(
                word = word,
                statusLabel = if (approved) "approved" else "pending",
                createdAt = ts,
                isApproved = approved
            )
        }
        return list
    }

    private fun renderContributions(items: List<UserContribution>) {
        val container = contributionsContainer ?: return
        val inflater = LayoutInflater.from(requireContext())

        container.removeAllViews()

        if (items.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "No contributions yet. Upload a gesture to get started!"
                textSize = 14f
            }
            container.addView(empty)
            return
        }

        for (item in items) {
            val row = inflater.inflate(
                R.layout.item_contribution,
                container,
                false
            )

            val wordText = row.findViewById<TextView>(R.id.textGestureWord)
            val statusChip = row.findViewById<TextView>(R.id.textStatusChip)
            val dateText = row.findViewById<TextView>(R.id.textCreatedDate)

            wordText.text = item.word
            statusChip.text = item.statusLabel

            // simple date string
            val dateStr = item.createdAt?.toDate()?.let { date ->
                android.text.format.DateFormat.format("yyyy-MM-dd", date)
                    .toString()
            } ?: ""

            dateText.text = dateStr

            // Use a color or a background that exists
            if (item.isApproved) {
                statusChip.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            } else {
                statusChip.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
            }

            container.addView(row)
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
