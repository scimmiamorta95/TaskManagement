package com.example.taskmanagement.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.example.taskmanagement.R
import com.example.taskmanagement.databinding.FragmentForgotPasswordBinding
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordFragment : Fragment() {
    private lateinit var navController: NavController
    private lateinit var mAuth: FirebaseAuth
    private lateinit var binding: FragmentForgotPasswordBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        init(view)

        binding.forgotPasswordButton.setOnClickListener {
            val email = binding.username.text.toString()

            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "Per favore, inserisci l'email.", Toast.LENGTH_SHORT).show()
            } else {
                sendPasswordResetEmail(email)
            }
        }
    }

    private fun sendPasswordResetEmail(email: String) {
        binding.loading.visibility = View.VISIBLE
        mAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                binding.loading.visibility = View.INVISIBLE
                if (task.isSuccessful) {
                    Toast.makeText(
                        requireContext(),
                        "Email di reset inviata con successo.",
                        Toast.LENGTH_SHORT
                    ).show()
                    navController.navigate(R.id.action_forgotPasswordFragment_to_welcomeFragment)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Errore: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun init(view: View) {
        navController = Navigation.findNavController(view)
        mAuth = FirebaseAuth.getInstance()
    }
}
