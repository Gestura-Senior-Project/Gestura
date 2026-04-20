package com.example.gestura.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = application.getSharedPreferences("gestura_settings", Context.MODE_PRIVATE)

    private val _theme = MutableLiveData(prefs.getString("theme", "auto") ?: "auto")
    val theme: LiveData<String> = _theme

    private val _autoUpdate = MutableLiveData(prefs.getBoolean("auto_update", true))
    val autoUpdate: LiveData<Boolean> = _autoUpdate

    private val _maskSync = MutableLiveData(prefs.getBoolean("mask_sync", false))
    val maskSync: LiveData<Boolean> = _maskSync

    private val _totalContrib = MutableLiveData<Int?>()
    val totalContrib: LiveData<Int?> = _totalContrib

    private val _accuracy = MutableLiveData<Double?>()
    val accuracy: LiveData<Double?> = _accuracy

    private val _devAvailable = MutableLiveData(false)
    val devAvailable: LiveData<Boolean> = _devAvailable

    private val _devMode = MutableLiveData(false)
    val devMode: LiveData<Boolean> = _devMode

    init {
        loadStatsForCurrentUser()
    }

    fun setTheme(value: String) {
        _theme.value = value
        prefs.edit().putString("theme", value).apply()
    }

    fun setAutoUpdate(enabled: Boolean) {
        _autoUpdate.value = enabled
        prefs.edit().putBoolean("auto_update", enabled).apply()
    }

    fun setMaskSync(enabled: Boolean) {
        _maskSync.value = enabled
        prefs.edit().putBoolean("mask_sync", enabled).apply()
    }

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
                val total = acceptedCount + pendingCount

                _totalContrib.value = total

                _accuracy.value =
                    if (total > 0) (acceptedCount.toDouble() / total.toDouble()) * 100.0
                    else null

                _devAvailable.value = acceptedCount >= 90

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
        if (_devAvailable.value == true) {
            _devMode.value = !(_devMode.value ?: false)
        }
    }
}