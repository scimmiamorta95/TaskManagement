package com.example.taskmanagement.task

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.taskmanagement.R
import com.example.taskmanagement.databinding.FragmentAddTaskBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddTaskFragment : Fragment() {

    private lateinit var binding: FragmentAddTaskBinding
    private lateinit var mAuth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAddTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAuth = FirebaseAuth.getInstance()
        val currentUser = mAuth.currentUser

        if (currentUser == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.error_loading_users),
                Toast.LENGTH_SHORT
            ).show()
            findNavController().navigate(R.id.profileFragment)
            return
        }

        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }

        checkUserRoleAndProceed(currentUser.uid)
    }

    private fun checkUserRoleAndProceed(userId: String) {
        val userRoleRef = db.collection("users").document(userId)
        userRoleRef.get().addOnSuccessListener { document ->
            val userRole = document.getString("role")

            if (userRole == "Dev") {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.not_authorized),
                    Toast.LENGTH_LONG
                ).show()
                findNavController().navigate(R.id.profileFragment)
            } else {
                initializeViews()
            }
        }.addOnFailureListener { exception ->
            Toast.makeText(
                requireContext(),
                getString(R.string.failed_to_retrieve_user_role, exception.message),
                Toast.LENGTH_SHORT
            ).show()

        }
    }

    private fun initializeViews() {
        val taskNameEditText = binding.taskName
        val taskDescriptionEditText = binding.taskDescription
        val expirationInput = binding.expirationInput
        val assignedToDropdown = binding.assignedDropdown
        val addTaskButton = binding.addTaskButton

        loadUsers(assignedToDropdown)

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_date))
            .build()

        expirationInput.setOnClickListener {
            datePicker.show(childFragmentManager, "DATE_PICKER")
        }

        datePicker.addOnPositiveButtonClickListener { selection ->
            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val formattedDate = sdf.format(Date(selection))
            expirationInput.setText(formattedDate)
        }


        addTaskButton.setOnClickListener {
            val taskName = taskNameEditText.text.toString()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val taskDescription = taskDescriptionEditText.text.toString()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val expirationDate = expirationInput.text.toString()
            val assignedTo = assignedToDropdown.text.toString()
            val createdBy = mAuth.currentUser?.email

            if (taskName.isNotEmpty() && taskDescription.isNotEmpty() && expirationDate.isNotEmpty()) {
                val task = Task(
                    name = taskName,
                    description = taskDescription,
                    deadline = expirationDate,
                    assignedTo = assignedTo,
                    createdBy = createdBy,
                )

                db.collection("tasks").add(task)
                    .addOnSuccessListener {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.task_created_successfully),
                            Toast.LENGTH_SHORT
                        ).show()

                        findNavController().popBackStack()
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.error_creating_task, exception.message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.all_fields_required),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    }

    private fun loadUsers(assignedToDropdown: AutoCompleteTextView) {
        val currentUser = mAuth.currentUser ?: return
        val userRoleRef = db.collection("users").document(currentUser.uid)

        userRoleRef.get().addOnSuccessListener { document ->
            val userRole = document.getString("role")

            if (userRole != null) {
                val query = when (userRole) {
                    "PM" -> db.collection("users").whereEqualTo("role", "PL")
                    "PL" -> db.collection("users").whereEqualTo("role", "Dev")
                    else -> db.collection("users").whereEqualTo("role", "Dev")
                }

                query.get()
                    .addOnSuccessListener { result ->
                        val userList = mutableListOf<String>()

                        for (doc in result) {
                            val userEmail = doc.getString("email")
                            userEmail?.let { userList.add(it) }
                        }

                        val adapter = ArrayAdapter(
                            requireContext(),
                            R.layout.dropdown_item,
                            userList
                        )
                        assignedToDropdown.setAdapter(adapter)
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.failed_to_load_users, exception.message),
                            Toast.LENGTH_SHORT
                        ).show()

                    }
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.user_role_not_found),
                    Toast.LENGTH_SHORT
                ).show()

            }
        }.addOnFailureListener { exception ->
            Toast.makeText(
                requireContext(),
                getString(R.string.failed_to_retrieve_user_role, exception.message),
                Toast.LENGTH_SHORT
            ).show()

        }
    }


}
