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
import com.example.taskmanagement.databinding.FragmentAddSubTaskBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddSubTaskFragment : Fragment() {

    private lateinit var binding: FragmentAddSubTaskBinding
    private lateinit var mAuth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private var taskId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        taskId = arguments?.getString("taskId")
        binding = FragmentAddSubTaskBinding.inflate(inflater, container, false)
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

        setupDropdownMenus()
        initializeViews()
    }

    private fun setupDropdownMenus() {
        setPriorityDropdown()
        setStatusDropdown()
    }

    private fun setPriorityDropdown() {
        val priorityOptions = resources.getStringArray(R.array.priority_filters).toList()
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_item,
            priorityOptions
        )
        binding.priorityDropdown.setAdapter(adapter)

        if (priorityOptions.isNotEmpty()) {
            binding.priorityDropdown.setText(priorityOptions[0], false)
        }
    }

    private fun setStatusDropdown() {
        val statusOptions = resources.getStringArray(R.array.status_filters).toList()
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_item,
            statusOptions
        )
        binding.statusDropdown.setAdapter(adapter)

        if (statusOptions.isNotEmpty()) {
            binding.statusDropdown.setText(statusOptions[0], false)
        }
    }


    private fun initializeViews() {
        val taskNameEditText = binding.subtaskName
        val taskDescriptionEditText = binding.subtaskDescription
        val deadline = binding.subtaskDeadline
        val assignedToDropdown = binding.assignedDropdown
        val addSubTaskButton = binding.addSubtaskButton
        val progressSlider = binding.progressSlider

        loadUsers(assignedToDropdown)

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_date))
            .build()

        deadline.setOnClickListener {
            datePicker.show(childFragmentManager, "DATE_PICKER")
        }

        datePicker.addOnPositiveButtonClickListener { selection ->
            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val formattedDate = sdf.format(Date(selection))
            deadline.setText(formattedDate)
        }


        addSubTaskButton.setOnClickListener {
            val taskName = taskNameEditText.text.toString()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val taskDescription = taskDescriptionEditText.text.toString()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val priorityText = binding.priorityDropdown.text.toString()
            val priority = mapPriorityToNumber(priorityText)
            val deadlineDate = deadline.text.toString()
            val progress = progressSlider.value.toInt()
            val statusText = binding.statusDropdown.text.toString()
            val status = mapStatusToNumber(statusText)
            val assignedTo = assignedToDropdown.text.toString()
            val createdBy = mAuth.currentUser?.email

            if (taskName.isNotEmpty() && taskDescription.isNotEmpty() && deadlineDate.isNotEmpty()) {
                val newSubtask = SubTask(
                    name = taskName,
                    description = taskDescription,
                    deadline = deadlineDate,
                    assignedTo = assignedTo,
                    createdBy = createdBy,
                    priority = priority,
                    progress = progress,
                    status = status
                )

                db.collection("tasks")
                    .document(taskId!!)
                    .collection("subTasks")
                    .add(newSubtask)
                    .addOnSuccessListener {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.subtask_added_successfully),
                            Toast.LENGTH_SHORT
                        ).show()

                        findNavController().popBackStack()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.failed_to_add_subtask, e.message ?: "Unknown error"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.please_fill_all_fields),
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
                    .addOnFailureListener {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.role_not_found),
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
        }.addOnFailureListener {
            Toast.makeText(
                requireContext(),
                getString(R.string.error_retrieving_role),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun mapPriorityToNumber(priorityText: String): Int {
        return when (priorityText.lowercase()) {
            "high" -> 2
            "medium" -> 1
            "low" -> 0
            else -> 0

        }
    }

    private fun mapStatusToNumber(statusText: String): Int {
        return when (statusText.lowercase()) {
            "Todo" -> 0
            "Assigned" -> 1
            "Completed" -> 2
            else -> 0
        }
    }


}
