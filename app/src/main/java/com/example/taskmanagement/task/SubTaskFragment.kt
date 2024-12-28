package com.example.taskmanagement.task

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SubTaskFragment : Fragment() {

    private lateinit var subtaskRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var subtaskAdapter: SubtaskAdapter
    private val subtaskList = mutableListOf<SubTask>()
    private val firestore = FirebaseFirestore.getInstance()
    private var taskID: String? = null
    private var isFabMenuOpen = false
    private lateinit var fabAddSubtask: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sub_task, container, false)


        taskID = arguments?.getString("taskId")

        subtaskRecyclerView = view.findViewById(R.id.subtask_list)
        progressBar = view.findViewById(R.id.progressBar)
        fabAddSubtask = view.findViewById(R.id.fab_add_subtask)
        subtaskRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        subtaskAdapter =
            SubtaskAdapter(subtaskList, taskID ?: "") { subtask -> onEditSubTask(subtask) }

        subtaskRecyclerView.adapter = subtaskAdapter

        if (taskID != null) {
            loadSubtasks(taskID!!)
        }

        val backArrow: ImageView = view.findViewById(R.id.backArrow)
        backArrow.setOnClickListener {
            findNavController().popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fabMain: View = view.findViewById(R.id.fab_main)
        val fabBackHome: View = view.findViewById(R.id.fab_back_home)

        fabMain.setOnClickListener {
            toggleFabMenu(fabAddSubtask, fabBackHome)
        }

        fabAddSubtask.setOnClickListener {
            if (taskID != null) {
                val bundle = Bundle().apply {
                    putString("taskId", taskID)
                }
                findNavController().navigate(
                    R.id.action_subTaskFragment_to_addSubTaskFragment,
                    bundle
                )
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.task_id_not_found),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        fabBackHome.setOnClickListener {
            findNavController().navigate(R.id.action_subTaskFragment_to_homeFragment)
        }
    }

    private val subtaskIdMap = mutableMapOf<SubTask, String>()

    private fun loadSubtasks(taskId: String) {
        subtaskRecyclerView.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val result = firestore.collection("tasks")
                    .document(taskId)
                    .collection("subTasks")
                    .get()
                    .await()

                subtaskList.clear()
                for (document in result) {
                    val subTask = document.toObject(SubTask::class.java)
                    subtaskIdMap[subTask] = document.id
                    subtaskList.add(subTask)
                }
                subtaskList.sortBy { it.name }
                subtaskAdapter.updateSubTasks(subtaskList)


                val noSubtasksMessage: TextView =
                    view?.findViewById(R.id.no_subtasks_message) ?: return@launch
                if (subtaskList.isEmpty()) {
                    noSubtasksMessage.visibility = View.VISIBLE
                } else {
                    noSubtasksMessage.visibility = View.GONE
                }

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.failed_to_load_subtasks),
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
    }

    private fun toggleFabMenu(fabAddSubtask: View, fabBackHome: View) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val sharedPrefs =
                requireContext().getSharedPreferences("TaskManagerPrefs", Context.MODE_PRIVATE)
            val role = sharedPrefs.getString("role", "defaultRole")
            if (role == "PM") {
                fabAddSubtask.visibility = View.GONE
                if (isFabMenuOpen) {
                    fabBackHome.visibility = View.GONE
                } else {
                    fabBackHome.visibility = View.VISIBLE
                }
            } else {
                if (isFabMenuOpen) {
                    fabAddSubtask.visibility = View.GONE
                    fabBackHome.visibility = View.GONE
                } else {
                    fabAddSubtask.visibility = View.VISIBLE
                    fabBackHome.visibility = View.VISIBLE
                }
            }
            isFabMenuOpen = !isFabMenuOpen

        }
    }

    private fun onEditSubTask(subtask: SubTask) {
        val subtaskId = subtaskIdMap[subtask]
        val bundle = Bundle().apply {
            putString("taskID", taskID)
            putString("subtaskId", subtaskId)
        }
        findNavController().navigate(R.id.action_subTaskFragment_to_modifySubTaskFragment, bundle)
    }
}

