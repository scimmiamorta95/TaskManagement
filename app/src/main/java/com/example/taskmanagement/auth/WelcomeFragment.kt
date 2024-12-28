package com.example.taskmanagement.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.taskmanagement.R
import com.example.taskmanagement.databinding.FragmentWelcomeBinding
import com.google.firebase.auth.FirebaseAuth

class WelcomeFragment : Fragment() {

    private lateinit var binding: FragmentWelcomeBinding
    private lateinit var mAuth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        FragmentWelcomeBinding.inflate(inflater, container, false).also { binding = it }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAuth = FirebaseAuth.getInstance()

        val currentUser = mAuth.currentUser
        if (currentUser != null) {
            findNavController().navigate(R.id.action_welcomeFragment_to_homeFragment)
        } else {
            setupListeners()
        }
    }

    private fun setupListeners() {
        binding.loginButton.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_welcomeFragment_to_loginFragment)
            } catch (e: Exception) {
                Log.e("WelcomeFragment", "Errore durante la navigazione", e)
            }
        }

        binding.registerButton.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_welcomeFragment_to_registerFragment)
            } catch (e: Exception) {
                Log.e("WelcomeFragment", "Errore durante la navigazione", e)
            }
        }
    }

}
