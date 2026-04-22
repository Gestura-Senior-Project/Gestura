package com.example.gestura.dev

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gestura.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class DevReviewFragment : Fragment(R.layout.dev_review_fragment) {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ReviewAdapter
    private val reviewItems = mutableListOf<ReviewContribution>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.reviewRecyclerView)
        adapter = ReviewAdapter(reviewItems)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<View>(R.id.backRow).setOnClickListener {
            findNavController().navigateUp()
        }

        attachSwipeActions()
        loadPendingReviews()
    }

    private fun loadPendingReviews() {
        db.collection("asl_review")
            .get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.documents.map { doc ->
                    @Suppress("UNCHECKED_CAST")
                    ReviewContribution(
                        id = doc.id,
                        word = doc.getString("word") ?: doc.getString("typedWord") ?: "",
                        predictedLabel = doc.getString("predictedLabel") ?: "",
                        videoUrl = doc.getString("videoUrl") ?: "",
                        userEmail = doc.getString("userEmail") ?: "",
                        confidence = doc.getDouble("confidence") ?: 0.0,
                        keypoints = (doc.get("keypoints") as? List<Double>) ?: emptyList(),
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }

                adapter.setItems(items)
            }
    }

    private fun attachSwipeActions() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.getItem(position)

                when (direction) {
                    ItemTouchHelper.RIGHT -> acceptContribution(item, position)
                    ItemTouchHelper.LEFT -> rejectContribution(item, position)
                }
            }
        }

        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    private fun acceptContribution(item: ReviewContribution, position: Int) {
        val reviewRef = db.collection("asl_review").document(item.id)
        val acceptedRef = db.collection("asl_accepted").document(item.id)

        // Matching Screenshot 2 payload exactly
        val acceptedData = hashMapOf(
            "confidence" to item.confidence,
            "createdAt" to item.createdAt,
            "id" to item.id,
            "keypoints" to item.keypoints,
            "predictedLabel" to item.predictedLabel,
            "status" to "accepted",
            "userEmail" to item.userEmail,
            "word" to item.word
        )

        db.runBatch { batch ->
            batch.set(acceptedRef, acceptedData)
            batch.delete(reviewRef)
        }.addOnSuccessListener {
            adapter.removeAt(position)
        }.addOnFailureListener {
            adapter.notifyItemChanged(position)
        }
    }

    private fun rejectContribution(item: ReviewContribution, position: Int) {
        val reviewRef = db.collection("asl_review").document(item.id)
        val rejectedRef = db.collection("asl_rejected").document(item.id)

        // Maintaining consistency for rejected documents as well
        val rejectedData = hashMapOf(
            "confidence" to item.confidence,
            "createdAt" to item.createdAt,
            "id" to item.id,
            "keypoints" to item.keypoints,
            "predictedLabel" to item.predictedLabel,
            "status" to "rejected",
            "userEmail" to item.userEmail,
            "word" to item.word
        )

        db.runBatch { batch ->
            batch.set(rejectedRef, rejectedData)
            batch.delete(reviewRef)
        }.addOnSuccessListener {
            adapter.removeAt(position)
        }.addOnFailureListener {
            adapter.notifyItemChanged(position)
        }
    }
}