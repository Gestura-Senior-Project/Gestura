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
                    ReviewContribution(
                        id = doc.id,

                        // Show what user was supposed to sign
                        label = doc.getString("typedWord")
                            ?: doc.getString("word")
                            ?: "",

                        videoUrl = doc.getString("videoUrl") ?: "",

                        // uploader
                        uploaderEmail = doc.getString("userEmail") ?: "",

                        // confidence not stored — compute fallback
                        confidence = doc.getDouble("confidence")
                            ?: 0.0,

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

        val acceptedData = hashMapOf(
            "label" to item.label,
            "videoUrl" to item.videoUrl,
            "uploaderEmail" to item.uploaderEmail,
            "confidence" to item.confidence,
            "createdAt" to item.createdAt,
            "reviewDecision" to "accepted",
            "reviewedAt" to Timestamp.now()
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

        val rejectedData = hashMapOf(
            "label" to item.label,
            "videoUrl" to item.videoUrl,
            "uploaderEmail" to item.uploaderEmail,
            "confidence" to item.confidence,
            "createdAt" to item.createdAt,
            "reviewDecision" to "rejected",
            "reviewedAt" to Timestamp.now()
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