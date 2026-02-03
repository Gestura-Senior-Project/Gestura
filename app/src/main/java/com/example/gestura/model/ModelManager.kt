package com.example.gestura.model

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import java.io.File

class ModelManager(private val context: Context) {

    private val storage = Firebase.storage

    private val modelDir: File = File(context.filesDir, "models").apply { mkdirs() }

    // The “active” downloaded model path your classifier will check
    val localModelFile: File = File(modelDir, "asl_model.tflite")

    fun hasDownloadedModel(): Boolean =
        localModelFile.exists() && localModelFile.length() > 0L

    fun downloadLatest(
        remotePath: String = "models/asl_model.tflite",
        onProgress: (Int) -> Unit = {},
        onSuccess: (File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val ref = storage.reference.child(remotePath)
        val tmp = File(modelDir, "asl_model.tmp")

        ref.getFile(tmp)
            .addOnProgressListener { snap ->
                val pct =
                    if (snap.totalByteCount > 0)
                        (100.0 * snap.bytesTransferred / snap.totalByteCount).toInt()
                    else 0
                onProgress(pct)
            }
            .addOnSuccessListener {
                try {
                    if (localModelFile.exists()) localModelFile.delete()
                    val ok = tmp.renameTo(localModelFile)
                    if (!ok) throw IllegalStateException("Failed to replace local model file.")
                    onSuccess(localModelFile)
                } catch (e: Exception) {
                    if (tmp.exists()) tmp.delete()
                    onError(e)
                }
            }
            .addOnFailureListener { e ->
                if (tmp.exists()) tmp.delete()
                onError(e)
            }
    }
}
