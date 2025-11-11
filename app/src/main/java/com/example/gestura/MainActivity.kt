package com.example.gestura

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.gestura.util.ThemeHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

// MainActivity.kt (keep your imports)
class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("gestura_settings", MODE_PRIVATE)
        val savedTheme = prefs.getString("theme", "auto") ?: "auto"
        ThemeHelper.apply(savedTheme)

        super.onCreate(savedInstanceState)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        // ✅ Only choose start destination once
        if (savedInstanceState == null) {
            val user = auth.currentUser
            val isSignedIn = user != null /* or: user?.isEmailVerified == true */
            navController.graph = navController.navInflater.inflate(R.navigation.nav_graph).apply {
                setStartDestination(if (isSignedIn) R.id.aslFragment else R.id.loginFragment)
            }
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, dest, _ ->
            bottomNav.visibility = if (dest.id == R.id.loginFragment) View.GONE else View.VISIBLE
        }
        bottomNav.setOnItemReselectedListener { /* no-op */ }
    }
}

// Optional helper: call from anywhere to logout and jump to Login (clears stack)
fun AppCompatActivity.signOutAndReturnToLogin() {
    FirebaseAuth.getInstance().signOut()
    val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
    val nav = navHost.navController
    nav.navigate(R.id.loginFragment, null, androidx.navigation.navOptions {
        popUpTo(0) { inclusive = true } // clear entire back stack
    })
}
