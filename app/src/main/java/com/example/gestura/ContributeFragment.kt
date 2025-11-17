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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
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

    private var editWord: EditText? = null
    private var buttonPickVideo: Button? = null
    private var buttonSubmit: Button? = null
    private var videoView: VideoView? = null
    private var statusText: TextView? = null
    private var progress: ProgressBar? = null

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

        editWord = view.findViewById(R.id.editWord)
        buttonPickVideo = view.findViewById(R.id.buttonPickVideo)
        buttonSubmit = view.findViewById(R.id.buttonSubmit)
        videoView = view.findViewById(R.id.videoView)
        statusText = view.findViewById(R.id.statusText)
        progress = view.findViewById(R.id.progressBar)

        buttonPickVideo?.setOnClickListener {
            launchVideoPicker()
        }

        buttonSubmit?.setOnClickListener {
            submitSample()
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
    }

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

                // Prepare payload for Cloud Function
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
                val collection =
                    data?.get("collection") as? String

                statusText?.text =
                    "Submitted: $status → $collection"
                Toast.makeText(
                    requireContext(),
                    "Sample submitted: $status",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
                statusText?.text =
                    "Error: ${e.localizedMessage}"
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
}
