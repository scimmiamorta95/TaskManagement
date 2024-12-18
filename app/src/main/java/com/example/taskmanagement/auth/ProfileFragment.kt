package com.example.taskmanagement.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.taskmanagement.R
import com.example.taskmanagement.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var binding: FragmentProfileBinding

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentUser = auth.currentUser
        binding.userName.text = getString(R.string.welcome_message, currentUser?.email ?: "Utente")
        binding.userMail.text = currentUser?.email

        // Conta i task per stato
        loadTaskCounts()


        // Logout
        binding.buttonLogout.setOnClickListener {
            auth.signOut()
            findNavController().navigate(R.id.action_profileFragment_to_welcomeFragment)
        }
    }

    private fun loadTaskCounts() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Utente non autenticato.", Toast.LENGTH_SHORT).show()
            return
        }

        val userEmail = currentUser.email ?: return
        countTasksByState(
            userEmail = userEmail,
            onSuccess = { counts ->
                binding.initialTask.text = counts["Initial"]?.toString() ?: "0"
                binding.inprogressTask.text = counts["inProgress"]?.toString() ?: "0"
                binding.completeTask.text = counts["Complete"]?.toString() ?: "0"
            },
            onFailure = { exception ->
                Toast.makeText(
                    requireContext(),
                    "Errore nel caricamento dei task: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        countAllTasks(
            userEmail = userEmail,
            onSuccess = { totalCount ->
                binding.assignedTaskCount.text = totalCount.toString()
            },
            onFailure = { exception ->
                Toast.makeText(
                    requireContext(),
                    "Errore nel caricamento dei task totali: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        val userUID = currentUser.uid
        firestore.collection("users")
            .document(userUID)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val role = documentSnapshot.getString("role") ?: getString(R.string.role_not_assigned)
                    binding.group.text = role
                } else {
                    binding.group.text = getString(R.string.role_not_found)
                }

            }
            .addOnFailureListener { exception ->
                val errorMessage = getString(R.string.error_loading_role, exception.message)
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
            }

    }

    private fun countTasksByState(
        userEmail: String,
        onSuccess: (Map<String, Int>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val states = listOf(
            getString(R.string.state_initial),
            getString(R.string.state_in_progress),
            getString(R.string.state_complete)
        )

        val taskCounts = mutableMapOf<String, Int>()
        val firestore = FirebaseFirestore.getInstance()

        var queriesCompleted = 0
        var queryFailed = false

        for (state in states) {
            firestore.collection("tasks")
                .whereEqualTo("assignedTo", userEmail)
                .whereEqualTo("state", state)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!queryFailed) {
                        taskCounts[state] = querySnapshot.size()
                        queriesCompleted++

                        if (queriesCompleted == states.size) {
                            onSuccess(taskCounts)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    if (!queryFailed) {
                        queryFailed = true
                        onFailure(exception)
                    }
                }
        }
    }

    private fun countAllTasks(
        userEmail: String,
        onSuccess: (Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        firestore.collection("tasks")
            .whereEqualTo("assignedTo", userEmail)
            .get()
            .addOnSuccessListener { querySnapshot ->
                onSuccess(querySnapshot.size())
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

}
