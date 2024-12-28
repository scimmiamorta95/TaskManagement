package com.example.taskmanagement.task

import android.content.Context
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
import com.example.taskmanagement.databinding.FragmentModifyTaskBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ModifyTaskFragment : Fragment() {

    private lateinit var binding: FragmentModifyTaskBinding
    private lateinit var mAuth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private var taskId: String? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentModifyTaskBinding.inflate(inflater, container, false)
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

        taskId = arguments?.getString("taskId")
        if (taskId == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.error_task_not_found),
                Toast.LENGTH_SHORT
            ).show()
            findNavController().popBackStack()
            return
        }
        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }

        loadTaskData()
    }

    private fun loadTaskData() {
        taskId?.let { id ->
            db.collection("tasks").document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val task = document.toObject(Task::class.java)
                        task?.let {
                            populateFields(it)
                        }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.error_task_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                        findNavController().popBackStack()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_task_not_modify, exception.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun populateFields(task: Task) {
        binding.taskName.setText(task.name)
        binding.taskDescription.setText(task.description)
        binding.expirationInput.setText(task.deadline)
        binding.assignedDropdown.setText(task.assignedTo, false)

        loadUsers(binding.assignedDropdown)


        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_date))
            .build()

        binding.expirationInput.setOnClickListener {
            datePicker.show(childFragmentManager, "DATE_PICKER")
        }

        datePicker.addOnPositiveButtonClickListener {
            binding.expirationInput.setText(datePicker.headerText)
        }

        binding.updateTaskButton.setOnClickListener {
            val taskName = binding.taskName.text.toString()
            val taskDescription = binding.taskDescription.text.toString()
            val expirationDate = binding.expirationInput.text.toString()
            val assignedTo = binding.assignedDropdown.text.toString()

            if (taskName.isNotEmpty() && taskDescription.isNotEmpty() && expirationDate.isNotEmpty()) {
                val updatedTask = Task(
                    name = taskName,
                    description = taskDescription,
                    deadline = expirationDate,
                    assignedTo = assignedTo,
                    createdBy = mAuth.currentUser?.email
                )

                db.collection("tasks").document(taskId!!)
                    .set(updatedTask)
                    .addOnSuccessListener {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.task_updated_successfully),
                            Toast.LENGTH_SHORT
                        ).show()

                        findNavController().popBackStack()
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.error_updating_task, exception.message),
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
        val sharedPrefs =
            requireContext().getSharedPreferences("TaskManagerPrefs", Context.MODE_PRIVATE)
        val role = sharedPrefs.getString("role", "defaultRole")
        if (role != null) {
            val query = when (role) {
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
    }
}
