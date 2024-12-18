package com.example.taskmanagement


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_main)

        val navController = findNavController(R.id.nav_host_fragment)

        val bottomNavMenu: BottomNavigationView = findViewById(R.id.bottomNavMenu)
        bottomNavMenu.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.welcomeFragment, R.id.loginFragment, R.id.registerFragment, R.id.forgotPasswordFragment -> {
                    bottomNavMenu.visibility = android.view.View.GONE
                }

                else -> {
                    bottomNavMenu.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

}
