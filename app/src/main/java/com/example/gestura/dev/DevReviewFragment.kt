package com.example.gestura.dev

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.gestura.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DevReviewFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var list: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dev_review_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        list = view.findViewById(R.id.pendingList)

        lifecycleScope.launch {
            loadPending()
        }
    }

    private suspend fun loadPending() {
        list.removeAllViews()

        val snap = db.collection("asl_pending").get().await()

        for (doc in snap) {
            val item = layoutInflater.inflate(R.layout.item_pending_sample, list, false)

            val tvWord = item.findViewById<TextView>(R.id.tvWord)
            val tvUser = item.findViewById<TextView>(R.id.tvUser)
            val btnAccept = item.findViewById<Button>(R.id.btnAccept)
            val btnReject = item.findViewById<Button>(R.id.btnReject)

            val data = doc.data
            tvWord.text = data["word"].toString()
            tvUser.text = data["userEmail"].toString()

            btnAccept.setOnClickListener { accept(doc.id, data) }
            btnReject.setOnClickListener { reject(doc.id) }

            list.addView(item)
        }
    }

    private fun accept(docId: String, data: Map<String, Any>) {
        lifecycleScope.launch {
            db.collection("asl_accepted").document(docId).set(data).await()
            db.collection("asl_pending").document(docId).delete().await()
            loadPending()
        }
    }

    private fun reject(docId: String) {
        lifecycleScope.launch {
            db.collection("asl_pending").document(docId).delete().await()
            loadPending()
        }
    }
}
