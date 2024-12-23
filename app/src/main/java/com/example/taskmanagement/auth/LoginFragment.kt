package com.example.taskmanagement.auth

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.taskmanagement.R
import com.example.taskmanagement.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class LoginFragment : Fragment() {
    private lateinit var mAuth: FirebaseAuth
    private lateinit var binding: FragmentLoginBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        init()

        val currentUser = mAuth.currentUser
        if (currentUser != null) {
            findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
        }

        binding.registerBtn.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        binding.loginBtn.setOnClickListener {
            val email = binding.username.text.toString()
            val pass = binding.password.text.toString()

            when {
                email.isEmpty() || pass.isEmpty() -> {
                    Toast.makeText(
                        requireContext(), getString(R.string.enter_all_field),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                else -> {
                    loginUser(email, pass)
                }
            }
        }

        binding.username.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {

                val email = binding.username.text.toString()
                val pass = binding.password.text.toString()

                when {
                    email.isEmpty() || pass.isEmpty() -> {
                        Toast.makeText(
                            requireContext(), getString(R.string.enter_all_field),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    else -> {
                        loginUser(email, pass)
                    }
                }
                true
            } else {
                false
            }
        }

        binding.password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val email = binding.username.text.toString()
                val pass = binding.password.text.toString()
                when {
                    email.isEmpty() || pass.isEmpty() -> {
                        Toast.makeText(
                            requireContext(), getString(R.string.enter_all_field),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    else -> {
                        loginUser(email, pass)
                    }
                }
                true
            } else {
                false
            }
        }
    }


    private fun loginUser(email: String, pass: String) {
        mAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val userId = mAuth.currentUser?.uid

                userId?.let { it ->
                    FirebaseFirestore.getInstance().collection("users")
                        .document(it)
                        .get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                val user = document.toObject(User::class.java)
                                user?.let {
                                    saveUserLocally(it)
                                    Toast.makeText(
                                        requireContext(), getString(R.string.login_succeffully),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    val role = document.getString("role")
                                    saveUserRoleLocally(role)
                                    findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                                }
                            } else {
                                Toast.makeText(
                                    requireContext(), getString(R.string.user_not_found),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            val errorMessage = getString(R.string.error_fetching_data, e.message)
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT)
                                .show()
                        }

                }
            } else {
                val errorMessage = task.exception?.message ?: getString(R.string.login_error)
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }

        }
    }

    private fun saveUserLocally(user: User) {
        val sharedPreferences =
            requireContext().getSharedPreferences("TaskManagerPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val userJson = gson.toJson(user)
        editor.putString("user", userJson)
        editor.apply()
    }
    private fun saveUserRoleLocally(role: String?) {
        val sharedPreferences =
            requireContext().getSharedPreferences("TaskManagerPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("role", role)
        editor.apply()
    }


    private fun init() {
        mAuth = FirebaseAuth.getInstance()
    }
}
