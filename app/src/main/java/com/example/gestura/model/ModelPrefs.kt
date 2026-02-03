package com.example.gestura.model

import android.content.Context

class ModelPrefs(context: Context) {
    private val sp = context.getSharedPreferences("gestura_model_prefs", Context.MODE_PRIVATE)

    fun getLocalVersion(): Long = sp.getLong("local_version", 0L)
    fun setLocalVersion(v: Long) = sp.edit().putLong("local_version", v).apply()

    fun getLastUpdatedMillis(): Long = sp.getLong("last_updated_millis", 0L)
    fun setLastUpdatedMillis(ms: Long) = sp.edit().putLong("last_updated_millis", ms).apply()
}
