package com.example.taskmanagement.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.taskmanagement.R
import com.example.taskmanagement.databinding.FragmentProfileBinding
import com.example.taskmanagement.task.HomeFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

class ProfileFragment : Fragment() {

    private lateinit var binding: FragmentProfileBinding
    private var firebaseAuth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null

    private var taskList: MutableList<String> = mutableListOf()
    private var taskAdapter: ArrayAdapter<String>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentProfileBinding.inflate(inflater, container, false)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        taskAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, taskList)
        binding.assignedTasksList.adapter = taskAdapter

        loadUserData()

        binding.buttonLogout.setOnClickListener {
            firebaseAuth?.signOut()
            Toast.makeText(context, "Logout effettuato", Toast.LENGTH_SHORT).show()
            HomeFragment.deleteSavedTasksFiles(requireContext())
            findNavController().popBackStack(R.id.welcomeFragment, false)
        }

        return binding.root
    }

    private fun loadUserData() {
        val userId = firebaseAuth!!.currentUser!!.uid

        binding.userName.text = firebaseAuth!!.currentUser!!.email
        binding.welcomeMessage.text = "Benvenuto, ${firebaseAuth!!.currentUser!!.email}"

        firestore!!.collection("tasks")
            .whereEqualTo("assignedTo", userId)
            .get()
            .addOnSuccessListener { queryDocumentSnapshots: QuerySnapshot ->
                taskList.clear()
                for (snapshot in queryDocumentSnapshots) {
                    val taskName = snapshot.getString("taskName")
                    val deadline = snapshot.getString("deadline")
                    taskList.add("$taskName - Scadenza: $deadline")
                }
                taskAdapter!!.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(
                    context,
                    "Errore nel caricamento dei task",
                    Toast.LENGTH_SHORT
                ).show()
            }

        firestore!!.collection("tasks")
            .whereEqualTo("assignedTo", userId)
            .get()
            .addOnSuccessListener { queryDocumentSnapshots: QuerySnapshot ->
                var completed = 0
                var todo = 0
                var assigned = 0

                for (snapshot in queryDocumentSnapshots) {
                    val status = snapshot.getString("status")
                    if (status == "Completed") {
                        completed++
                    } else if (status == "Assigned") {
                        assigned++
                    } else if (status == "Todo") {
                        todo++
                    }
                }

                binding.completedProjects.text = completed.toString()
                binding.projectsToStart.text = todo.toString()
                binding.averageCompletionTime.text = assigned.toString() // Projects Assigned
            }
            .addOnFailureListener {
                Toast.makeText(
                    context,
                    "Errore nel caricamento delle statistiche",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

}
