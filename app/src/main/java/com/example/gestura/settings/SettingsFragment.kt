package com.example.gestura.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.example.gestura.R
import com.example.gestura.util.ThemeHelper
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    private val vm: SettingsViewModel by viewModels()
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Profile header
        val tvName = view.findViewById<TextView>(R.id.tvName)
        val tvEmail = view.findViewById<TextView>(R.id.tvEmail)
        val avatar = view.findViewById<TextView>(R.id.tvAvatarInitial)

        val user = auth.currentUser
        val displayName = user?.displayName ?: user?.email?.substringBefore("@") ?: "—"
        tvName.text = displayName
        tvEmail.text = user?.email ?: "—"
        avatar.text = (displayName.firstOrNull() ?: '?').uppercase()

        // Editable profile (local only; wire to backend later)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etProfileEmail = view.findViewById<EditText>(R.id.etProfileEmail)
        etName.setText(displayName)
        etProfileEmail.setText(user?.email ?: "")
        view.findViewById<View>(R.id.btnSaveProfile).setOnClickListener {
            Toast.makeText(requireContext(), "Profile updated successfully!", Toast.LENGTH_SHORT).show()
        }

        // Preferences
        val spTheme = view.findViewById<Spinner>(R.id.spTheme)
        spTheme.adapter = ArrayAdapter.createFromResource(
            requireContext(), R.array.settings_themes, android.R.layout.simple_spinner_dropdown_item
        )

        vm.theme.observe(viewLifecycleOwner) { theme ->
            val idx = resources.getStringArray(R.array.settings_themes_values).indexOf(theme)
            if (idx >= 0 && spTheme.selectedItemPosition != idx) spTheme.setSelection(idx)
        }

        spTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val value = resources.getStringArray(R.array.settings_themes_values)[pos] // "light"|"dark"|"auto"
                vm.setTheme(value)
                ThemeHelper.apply(value) // applies Light/Dark/Auto
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // AI Model
        val swAutoUpdate = view.findViewById<Switch>(R.id.swAutoUpdate)
        val swMaskSync = view.findViewById<Switch>(R.id.swMaskSync)
        vm.autoUpdate.observe(viewLifecycleOwner) { swAutoUpdate.isChecked = it }
        vm.maskSync.observe(viewLifecycleOwner) { swMaskSync.isChecked = it }
        swAutoUpdate.setOnCheckedChangeListener { _, b -> vm.setAutoUpdate(b) }
        swMaskSync.setOnCheckedChangeListener { _, b -> vm.setMaskSync(b) }

        view.findViewById<View>(R.id.rowUpdateModel).setOnClickListener {
            Toast.makeText(requireContext(), "Checking for model update…", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.rowSyncMasks).setOnClickListener {
            Toast.makeText(requireContext(), "Syncing masks…", Toast.LENGTH_SHORT).show()
        }

        // Stats + Developer (safe when no DB)
        val tvContrib = view.findViewById<TextView>(R.id.tvContrib)
        val tvAccuracy = view.findViewById<TextView>(R.id.tvAccuracy)
        val tvStatsHint = view.findViewById<TextView>(R.id.tvStatsHint)
        val tvDevHint = view.findViewById<TextView>(R.id.tvDevHint)
        val swDevMode = view.findViewById<Switch>(R.id.swDevMode)
        val rowReview = view.findViewById<View>(R.id.rowReviewContrib)

        fun hasStats(): Boolean = vm.totalContrib.value != null && vm.accuracy.value != null
        fun qualifies(): Boolean {
            val c = vm.totalContrib.value ?: return false
            val a = vm.accuracy.value ?: return false
            return c >= 47 && a >= 90
        }
        fun updateDevVisibility() {
            val has = hasStats()
            swDevMode.isEnabled = has && qualifies()
            tvDevHint.text = if (has) {
                if (qualifies()) "Review contributions" else "Need ≥47 contributions and ≥90% accuracy"
            } else {
                "Connect database to enable"
            }
            rowReview.isVisible = swDevMode.isChecked && has
        }
        fun updateStatsHint() {
            val has = hasStats()
            if (has) {
                tvStatsHint.text = ""
                tvStatsHint.visibility = View.GONE
            } else {
                tvStatsHint.text = "Connect your database to view stats and unlock Developer Mode."
                tvStatsHint.visibility = View.VISIBLE
            }
        }

        vm.totalContrib.observe(viewLifecycleOwner) { c ->
            tvContrib.text = c?.toString() ?: "—"
            updateDevVisibility(); updateStatsHint()
        }
        vm.accuracy.observe(viewLifecycleOwner) { a ->
            tvAccuracy.text = a?.let { "$it%" } ?: "—"
            updateDevVisibility(); updateStatsHint()
        }
        vm.devMode.observe(viewLifecycleOwner) { enabled ->
            swDevMode.isChecked = enabled
            rowReview.isVisible = enabled && hasStats()
        }

        swDevMode.setOnCheckedChangeListener { _, _ -> vm.tryToggleDevMode() }
        rowReview.setOnClickListener {
            Toast.makeText(requireContext(), "Open dev review screen (coming soon)", Toast.LENGTH_SHORT).show()
        }

        // ✅ Logout: sign out + navigate to Login and clear the stack
        view.findViewById<View>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()

            val navHost = requireActivity()
                .supportFragmentManager
                .findFragmentById(R.id.nav_host) as NavHostFragment
            val nav = navHost.navController

            nav.navigate(R.id.loginFragment, null, navOptions {
                popUpTo(0) { inclusive = true } // clear entire back stack
            })
        }
    }
}
