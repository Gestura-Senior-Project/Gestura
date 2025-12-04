package com.example.gestura

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.withContext

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

    private var selectedVideoUri: Uri? = null

    private val pickVideoLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    requireContext().contentResolver
                        .takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    onVideoSelected(uri)
                }
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

        // 🔥 load real stats + contributions whenever screen opens
        loadUserContributions()
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
            seekTo(1)
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
    //  Reference video (same as before)
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
    //  Submit sample (unchanged)
    // --------------------------------------------------------------------
    private fun submitSample() {
        val email = auth.currentUser?.email
        val word = editWord?.text?.toString()?.trim()
        val videoUri = selectedVideoUri

        if (email.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                "You must be logged in",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (word.isNullOrEmpty()) {
            editWord?.error = "Enter a word"
            return
        }

        if (videoUri == null) {
            Toast.makeText(
                requireContext(),
                "Select a video first",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                val pipeline = AslSamplePipeline(requireContext())
                val result = withContext(Dispatchers.IO) {
                    pipeline.run(word, videoUri)
                }
                pipeline.close()

                val payload = hashMapOf(
                    "word" to result.word,
                    "predictedLabel" to result.predictedLabel,
                    "confidence" to result.confidence,
                    "keypoints" to result.keypoints.toList(),
                    "userEmail" to email
                )

                val response = functions
                    .getHttpsCallable("submitAslSample")
                    .call(payload)
                    .await()

                val data = response.data as? Map<*, *>
                val status = data?.get("status") as? String
                val collection = data?.get("collection") as? String

                statusText?.text = "Submitted: $status → $collection"
                Toast.makeText(
                    requireContext(),
                    "Sample submitted: $status",
                    Toast.LENGTH_SHORT
                ).show()

                // 🔁 Reload stats after a successful submission
                loadUserContributions()
            } catch (e: Exception) {
                e.printStackTrace()
                statusText?.text = "Error: ${e.localizedMessage}"
                Toast.makeText(
                    requireContext(),
                    "Submit failed: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    // --------------------------------------------------------------------
    //  NEW: Real stats + “Your Contributions” list
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
                // 🔁 same idea as SettingsViewModel.loadStatsForCurrentUser()
                val acceptedSnap = firestore.collection("asl_accepted")
                    .whereEqualTo("userEmail", email)
                    .get()
                    .await()

                val pendingSnap = firestore.collection("asl_review")
                    .whereEqualTo("userEmail", email)
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
            val word = doc.getString("word") ?: "(unknown)"
            // ⚠️ adjust "createdAt" if your field name is different
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

            // tiny visual tweak for status color
            val chipBg = if (item.isApproved) {
                R.drawable.a   // you can create these shapes
            } else {
                R.drawable.b
            }
            statusChip.setBackgroundResource(chipBg)

            container.addView(row)
        }
    }
}
