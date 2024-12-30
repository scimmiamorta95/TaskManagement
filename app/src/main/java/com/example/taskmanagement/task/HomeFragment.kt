package com.example.taskmanagement.task

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class HomeFragment : Fragment() {

    private lateinit var taskRecyclerView: RecyclerView
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var fabMain: com.google.android.material.floatingactionbutton.FloatingActionButton

    private val taskList = mutableListOf<Task>()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var noTasksMessage: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private val taskIdMap = mutableMapOf<String, String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        taskRecyclerView = view.findViewById(R.id.task_list)
        fabMain = view.findViewById(R.id.fab_main)
        noTasksMessage = view.findViewById(R.id.noTasksMessage)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)

        taskRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        taskAdapter =
            TaskAdapter(taskList, { task -> onTaskClick(task) }, { task -> onEditTask(task) })
        taskRecyclerView.adapter = taskAdapter

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
        val sharedPrefs =
            requireContext().getSharedPreferences("TaskManagerPrefs", Context.MODE_PRIVATE)
        val role = sharedPrefs.getString("role", "defaultRole")
        if (role == "PL" || role == "Dev") {
            fabMain.visibility = View.GONE
        } else {
            fabMain.visibility = View.VISIBLE
            fabMain.setOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_addTaskFragment)
            }
        }
        swipeRefreshLayout.setOnRefreshListener {
            currentUser?.let {
                loadTasks()
            }
        }

        return view
    }

    private fun loadTasks() {
        swipeRefreshLayout.isRefreshing = true
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
                    noTasksMessage.visibility = if (taskList.isEmpty()) View.VISIBLE else View.GONE
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
                    swipeRefreshLayout.isRefreshing = false
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
            val assignedToDevResult = firestore.collection("tasks")
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

            val progressList =
                subTasksResult.mapNotNull { it.toObject(SubTask::class.java).progress }

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
        val sharedPrefs =
            requireContext().getSharedPreferences("TaskManagerPrefs", Context.MODE_PRIVATE)
        val role = sharedPrefs.getString("role", "defaultRole")

        if (role == "PM") {
            return
        }
        val taskId = taskIdMap[task.name]
        val bundle = Bundle().apply {
            putString("taskId", taskId)
        }
        findNavController().navigate(R.id.action_homeFragment_to_subTaskFragment, bundle)
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
        findNavController().navigate(R.id.action_homeFragment_to_modifyTaskFragment, bundle)
    }

    companion object {
        fun deleteSavedTasksFiles(context: Context) {
            try {
                val tasksFile = File(context.filesDir, "taskList.json")
                val taskIdMapFile = File(context.filesDir, "taskIdMap.json")

                if (tasksFile.exists()) {
                    tasksFile.delete()
                }
                if (taskIdMapFile.exists()) {
                    taskIdMapFile.delete()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.error_deleting_files),
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("HomeFragment", "Error deleting saved files: ${e.message}")
                e.printStackTrace()
            }
        }
    }

}

