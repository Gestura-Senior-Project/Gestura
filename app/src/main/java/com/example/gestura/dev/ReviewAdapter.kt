package com.example.gestura.dev

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.example.gestura.R

class ReviewAdapter(
    private val items: MutableList<ReviewContribution>
) : RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>() {

    inner class ReviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val videoView: VideoView = view.findViewById(R.id.videoView)
        val tvLabel: TextView = view.findViewById(R.id.tvLabel)
        val tvUploader: TextView = view.findViewById(R.id.tvUploader)
        val tvConfidence: TextView = view.findViewById(R.id.tvConfidence)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review_contribution, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val item = items[position]

        holder.tvLabel.text = item.label
        holder.tvUploader.text = "Uploaded by: ${item.uploaderEmail}"
        val confidenceText = if (item.confidence <= 1.0) {
            "${(item.confidence * 100).toInt()}%"
        } else {
            "${item.confidence.toInt()}%"
        }
        holder.tvConfidence.text = "Confidence: $confidenceText"

        if (item.videoUrl.isNotBlank()) {
            holder.videoView.setVideoURI(Uri.parse(item.videoUrl))
            holder.videoView.setOnPreparedListener { mp ->
                mp.isLooping = true
                holder.videoView.start()
            }
        } else {
            holder.videoView.stopPlayback()
        }
    }

    override fun getItemCount(): Int = items.size

    fun getItem(position: Int): ReviewContribution = items[position]

    fun removeAt(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    fun setItems(newItems: List<ReviewContribution>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}