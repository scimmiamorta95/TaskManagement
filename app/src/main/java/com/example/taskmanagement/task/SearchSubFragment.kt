package com.example.taskmanagement.task

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SearchView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.taskmanagement.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SearchSubFragment : Fragment() {


    private lateinit var searchView: SearchView
    private lateinit var subtaskRecyclerView: RecyclerView
    private lateinit var subtaskAdapter: SubtaskAdapter
    private val subtaskList = mutableListOf<SubTask>()
    private val firestore = FirebaseFirestore.getInstance()
    private var taskID: String? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_search_sub, container, false)

        taskID = arguments?.getString("taskId")

        searchView = view.findViewById(R.id.search_view)
        subtaskRecyclerView = view.findViewById(R.id.recycler_view)

        subtaskRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        subtaskAdapter =
            SubtaskAdapter(subtaskList, taskID ?: "") { subtask -> onEditSubTask(subtask) }
        subtaskRecyclerView.adapter = subtaskAdapter

        if (taskID != null) {
            loadSubtasks(taskID!!)
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                subtaskAdapter.filter(newText)
                return true
            }
        })
        val backButton = view.findViewById<ImageButton>(R.id.back_button)
        backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        return view
    }

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
                    subtaskList.add(subTask)
                }
                subtaskAdapter.updateSubTasks(subtaskList)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.failed_to_load_subtasks),
                    Toast.LENGTH_SHORT
                ).show()

            }
        }

    }

    private fun onEditSubTask(subtask: SubTask) {
        val bundle = Bundle().apply {
            putString("subtaskId", subtask.name)
        }
        findNavController().navigate(R.id.action_subTaskFragment_to_modifySubTaskFragment, bundle)
    }
}
