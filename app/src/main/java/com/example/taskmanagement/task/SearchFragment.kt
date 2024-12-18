package com.example.taskmanagement.task

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.taskmanagement.R

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SearchFragment : Fragment() {

    private lateinit var searchView: SearchView
    private lateinit var recyclerView: RecyclerView
    private lateinit var taskAdapter: TaskAdapter
    private val taskList = mutableListOf<Task>()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var noTasksMessage: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        searchView = view.findViewById(R.id.search_view)
        recyclerView = view.findViewById(R.id.recycler_view)
        noTasksMessage = view.findViewById(R.id.noTasksMessage)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        taskAdapter =
            TaskAdapter(taskList, { task -> onTaskClick(task) }, { task -> onEditTask(task) })
        recyclerView.adapter = taskAdapter

        val currentUser = auth.currentUser
        if (currentUser != null) {
            loadTasks(currentUser.uid)
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                taskAdapter.filter(newText)
                return true
            }
        })

        return view
    }

    private fun loadTasks(userId: String) {
        lifecycleScope.launch {
            try {
                val document = firestore.collection("users").document(userId).get().await()
                val role = document.getString("role")

                taskList.clear()
                taskIdMap.clear()

                when (role) {
                    "PM" -> loadTasksForPM()
                    "PL" -> loadTasksForPL()
                    "Dev" -> loadTasksForDev()
                }

                if (isAdded) {
                    if (taskList.isEmpty()) {

                        noTasksMessage.visibility = View.VISIBLE
                    } else {
                        noTasksMessage.visibility = View.GONE
                    }
                }

            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.failed_to_load_tasks),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                if (isAdded) {
                    taskAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private val taskIdMap = mutableMapOf<Task, String>()

    private suspend fun loadTasksForPM() {
        try {
            val currentUserEmail = auth.currentUser?.email ?: return

            val createdByPMResult = firestore.collection("tasks")
                .whereEqualTo("createdBy", currentUserEmail)
                .get()
                .await()

            val allTasks = mutableListOf<QueryDocumentSnapshot>()
            allTasks.addAll(createdByPMResult.documents as List<QueryDocumentSnapshot>)

            for (document in allTasks) {
                val task = document.toObject(Task::class.java)
                taskList.add(task)
                taskIdMap[task] = document.id

                calculateSubTasks(task, document.id)
            }

        } catch (e: Exception) {
            if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.failed_to_load_tasks),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private suspend fun loadTasksForPL() {
        try {
            val currentUserEmail = auth.currentUser?.email ?: return
            val createdByPLResult = firestore.collection("tasks")
                .whereEqualTo("createdBy", currentUserEmail)
                .get()
                .await()

            val assignedToPLResult = firestore.collection("tasks")
                .whereEqualTo("assignedTo", currentUserEmail)
                .get()
                .await()

            val allTasks = mutableListOf<QueryDocumentSnapshot>()
            allTasks.addAll(createdByPLResult.documents as List<QueryDocumentSnapshot>)
            allTasks.addAll(assignedToPLResult.documents as List<QueryDocumentSnapshot>)

            for (document in allTasks) {
                val task = document.toObject(Task::class.java)


                calculateSubTasks(task, document.id)

                if (!taskIdMap.containsKey(task)) {
                    taskList.add(task)
                    taskIdMap[task] = document.id
                }
            }
        } catch (e: Exception) {
            if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.failed_to_load_tasks),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private suspend fun loadTasksForDev() {
        try {
            val currentUserEmail = auth.currentUser?.email ?: return
            val assignedToDevResult = firestore.collection("tasks")
                .whereEqualTo("assignedTo", currentUserEmail)
                .get()
                .await()

            val allTasks = mutableListOf<QueryDocumentSnapshot>()
            allTasks.addAll(assignedToDevResult.documents as List<QueryDocumentSnapshot>)

            for (document in allTasks) {
                val task = document.toObject(Task::class.java)

                calculateSubTasks(task, document.id)

                taskList.add(task)
                taskIdMap[task] = document.id
            }

        } catch (e: Exception) {
            if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.failed_to_load_tasks),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private suspend fun calculateSubTasks(task: Task, taskId: String) {
        try {
            val subTasksResult = firestore.collection("tasks")
                .document(taskId)
                .collection("subTasks")
                .get()
                .await()

            val progressList = mutableListOf<Int>()
            for (subTaskDocument in subTasksResult) {
                val subTask = subTaskDocument.toObject(SubTask::class.java)
                progressList.add(subTask.progress)
                Log.d("TaskAdapter", "Subtask progress: ${subTask.progress}")
            }

            val averageProgress = if (progressList.isNotEmpty()) {
                progressList.average().toInt()
            } else {
                0
            }

            task.progress = averageProgress
            task.nSubTask = subTasksResult.size()

        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.error_calculating_subtask_progress),
                Toast.LENGTH_SHORT
            ).show()
        }

    }


    private fun onTaskClick(task: Task) {
        val taskId = taskIdMap[task]
        val bundle = Bundle().apply {
            putString("taskId", taskId)
        }
        findNavController().navigate(R.id.action_searchFragment_to_searchSubFragment, bundle)
    }

    private fun onEditTask(task: Task) {
        val taskId = taskIdMap[task]
        val bundle = Bundle().apply {
            putString("taskId", taskId)
        }
        findNavController().navigate(R.id.action_searchFragment_to_modifyTaskFragment, bundle)
    }
}
