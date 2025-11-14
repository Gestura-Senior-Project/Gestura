package com.example.gestura.model

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.io.Closeable
import kotlin.math.max

class HolisticFeatureExtractor(
    context: Context,
    faceModel: String = "face_landmarker.task",
    poseModel: String = "pose_landmarker_full.task",
    handModel: String = "hand_landmarker.task"
) : Closeable {

    companion object {
        const val TIMESTEPS = 5          // must match your TFLite model: (1, 5, 1662)
        const val FEATURES_PER_FRAME = 1662
    }

    private val faceLandmarker: FaceLandmarker
    private val poseLandmarker: PoseLandmarker
    private val handLandmarker: HandLandmarker

    init {
        val baseFace = BaseOptions.builder()
            .setModelAssetPath(faceModel)
            .build()

        val basePose = BaseOptions.builder()
            .setModelAssetPath(poseModel)
            .build()

        val baseHand = BaseOptions.builder()
            .setModelAssetPath(handModel)
            .build()

        val faceOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseFace)
            .setRunningMode(RunningMode.VIDEO)
            .setNumFaces(1)
            .build()

        val poseOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(basePose)
            .setRunningMode(RunningMode.VIDEO)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()

        val handOptions = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseHand)
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(2)
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(context, faceOptions)
        poseLandmarker = PoseLandmarker.createFromOptions(context, poseOptions)
        handLandmarker = HandLandmarker.createFromOptions(context, handOptions)
    }

    /**
     * Extract a [TIMESTEPS, FEATURES_PER_FRAME] feature matrix from a video Uri.
     * Returns a flattened FloatArray of size TIMESTEPS * FEATURES_PER_FRAME.
     */
    fun extractSequenceFromVideo(
        context: Context,
        videoUri: Uri
    ): FloatArray {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)

        val durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: throw IllegalArgumentException("Could not read duration for $videoUri")

        val sequence = Array(TIMESTEPS) { FloatArray(FEATURES_PER_FRAME) }

        // Sample TIMESTEPS timestamps from [0, duration]
        val steps = max(1, TIMESTEPS - 1)
        for (step in 0 until TIMESTEPS) {
            val timeMs = if (TIMESTEPS == 1) {
                durationMs / 2
            } else {
                (durationMs * step) / steps
            }
            val timeUs = timeMs * 1000L

            val frameBitmap = retriever.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST
            )

            if (frameBitmap == null) {
                // If we fail to get a frame, just keep zeros for this timestep
                continue
            }

            val features = extractFeaturesFromBitmap(frameBitmap, timeMs)
            sequence[step] = features
        }

        retriever.release()

        // Flatten to TIMESTEPS * FEATURES_PER_FRAME
        val flat = FloatArray(TIMESTEPS * FEATURES_PER_FRAME)
        var idx = 0
        for (t in 0 until TIMESTEPS) {
            for (f in 0 until FEATURES_PER_FRAME) {
                flat[idx++] = sequence[t][f]
            }
        }
        return flat
    }

    private fun extractFeaturesFromBitmap(
        bitmap: Bitmap,
        timestampMs: Long
    ): FloatArray {
        // ✅ Correct way to create MPImage from Bitmap
        val mpImage: MPImage = BitmapImageBuilder(bitmap).build()

        val imageOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(0)   // tweak if needed depending on camera orientation
            .build()

        // ✅ Correct argument order for VIDEO mode:
        // detectForVideo(image, imageOptions, timestampMs)
        val faceResult: FaceLandmarkerResult =
            faceLandmarker.detectForVideo(mpImage, imageOptions, timestampMs)
        val poseResult: PoseLandmarkerResult =
            poseLandmarker.detectForVideo(mpImage, imageOptions, timestampMs)
        val handResult: HandLandmarkerResult =
            handLandmarker.detectForVideo(mpImage, imageOptions, timestampMs)

        val features = FloatArray(FEATURES_PER_FRAME)
        var idx = 0

        // ----- Face: 468 * 3 -----
        val faceLandmarksList = faceResult.faceLandmarks()
        val faceLandmarks = faceLandmarksList.firstOrNull()
        if (faceLandmarks != null) {
            val count = minOf(468, faceLandmarks.size)
            for (i in 0 until count) {
                val lm: NormalizedLandmark = faceLandmarks[i]
                features[idx++] = lm.x()
                features[idx++] = lm.y()
                features[idx++] = lm.z()
            }
            val remaining = 468 - count
            if (remaining > 0) {
                repeat(remaining * 3) { features[idx++] = 0f }
            }
        } else {
            repeat(468 * 3) { features[idx++] = 0f }
        }

        // ----- Pose: 33 * 4 (x, y, z, visibility) -----
        val poseLandmarksList = poseResult.landmarks()
        val poseLandmarks = poseLandmarksList.firstOrNull()
        if (poseLandmarks != null) {
            val count = minOf(33, poseLandmarks.size)
            for (i in 0 until count) {
                val lm: NormalizedLandmark = poseLandmarks[i]
                features[idx++] = lm.x()
                features[idx++] = lm.y()
                features[idx++] = lm.z()
                // visibility isn't nullable in Java, but Kotlin wrapper may use default
                features[idx++] = lm.visibility().orElse(1.0f)
            }
            val remaining = 33 - count
            if (remaining > 0) {
                repeat(remaining * 4) { features[idx++] = 0f }
            }
        } else {
            repeat(33 * 4) { features[idx++] = 0f }
        }

        // ----- Left hand: 21 * 3 -----
        val hands = handResult.landmarks()
        val leftHand = hands.getOrNull(0)
        if (leftHand != null) {
            val count = minOf(21, leftHand.size)
            for (i in 0 until count) {
                val lm = leftHand[i]
                features[idx++] = lm.x()
                features[idx++] = lm.y()
                features[idx++] = lm.z()
            }
            val remaining = 21 - count
            if (remaining > 0) {
                repeat(remaining * 3) { features[idx++] = 0f }
            }
        } else {
            repeat(21 * 3) { features[idx++] = 0f }
        }

        // ----- Right hand: 21 * 3 -----
        val rightHand = hands.getOrNull(1)
        if (rightHand != null) {
            val count = minOf(21, rightHand.size)
            for (i in 0 until count) {
                val lm = rightHand[i]
                features[idx++] = lm.x()
                features[idx++] = lm.y()
                features[idx++] = lm.z()
            }
            val remaining = 21 - count
            if (remaining > 0) {
                repeat(remaining * 3) { features[idx++] = 0f }
            }
        } else {
            repeat(21 * 3) { features[idx++] = 0f }
        }

        require(idx == FEATURES_PER_FRAME) {
            "Feature vector length mismatch: expected $FEATURES_PER_FRAME, got $idx"
        }

        return features
    }

    override fun close() {
        faceLandmarker.close()
        poseLandmarker.close()
        handLandmarker.close()
    }
}
