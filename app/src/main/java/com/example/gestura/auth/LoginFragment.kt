package com.example.gestura.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.gestura.R
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val emailEt  = view.findViewById<EditText>(R.id.etEmail)
        val passEt   = view.findViewById<EditText>(R.id.etPassword)
        val signIn   = view.findViewById<Button>(R.id.btnSignIn)
        val signUp   = view.findViewById<Button>(R.id.btnSignUp)
        val reset    = view.findViewById<TextView>(R.id.tvReset)
        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val errorTv  = view.findViewById<TextView>(R.id.tvError)

        fun setLoading(loading: Boolean) {
            progress.visibility = if (loading) View.VISIBLE else View.GONE
            signIn.isEnabled = !loading
            signUp.isEnabled = !loading
        }

        fun clearError() { errorTv.text = "" }

        emailEt.setOnFocusChangeListener { _, _ -> clearError() }
        passEt.setOnFocusChangeListener { _, _ -> clearError() }

        // ================= SIGN IN =================
        signIn.setOnClickListener {
            clearError()

            val email = emailEt.text.toString().trim()
            val pass  = passEt.text.toString()

            if (email.isEmpty()) {
                errorTv.text = "Email required"
                return@setOnClickListener
            }
            if (pass.isEmpty()) {
                errorTv.text = "Password required"
                return@setOnClickListener
            }

            Log.i("LOGIN", "Attempting sign in for $email")
            setLoading(true)

            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    setLoading(false)

                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        Log.i("LOGIN", "Sign in success uid=${user?.uid}")
                        Toast.makeText(requireContext(), "Welcome back!", Toast.LENGTH_SHORT).show()
                        goToHome()
                    } else {
                        val e = task.exception
                        val msg = e?.localizedMessage ?: "Sign-in failed"
                        errorTv.text = msg
                        Log.e("LOGIN", "Sign in failed: ${e?.javaClass?.name}: $msg", e)
                    }
                }
        }

        // ================= SIGN UP =================
        signUp.setOnClickListener {
            clearError()

            val email = emailEt.text.toString().trim()
            val pass  = passEt.text.toString()

            if (email.isEmpty()) {
                errorTv.text = "Email required"
                return@setOnClickListener
            }
            if (pass.length < 6) {
                errorTv.text = "Password must be ≥ 6 characters"
                return@setOnClickListener
            }

            Log.i("LOGIN", "Attempting sign up for $email")
            setLoading(true)

            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    setLoading(false)

                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        Log.i("LOGIN", "Sign up success uid=${user?.uid}")
                        Toast.makeText(requireContext(), "Account created!", Toast.LENGTH_SHORT).show()
                        goToHome()
                    } else {
                        val e = task.exception
                        val msg = e?.localizedMessage ?: "Sign-up failed"
                        errorTv.text = msg
                        Log.e("LOGIN", "Sign up failed: ${e?.javaClass?.name}: $msg", e)
                    }
                }
        }

        // ================= RESET PASSWORD =================
        reset.setOnClickListener {
            clearError()
            val email = emailEt.text.toString().trim()
            if (email.isEmpty()) {
                errorTv.text = "Enter your email first"
                return@setOnClickListener
            }

            setLoading(true)
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    setLoading(false)

                    if (task.isSuccessful) {
                        Toast.makeText(requireContext(), "Reset email sent", Toast.LENGTH_SHORT).show()
                        Log.i("LOGIN", "Password reset email sent to $email")
                    } else {
                        val e = task.exception
                        val msg = e?.localizedMessage ?: "Reset failed"
                        errorTv.text = msg
                        Log.e("LOGIN", "Reset failed: ${e?.javaClass?.name}: $msg", e)
                    }
                }
        }
    }

    private fun goToHome() {
        val navController = findNavController()
        Log.d(
            "NAV",
            "currentDest=${navController.currentDestination?.id} " +
                    "expectedLoginId=${R.id.loginFragment}, navigating to action_login_to_home"
        )

        if (navController.currentDestination?.id == R.id.loginFragment) {
            navController.navigate(R.id.action_login_to_home)
        } else {
            Log.w("NAV", "Not on loginFragment, skip navigation")
        }
    }
}
