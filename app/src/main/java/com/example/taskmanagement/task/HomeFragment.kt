package com.example.taskmanagement.task


import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
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

class HomeFragment : Fragment() {

    private lateinit var taskRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var fabMain: com.google.android.material.floatingactionbutton.FloatingActionButton

    private val taskList = mutableListOf<Task>()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var noTasksMessage: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        taskRecyclerView = view.findViewById(R.id.task_list)
        progressBar = view.findViewById(R.id.progressBar)
        fabMain = view.findViewById(R.id.fab_main)
        noTasksMessage = view.findViewById(R.id.noTasksMessage)

        taskRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        taskAdapter =
            TaskAdapter(taskList, { task -> onTaskClick(task) }, { task -> onEditTask(task) })
        taskRecyclerView.adapter = taskAdapter

        val currentUser = auth.currentUser
        if (currentUser != null) {
            loadTasks(currentUser.uid)
        }

        fabMain.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_addTaskFragment)
        }

        return view
    }

    private fun loadTasks(userId: String) {
        progressBar.visibility = View.VISIBLE
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
                    progressBar.visibility = View.GONE
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

            // Inizializza una lista per i task
            val allTasks = mutableListOf<Task>()

            // Processa i task creati dal Project Manager
            createdByPMResult.documents.forEach { document ->
                val task = document.toObject(Task::class.java)
                task?.let {
                    allTasks.add(it)
                    taskIdMap[it] = document.id
                }
            }

            // Aggiorna i task aggiuntivi con il calcolo dei subtasks
            for (task in allTasks) {
                calculateSubTasks(task, taskIdMap[task] ?: return)
            }

            // Aggiungi i task caricati alla lista principale e aggiorna l'interfaccia utente
            taskList.clear()
            taskList.addAll(allTasks)

            if (isAdded) {
                fabMain.visibility = View.VISIBLE
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

            val allTasks = mutableListOf<Task>()

            createdByPLResult.documents.forEach { document ->
                val task = document.toObject(Task::class.java)
                task?.let {
                    allTasks.add(it)
                    taskIdMap[it] = document.id
                }
            }

            assignedToPLResult.documents.forEach { document ->
                val task = document.toObject(Task::class.java)
                task?.let {
                    if (!taskIdMap.containsKey(it)) {
                        allTasks.add(it)
                        taskIdMap[it] = document.id
                    }
                }
            }

            for (task in allTasks) {
                calculateSubTasks(task, taskIdMap[task] ?: return)
            }

            taskList.clear()
            taskList.addAll(allTasks)

            if (isAdded) {
                fabMain.visibility = View.VISIBLE
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

            // Inizializza una lista per i task
            val allTasks = mutableListOf<Task>()

            // Processa i task assegnati al Developer
            assignedToDevResult.documents.forEach { document ->
                val task = document.toObject(Task::class.java)
                task?.let {
                    allTasks.add(it)
                    taskIdMap[it] = document.id
                }
            }

            // Aggiorna i task aggiuntivi con il calcolo dei subtasks
            for (task in allTasks) {
                calculateSubTasks(task, taskIdMap[task] ?: return)
            }

            // Aggiungi i task caricati alla lista principale e aggiorna l'interfaccia utente
            taskList.clear()
            taskList.addAll(allTasks)

            if (isAdded) {
                fabMain.visibility = View.VISIBLE
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
        findNavController().navigate(R.id.action_homeFragment_to_subTaskFragment, bundle)
    }

    private fun onEditTask(task: Task) {
        val taskId = taskIdMap[task]
        val bundle = Bundle().apply {
            putString("taskId", taskId)
        }
        findNavController().navigate(R.id.action_homeFragment_to_modifyTaskFragment, bundle)
    }
}
