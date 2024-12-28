package com.example.taskmanagement.task

import android.content.Context
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.taskmanagement.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class SearchFragment : Fragment() {

    private lateinit var searchView: SearchView
    private lateinit var recyclerView: RecyclerView
    private lateinit var taskAdapter: TaskAdapter
    private val taskList = mutableListOf<Task>()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var noTasksMessage: TextView
    private lateinit var swipeRefreshSearch: SwipeRefreshLayout
    private val taskIdMap = mutableMapOf<String, String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        searchView = view.findViewById(R.id.search_view)
        recyclerView = view.findViewById(R.id.searchList)
        noTasksMessage = view.findViewById(R.id.noTasksMessage)
        swipeRefreshSearch = view.findViewById(R.id.swipeRefreshSearch)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        taskAdapter =
            TaskAdapter(taskList, { task -> onTaskClick(task) }, { task -> onEditTask(task) })
        recyclerView.adapter = taskAdapter

        val currentUser = auth.currentUser
        if (currentUser != null) {

            loadTasksFromFile()

            if (taskList.isEmpty()) {
                loadTasks()
            }
        } else {
            Toast.makeText(
                requireContext(),
                getString(R.string.failed_to_load_tasks),
                Toast.LENGTH_SHORT
            ).show()
        }

        swipeRefreshSearch.setOnRefreshListener {
            val currentUsers = auth.currentUser
            if (currentUsers != null) {
                loadTasks()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.failed_to_load_tasks),
                    Toast.LENGTH_SHORT
                ).show()
            }
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

    private fun loadTasks() {
        if (!swipeRefreshSearch.isRefreshing) {
            swipeRefreshSearch.isRefreshing = true
        }
        lifecycleScope.launch {
            try {
                val sharedPrefs =
                    requireContext().getSharedPreferences("TaskManagerPrefs", Context.MODE_PRIVATE)
                val role = sharedPrefs.getString("role", "defaultRole")

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

                saveTasksToFile()

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
                    swipeRefreshSearch.isRefreshing = false
                    taskAdapter.notifyDataSetChanged()
                }
            }
        }
    }


    private suspend fun loadTasksForPM() {
        try {
            val currentUserEmail = auth.currentUser?.email ?: return

            val createdByPMResult = firestore.collection("tasks")
                .whereEqualTo("createdBy", currentUserEmail)
                .get()
                .await()

            for (document in createdByPMResult.documents) {
                val task = document.toObject(Task::class.java)
                if (task != null) {
                    taskList.add(task)
                    taskIdMap[task.name] = document.id
                    calculateSubTasks(task, document.id)
                }
                taskList.sortBy { it.name }
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

            for (document in createdByPLResult.documents + assignedToPLResult.documents) {
                val task = document.toObject(Task::class.java)
                if (task != null) {
                    calculateSubTasks(task, document.id)
                    if (!taskIdMap.containsKey(task.name)) {
                        taskList.add(task)
                        taskIdMap[task.name] = document.id
                    }
                }
            }
            taskList.sortBy { it.name }
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

            for (document in assignedToDevResult.documents) {
                val task = document.toObject(Task::class.java)
                if (task != null) {
                    calculateSubTasks(task, document.id)
                    taskList.add(task)
                    taskIdMap[task.name] = document.id
                }
            }
            taskList.sortBy { it.name }
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

    private fun saveTasksToFile() {
        val tasksJson = Gson().toJson(taskList)
        val taskIdMapJson = Gson().toJson(taskIdMap)

        val tasksFile = File(requireContext().filesDir, "taskList.json")
        tasksFile.writeText(tasksJson)

        val taskIdMapFile = File(requireContext().filesDir, "taskIdMap.json")
        taskIdMapFile.writeText(taskIdMapJson)

        Log.d("SearchFragment", "Saved taskList and taskIdMap separately")
    }


    private fun loadTasksFromFile() {
        val tasksFile = File(requireContext().filesDir, "taskList.json")
        val taskIdMapFile = File(requireContext().filesDir, "taskIdMap.json")

        if (tasksFile.exists() && taskIdMapFile.exists()) {
            try {
                val tasksJson = tasksFile.readText()
                val taskIdMapJson = taskIdMapFile.readText()

                val savedTasks = Gson().fromJson(tasksJson, Array<Task>::class.java).toList()
                val savedTaskIdMap = Gson().fromJson(taskIdMapJson, Map::class.java)

                taskList.clear()
                taskList.addAll(savedTasks)
                taskIdMap.clear()

                savedTaskIdMap.entries.forEach {
                    taskIdMap[it.key as String] = it.value as String
                }

                taskAdapter.notifyDataSetChanged()

            } catch (e: JsonSyntaxException) {
                Log.e("SearchFragment", "Error loading tasks: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun onTaskClick(task: Task) {
        val taskId = taskIdMap[task.name]
        Log.e("SearchFragment", "Task ID: $taskId")
        val bundle = Bundle().apply {
            putString("taskId", taskId)
        }
        findNavController().navigate(R.id.action_searchFragment_to_searchSubFragment, bundle)
    }

    private fun onEditTask(task: Task) {
        val sharedPrefs =
            requireContext().getSharedPreferences("TaskManagerPrefs", Context.MODE_PRIVATE)
        val role = sharedPrefs.getString("role", "defaultRole")
        if (role == "PL" || role == "Dev") {
            Toast.makeText(
                requireContext(),
                getString(R.string.only_pm_can_edit_tasks),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val taskId = taskIdMap[task.name]
        val bundle = Bundle().apply {
            putString("taskId", taskId)
        }
        findNavController().navigate(R.id.action_searchFragment_to_modifyTaskFragment, bundle)
    }
}
