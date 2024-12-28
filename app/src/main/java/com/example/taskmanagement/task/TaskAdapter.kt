package com.example.taskmanagement.task

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.taskmanagement.R
import com.example.taskmanagement.databinding.TaskListItemBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private var taskList: List<Task>,
    private val onTaskClick: (Task) -> Unit,
    private val onEditTask: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private var originalTaskList: List<Task> = taskList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding =
            TaskListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = taskList[position]
        holder.bind(task)
    }

    override fun getItemCount(): Int = taskList.size

    private fun updateTasks(newTaskList: List<Task>) {
        val diffCallback = TaskDiffCallback(taskList, newTaskList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        taskList = newTaskList
        diffResult.dispatchUpdatesTo(this)
    }

    fun filter(query: String?) {
        val filteredList = if (query.isNullOrEmpty()) {
            originalTaskList
        } else {
            originalTaskList.filter {
                it.name.contains(query, ignoreCase = true) || it.description.contains(
                    query,
                    ignoreCase = true
                )
                        || it.assignedTo?.contains(
                    query,
                    ignoreCase = true
                ) == true || it.deadline.contains(query, ignoreCase = true)

            }
        }
        updateTasks(filteredList)
    }

    inner class TaskViewHolder(private val binding: TaskListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")

        fun bind(task: Task) {
            binding.taskTitle.text = task.name
            binding.taskDescription.text = task.description
            binding.taskAssignedToMaster.text = task.assignedTo ?: "Non assegnato"

            binding.subtaskProgressBar.progress = task.progress
            binding.subtaskProgressText.text = "${task.progress} %"

            binding.taskDeadline.text = task.deadline.let { "Deadline: $it" }
            val daysRemaining = calculateDaysRemaining(task.deadline)
            val formattedText = itemView.context.getString(R.string.days_remaining, daysRemaining)
            binding.taskDaysRemaining.text = formattedText

            binding.subtaskCount.text = "Sottotask: ${task.nSubTask}"

            binding.btnEditTask.setOnClickListener {
                onEditTask(task)
            }

            binding.root.setOnClickListener {
                onTaskClick(task)
            }
            val sharedPrefs =
                itemView.context.getSharedPreferences("TaskManagerPrefs", Context.MODE_PRIVATE)
            val userRole = sharedPrefs.getString("role", "defaultRole")

            if (userRole == "PM") {
                binding.deleteBtn.visibility = View.VISIBLE
                binding.deleteBtn.setOnClickListener {
                    db.collection("tasks")
                        .whereEqualTo("name", task.name)
                        .get()
                        .addOnSuccessListener { documents ->
                            if (!documents.isEmpty) {
                                for (document in documents) {
                                    document.reference.delete()
                                        .addOnSuccessListener {
                                            val position = adapterPosition
                                            if (position != RecyclerView.NO_POSITION) {
                                                val updatedList = taskList.toMutableList()
                                                updatedList.removeAt(position)
                                                taskList = updatedList
                                                notifyItemRemoved(position)
                                                Toast.makeText(
                                                    itemView.context,
                                                    itemView.context.getString(R.string.task_deleted_successfully),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(
                                                itemView.context,
                                                itemView.context.getString(
                                                    R.string.error_deleting_task,
                                                    e.message
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                }
                            } else {
                                Toast.makeText(
                                    itemView.context,
                                    itemView.context.getString(R.string.task_not_found),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                itemView.context,
                                itemView.context.getString(
                                    R.string.error_searching_task,
                                    e.message
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            } else {
                binding.deleteBtn.visibility = View.GONE
            }
        }


        private fun calculateDaysRemaining(deadline: String?): Int {
            return try {
                val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val deadlineDate = deadline?.let { dateFormat.parse(it) }
                val currentDate = Date()

                val difference = deadlineDate?.time?.minus(currentDate.time) ?: 0
                val daysRemaining = (difference / (1000 * 60 * 60 * 24)).toInt()

                if (daysRemaining < 0) 0 else daysRemaining
            } catch (e: Exception) {
                0
            }
        }
    }


    class TaskDiffCallback(
        private val oldList: List<Task>,
        private val newList: List<Task>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].name == newList[newItemPosition].name
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
