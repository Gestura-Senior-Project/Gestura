package com.example.gestura.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SettingsViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // --- Theme prefs (same as before) ---
    private val _theme = MutableLiveData("auto")   // "light" | "dark" | "auto"
    val theme: LiveData<String> = _theme

    private val _autoUpdate = MutableLiveData(true)
    val autoUpdate: LiveData<Boolean> = _autoUpdate

    private val _maskSync = MutableLiveData(false)
    val maskSync: LiveData<Boolean> = _maskSync

    // --- Stats & Dev mode ---
    private val _totalContrib = MutableLiveData<Int?>()
    val totalContrib: LiveData<Int?> = _totalContrib

    private val _accuracy = MutableLiveData<Double?>()
    val accuracy: LiveData<Double?> = _accuracy

    private val _devAvailable = MutableLiveData(false)
    val devAvailable: LiveData<Boolean> = _devAvailable

    private val _devMode = MutableLiveData(false)
    val devMode: LiveData<Boolean> = _devMode

    init {
        // Load stats whenever the VM is created
        loadStatsForCurrentUser()
    }

    // -------- Theme / toggles ----------

    fun setTheme(value: String) {
        // "light" | "dark" | "auto"
        _theme.value = value
    }

    fun setAutoUpdate(enabled: Boolean) {
        _autoUpdate.value = enabled
    }

    fun setMaskSync(enabled: Boolean) {
        _maskSync.value = enabled
    }

    // -------- Stats / Dev mode ----------

    fun loadStatsForCurrentUser() {
        val email = auth.currentUser?.email
        if (email.isNullOrBlank()) {
            _totalContrib.value = null
            _accuracy.value = null
            _devAvailable.value = false
            _devMode.value = false
            return
        }

        viewModelScope.launch {
            try {
                // Count accepted and pending docs for this userEmail
                val acceptedSnap = db.collection("asl_accepted")
                    .whereEqualTo("userEmail", email)
                    .get()
                    .await()

                val pendingSnap = db.collection("asl_pending")
                    .whereEqualTo("userEmail", email)
                    .get()
                    .await()

                val acceptedCount = acceptedSnap.size()
                val pendingCount = pendingSnap.size()

                // Total contributions = accepted + pending
                val total = acceptedCount + pendingCount
                _totalContrib.value = if (total > 0) total else null

                // ✅ Accuracy: approved / total * 100
                // Example: 45 accepted out of 47 total => 95.7%
                val accuracyPct: Double? =
                    if (total > 0) {
                        (acceptedCount.toDouble() / total.toDouble()) * 100.0
                    } else {
                        null
                    }
                _accuracy.value = accuracyPct

                // ✅ Dev available if this userEmail appears >= 90 times in asl_accepted
                _devAvailable.value = (acceptedCount >= 90)

                // If dev no longer available, force-disable devMode
                if (acceptedCount < 90) {
                    _devMode.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _totalContrib.value = null
                _accuracy.value = null
                _devAvailable.value = false
                _devMode.value = false
            }
        }
    }

    fun tryToggleDevMode() {
        // Dev mode can only be toggled if user qualifies
        if (_devAvailable.value == true) {
            _devMode.value = !(_devMode.value ?: false)
        }
    }
}
