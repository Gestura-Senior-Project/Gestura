package com.example.gestura.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.example.gestura.R
import com.example.gestura.util.ThemeHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SettingsFragment : Fragment() {

    private val vm: SettingsViewModel by viewModels()
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { Firebase.firestore }

    private fun renderModelStatus(tv: TextView?) {
        if (tv == null) return
        val prefs = com.example.gestura.model.ModelPrefs(requireContext())

        val v = prefs.getLocalVersion()
        val ms = prefs.getLastUpdatedMillis()

        val dateStr = if (ms > 0L) {
            android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", java.util.Date(ms)).toString()
        } else "—"

        tv.text = "Model v$v • Last updated: $dateStr"
    }


    private fun downloadModelFromFirebaseWithVersionCheck(
        tvModelLastUpdated: TextView? = null
    ) {
        val ctx = requireContext()
        val prefs = com.example.gestura.model.ModelPrefs(ctx)
        val manager = com.example.gestura.model.ModelManager(ctx)

        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(ctx, "Checking for model update…", Toast.LENGTH_SHORT).show()

            try {
                // 1) Read remote metadata from Firestore: config/model
                val doc = firestore.collection("config").document("model").get().await()
                if (!doc.exists()) {
                    Toast.makeText(ctx, "No model metadata found (config/model).", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val storagePath = doc.getString("storagePath") ?: "models/asl_model.tflite"

                val versionAny = doc.get("version") ?: 0L
                val remoteVersion = when (versionAny) {
                    is Long -> versionAny
                    is Int -> versionAny.toLong()
                    is Double -> versionAny.toLong()
                    else -> 0L
                }

                val updatedAt = doc.getTimestamp("updatedAt")
                val remoteUpdatedMillis = updatedAt?.toDate()?.time

                // 2) Compare local vs remote
                val localVersion = prefs.getLocalVersion()

                val hasLocalFile = manager.hasDownloadedModel()
                val upToDate = hasLocalFile && remoteVersion <= localVersion

                if (upToDate) {
                    Toast.makeText(ctx, "Model is already up to date (v$localVersion).", Toast.LENGTH_SHORT).show()
                    renderModelStatus(tvModelLastUpdated)
                    return@launch
                }

                // 3) Download from Storage
                Toast.makeText(ctx, "Downloading model v$remoteVersion…", Toast.LENGTH_SHORT).show()

                manager.downloadLatest(
                    remotePath = storagePath,
                    onProgress = { /* optional: update UI */ },
                    onSuccess = { file ->
                        // Persist local metadata
                        prefs.setLocalVersion(remoteVersion)
                        prefs.setLastUpdatedMillis(remoteUpdatedMillis ?: System.currentTimeMillis())

                        Toast.makeText(ctx, "Model updated to v$remoteVersion.", Toast.LENGTH_LONG).show()
                        renderModelStatus(tvModelLastUpdated)
                    },
                    onError = { e ->
                        Toast.makeText(ctx, "Model download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                )

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(ctx, "Update check failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // -------- Profile header --------
        val tvName = view.findViewById<TextView>(R.id.tvName)
        val tvEmail = view.findViewById<TextView>(R.id.tvEmail)
        val avatar = view.findViewById<TextView>(R.id.tvAvatarInitial)
        val tvModelLastUpdated = view.findViewById<TextView>(R.id.tvModelLastUpdated)
        renderModelStatus(tvModelLastUpdated)

        val user = FirebaseAuth.getInstance().currentUser
        android.util.Log.d("AUTH", "user=${user?.uid} email=${user?.email}")
        val email = user?.email
        val displayName = user?.displayName ?: email?.substringBefore("@") ?: "—"

        tvName.text = displayName
        tvEmail.text = email ?: "—"
        avatar.text = (displayName.firstOrNull() ?: '?').uppercase()

        // Editable profile (local only)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etProfileEmail = view.findViewById<EditText>(R.id.etProfileEmail)
        etName.setText(displayName)
        etProfileEmail.setText(email ?: "")
        view.findViewById<View>(R.id.btnSaveProfile).setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Profile updated locally (backend sync coming soon)",
                Toast.LENGTH_SHORT
            ).show()
        }

        // -------- Preferences (theme etc) --------
        val spTheme = view.findViewById<Spinner>(R.id.spTheme)
        spTheme.adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.settings_themes,
            android.R.layout.simple_spinner_dropdown_item
        )

        vm.theme.observe(viewLifecycleOwner) { theme ->
            val values = resources.getStringArray(R.array.settings_themes_values)
            val idx = values.indexOf(theme)
            if (idx >= 0 && spTheme.selectedItemPosition != idx) {
                spTheme.setSelection(idx)
            }
        }

        spTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                v: View?,
                pos: Int,
                id: Long
            ) {
                val value =
                    resources.getStringArray(R.array.settings_themes_values)[pos] // "light"|"dark"|"auto"
                vm.setTheme(value)
                ThemeHelper.apply(value)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // -------- Stats views --------
        val tvContrib = view.findViewById<TextView>(R.id.tvContrib)
        val tvAccuracy = view.findViewById<TextView>(R.id.tvAccuracy)
        val tvStatsHint = view.findViewById<TextView>(R.id.tvStatsHint)

        tvContrib.text = "—"
        tvAccuracy.text = "—"
        tvStatsHint.text = "Loading your contribution stats…"
        tvStatsHint.visibility = View.VISIBLE

        // -------- Developer section --------
        val swDevMode = view.findViewById<Switch>(R.id.swDevMode)
        val tvDevHint = view.findViewById<TextView>(R.id.tvDevHint)
        val rowReviewContrib = view.findViewById<View>(R.id.rowReviewContrib)

        swDevMode.isEnabled = false
        rowReviewContrib.visibility = View.GONE
        tvDevHint.text = "Connect database to enable"

        // -------- AI Model (stubs) --------
        val swAutoUpdate = view.findViewById<Switch>(R.id.swAutoUpdate)
        vm.autoUpdate.observe(viewLifecycleOwner) { swAutoUpdate.isChecked = it }
        swAutoUpdate.setOnCheckedChangeListener { _, b -> vm.setAutoUpdate(b) }

        view.findViewById<View>(R.id.rowUpdateModel).setOnClickListener {
            downloadModelFromFirebaseWithVersionCheck(tvModelLastUpdated)
        }


        view.findViewById<View>(R.id.rowSyncMasks).setOnClickListener {
            Toast.makeText(requireContext(), "Syncing masks…", Toast.LENGTH_SHORT).show()
        }

        // -------- Logout --------
        view.findViewById<View>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.loginFragment)
        }

        // -------- Load stats + gate Dev Mode --------
        if (email == null) {
            tvStatsHint.text = "Log in to see your stats."
            swDevMode.isEnabled = false
            rowReviewContrib.visibility = View.GONE
        } else {
            loadStatsAndDevGate(
                email = email,
                tvContrib = tvContrib,
                tvAccuracy = tvAccuracy,
                tvStatsHint = tvStatsHint,
                swDevMode = swDevMode,
                tvDevHint = tvDevHint,
                rowReviewContrib = rowReviewContrib
            )
        }

        // Navigate to DevReviewFragment when unlocked + dev mode ON
        rowReviewContrib.setOnClickListener {
            findNavController().navigate(R.id.devReviewFragment)
        }
    }

    private fun loadStatsAndDevGate(
        email: String,
        tvContrib: TextView,
        tvAccuracy: TextView,
        tvStatsHint: TextView,
        swDevMode: Switch,
        tvDevHint: TextView,
        rowReviewContrib: View
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // ---------- Per-user contributions ----------
                val userAcceptedSnap = firestore
                    .collection("asl_accepted")
                    .whereEqualTo("userEmail", email)
                    .get()
                    .await()

                val userPendingSnap = firestore
                    .collection("asl_review")
                    .whereEqualTo("userEmail", email)
                    .get()
                    .await()

                val userAccepted = userAcceptedSnap.size()
                val userPending = userPendingSnap.size()
                val userTotal = userAccepted + userPending

                // Contributions = accepted + pending
                if (userTotal > 0) {
                    tvContrib.text = userTotal.toString()
                    tvStatsHint.visibility = View.GONE
                } else {
                    tvContrib.text = "0"
                    tvStatsHint.text = "Contribute some samples to see stats."
                    tvStatsHint.visibility = View.VISIBLE
                }

                // ---------- Accuracy = avg(confidence) of this user's accepted samples ----------
                if (userAccepted > 0) {
                    var sum = 0.0
                    var count = 0

                    for (doc in userAcceptedSnap.documents) {
                        val c = doc.getDouble("confidence")
                        if (c != null) {
                            sum += c
                            count++
                        }
                    }

                    if (count > 0) {
                        val rawAverage = sum / count.toDouble()
                        // If confidence stored 0–1, scale to percentage; if already 0–100, leave it.
                        val percent =
                            if (rawAverage <= 1.0) rawAverage * 100.0 else rawAverage
                        tvAccuracy.text = String.format("%.1f%%", percent)
                    } else {
                        tvAccuracy.text = "—"
                    }
                } else {
                    tvAccuracy.text = "—"
                    if (userTotal > 0) {
                        tvStatsHint.text = "No accepted samples yet – keep contributing!"
                        tvStatsHint.visibility = View.VISIBLE
                    }
                }

                // ---------- Dev Mode unlock: needs at least 90 accepted samples ----------
                val devUnlocked = userAccepted >= 5
                swDevMode.isEnabled = devUnlocked

                tvDevHint.text = if (devUnlocked) {
                    "Developer Mode unlocked – you can review pending contributions."
                } else {
                    "Submit at least 90 accepted samples to unlock Developer Mode."
                }

                // Show/hide review row based on switch + unlock
                rowReviewContrib.isVisible = devUnlocked && swDevMode.isChecked

                swDevMode.setOnCheckedChangeListener { _, isChecked ->
                    rowReviewContrib.isVisible = devUnlocked && isChecked
                }

            } catch (e: Exception) {
                e.printStackTrace()
                tvContrib.text = "—"
                tvAccuracy.text = "—"
                tvStatsHint.text = "Error loading stats: ${e.localizedMessage ?: "unknown"}"
                tvStatsHint.visibility = View.VISIBLE
                swDevMode.isEnabled = false
                rowReviewContrib.visibility = View.GONE
                tvDevHint.text = "Developer Mode unavailable (stats error)."
            }
        }
    }
}
