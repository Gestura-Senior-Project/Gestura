package com.example.gestura.model

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object ModelUpdateScheduler {

    private const val UNIQUE_NAME = "gestura_model_auto_update"

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)

        if (!enabled) {
            wm.cancelUniqueWork(UNIQUE_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi
            .build()

        val req = PeriodicWorkRequestBuilder<ModelUpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        wm.enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }
}
