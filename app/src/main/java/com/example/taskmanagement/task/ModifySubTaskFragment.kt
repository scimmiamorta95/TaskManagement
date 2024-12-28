package com.example.taskmanagement.task

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.taskmanagement.R
import com.example.taskmanagement.databinding.FragmentModifySubTaskBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ModifySubTaskFragment : Fragment() {

    private lateinit var binding: FragmentModifySubTaskBinding
    private lateinit var mAuth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private var subtaskId: String? = null
    private var taskId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentModifySubTaskBinding.inflate(inflater, container, false)
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

        taskId = arguments?.getString("taskID")
        subtaskId = arguments?.getString("subtaskId")

        if (subtaskId == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.error_subtask_not_found),
                Toast.LENGTH_SHORT
            ).show()
            findNavController().popBackStack()
            return
        }
        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }

        loadSubtaskData()
    }

    private fun loadSubtaskData() {
        db.collection("tasks")
            .document(taskId ?: "")
            .collection("subTasks")
            .document(subtaskId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val subtask = document.toObject(SubTask::class.java)
                    subtask?.let { populateFields(it) }
                } else {
                    Toast.makeText(
                        requireContext(),
                        R.string.error_subtask_not_found,
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().popBackStack()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("ModifySubTaskFragment", "Error loading document", exception)
                Toast.makeText(requireContext(), R.string.error_task_not_modify, Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun populateFields(subtask: SubTask) {
        binding.subtaskName.setText(subtask.name)
        binding.subtaskDescription.setText(subtask.description)
        binding.subtaskDeadline.setText(subtask.deadline)
        binding.progressSlider.value = subtask.progress.toFloat()
        binding.assignedDropdown.setText(subtask.assignedTo, false)

        setPriorityDropdown(subtask.priority)
        setStatusDropdown(subtask.status)

        loadUsers(binding.assignedDropdown)

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_date))
            .build()

        binding.subtaskDeadline.setOnClickListener {
            datePicker.show(childFragmentManager, "DATE_PICKER")
        }

        datePicker.addOnPositiveButtonClickListener {
            binding.subtaskDeadline.setText(datePicker.headerText)
        }

        binding.saveSubtaskButton.setOnClickListener {
            val subtaskName = binding.subtaskName.text.toString()
            val subtaskDescription = binding.subtaskDescription.text.toString()
            val deadline = binding.subtaskDeadline.text.toString()
            val progress = binding.progressSlider.value.toInt()
            val assignedTo = binding.assignedDropdown.text.toString()
            val priority = getSelectedPriority()
            val status = getSelectedStatus()

            if (subtaskName.isNotEmpty() && subtaskDescription.isNotEmpty() && deadline.isNotEmpty()) {
                val updatedSubtask = SubTask(
                    name = subtaskName,
                    description = subtaskDescription,
                    deadline = deadline,
                    progress = progress,
                    status = status,
                    assignedTo = assignedTo,
                    priority = priority,
                    createdBy = mAuth.currentUser?.email
                )

                db.collection("tasks")
                    .document(taskId ?: "")
                    .collection("subTasks")
                    .document(subtaskId!!)
                    .set(updatedSubtask)
                    .addOnSuccessListener {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.subtask_updated_successfully),
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

    private fun setPriorityDropdown(priority: Int) {
        val priorityOptions = resources.getStringArray(R.array.priority_filters).toList()
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_item,
            priorityOptions
        )
        binding.priorityDropdown.setAdapter(adapter)

        if (priority in priorityOptions.indices) {
            binding.priorityDropdown.setText(priorityOptions[priority], false)
        }
    }


    private fun getSelectedPriority(): Int {
        val priorities = resources.getStringArray(R.array.priority_filters)
        return priorities.indexOf(binding.priorityDropdown.text.toString()).takeIf { it >= 0 } ?: 0
    }

    private fun setStatusDropdown(status: Int) {
        val statusOptions = resources.getStringArray(R.array.status_filters).toList()
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_item,
            statusOptions
        )
        binding.statusDropdown.setAdapter(adapter)
        if (status in statusOptions.indices) {
            binding.priorityDropdown.setText(statusOptions[status], false)
        }
    }

    private fun getSelectedStatus(): Int {
        val priorities = resources.getStringArray(R.array.status_filters)
        return priorities.indexOf(binding.statusDropdown.text.toString()).takeIf { it >= 0 } ?: 0
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
