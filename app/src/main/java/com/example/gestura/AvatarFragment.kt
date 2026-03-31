package com.example.gestura

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class AvatarFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_avatar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val input = view.findViewById<EditText>(R.id.etAvatarInput)
        val status = view.findViewById<TextView>(R.id.txtAvatarStatus)
        val submit = view.findViewById<Button>(R.id.btnSubmitAvatar)

        submit.setOnClickListener {
            val text = input.text.toString().trim()

            if (text.isEmpty()) {
                input.error = "Please enter text"
                return@setOnClickListener
            }

            status.text = "Generating..."
            Toast.makeText(requireContext(), "Generating avatar", Toast.LENGTH_SHORT).show()

            // TODO: connect to backend / model / API
            // Example after success:
            // status.text = "Avatar ready"
        }
    }
}