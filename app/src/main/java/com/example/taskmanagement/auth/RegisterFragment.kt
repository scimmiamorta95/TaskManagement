package com.example.taskmanagement.auth

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.taskmanagement.R
import com.example.taskmanagement.databinding.FragmentRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class RegisterFragment : Fragment() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var binding: FragmentRegisterBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAuth = FirebaseAuth.getInstance()
        binding.nextBtn.isClickable = true
        binding.textViewSignIn.isClickable = true


        binding.textViewSignIn.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        binding.nextBtn.setOnClickListener {
            val email = binding.emailEt.text.toString().trim()
            val pass = binding.passEt.text.toString().trim()
            val verifyPass = binding.verifyPassEt.text.toString().trim()

            if (validateInputs(email, pass, verifyPass)) {
                registerUser(email, pass)
            }
        }
    }

    private fun validateInputs(email: String, pass: String, verifyPass: String): Boolean {
        return when {
            email.isEmpty() || pass.isEmpty() || verifyPass.isEmpty() -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.fill_all_field),
                    Toast.LENGTH_SHORT
                ).show()
                false
            }

            !isPasswordStrong(pass) -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.password_not_strong_enough),
                    Toast.LENGTH_SHORT
                ).show()
                false
            }

            pass != verifyPass -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.passwords_do_not_match),
                    Toast.LENGTH_SHORT
                ).show()
                false
            }

            else -> true
        }
    }

    private fun isPasswordStrong(password: String): Boolean {
        val passwordPattern =
            "^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#\$%^&*(),.?\":{}|<>])[A-Za-z\\d!@#\$%^&*(),.?\":{}|<>]{8,}$"
        return password.matches(passwordPattern.toRegex())
    }

    private fun registerUser(email: String, pass: String) {
        mAuth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val userId = mAuth.currentUser?.uid
                val user = User(email = email, password = pass)
                userId?.let {
                    FirebaseFirestore.getInstance().collection("users")
                        .document(it)
                        .set(user)
                        .addOnSuccessListener {
                            saveUserLocally(user)
                            Toast.makeText(requireContext(), getString(R.string.registration_success), Toast.LENGTH_SHORT).show()
                            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
                        }
                        .addOnFailureListener {
                            Toast.makeText(requireContext(), getString(R.string.error_fetching_data), Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.error_fetching_data), Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun saveUserLocally(user: User) {
        val sharedPreferences =
            requireContext().getSharedPreferences("TaskManagerPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        editor.putString("user", gson.toJson(user))
        editor.apply()
    }

}
