package com.example.gestura.model


import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.tasks.await

class ModelUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val prefs = ModelPrefs(ctx)
            val manager = ModelManager(ctx)
            val firestore = Firebase.firestore

            val doc = firestore.collection("config").document("model").get().await()
            if (!doc.exists()) return Result.success()

            val storagePath = doc.getString("storagePath") ?: "models/asl_model.tflite"
            val versionAny = doc.get("version") ?: 0L
            val remoteVersion = when (versionAny) {
                is Long -> versionAny
                is Int -> versionAny.toLong()
                is Double -> versionAny.toLong()
                else -> 0L
            }

            val localVersion = prefs.getLocalVersion()
            val hasLocalFile = manager.hasDownloadedModel()

            if (hasLocalFile && remoteVersion <= localVersion) {
                return Result.success()
            }

            // Download (Worker has no UI, so no progress)
            val completed = kotlinx.coroutines.suspendCancellableCoroutine<Result> { cont ->
                manager.downloadLatest(
                    remotePath = storagePath,
                    onProgress = {},
                    onSuccess = {
                        prefs.setLocalVersion(remoteVersion)
                        val ts = doc.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
                        prefs.setLastUpdatedMillis(ts)
                        if (!cont.isCompleted) cont.resume(Result.success()) {}
                    },
                    onError = {
                        if (!cont.isCompleted) cont.resume(Result.retry()) {}
                    }
                )
            }

            completed
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
