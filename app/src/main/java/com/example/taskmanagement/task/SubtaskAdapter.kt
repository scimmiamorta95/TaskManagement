package com.example.taskmanagement.task

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.taskmanagement.R
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class SubtaskAdapter(
    private var subtaskList: MutableList<SubTask>,
    private val taskId: String,
    private val onEditSubTask: (SubTask) -> Unit
) : RecyclerView.Adapter<SubtaskAdapter.SubtaskViewHolder>() {
    private var originalTaskList: List<SubTask> = subtaskList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubtaskViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.subtask_list_item, parent, false)
        return SubtaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubtaskViewHolder, position: Int) {
        val subtask = subtaskList[position]
        holder.bind(subtask)
    }

    override fun getItemCount(): Int = subtaskList.size

    fun updateSubTasks(newSubtaskList: List<SubTask>) {
        subtaskList = newSubtaskList.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String?) {
        val filteredList = if (query.isNullOrEmpty()) {
            originalTaskList
        } else {
            originalTaskList.filter {
                it.name.contains(query, ignoreCase = true) || it.description.contains(
                    query,
                    ignoreCase = true
                ) || it.deadline.contains(query, ignoreCase = true)
                        || it.status.contains(query, ignoreCase = true)
            }
        }


        updateSubTasks(filteredList)
    }

    inner class SubtaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val subtaskTitle: TextView = itemView.findViewById(R.id.task_title)
        private val subtaskPriority: TextView = itemView.findViewById(R.id.task_priority)
        private val subtaskDescription: TextView = itemView.findViewById(R.id.task_description)
        private val subtaskDeadline: TextView = itemView.findViewById(R.id.task_deadline)
        private val subtaskDaysRemaining: TextView = itemView.findViewById(R.id.task_days_remaining)
        private val subtaskState: TextView = itemView.findViewById(R.id.task_state)
        private val subtaskProgress: TextView = itemView.findViewById(R.id.subtask_progress)
        private val doneButton: Button = itemView.findViewById(R.id.done_button)
        private val editButton: Button = itemView.findViewById(R.id.edit_button)
        private val deleteButton: Button = itemView.findViewById(R.id.delete_button)

        private val db = FirebaseFirestore.getInstance()

        fun bind(subtask: SubTask) {
            subtaskTitle.text = subtask.name

            subtaskDescription.text = subtask.description
            subtaskProgress.text = "${subtask.progress}%"
            subtaskPriority.text = when (subtask.priority) {
                2 -> itemView.context.getString(R.string.priority_high)
                1 -> itemView.context.getString(R.string.priority_medium)
                0 -> itemView.context.getString(R.string.priority_low)
                else -> itemView.context.getString(R.string.priority_unknown)
            }
            subtaskDeadline.text = subtask.deadline
            subtaskDaysRemaining.text = subtask.deadline
            subtaskState.text = when (subtask.status) {
                "Todo" -> itemView.context.getString(R.string.todo)
                "Assigned" -> itemView.context.getString(R.string.assigned)
                "Completed" -> itemView.context.getString(R.string.completed)
                else -> itemView.context.getString(R.string.unknown)
            }
            val daysRemaining = calculateDaysRemaining(subtask.deadline)
            val formattedText = itemView.context.getString(R.string.days_remaining, daysRemaining)
            subtaskDaysRemaining.text = formattedText

            editButton.setOnClickListener {
                onEditSubTask(subtask)
            }

            doneButton.setOnClickListener {
                if (subtask.status != "completed") {
                    val subtasksRef = db.collection("tasks")
                        .document(taskId)
                        .collection("subTasks")

                    subtasksRef.get()
                        .addOnSuccessListener { documents ->
                            for (document in documents) {
                                val subtaskName = document.getString("name")
                                if (subtaskName == subtask.name) {
                                    document.reference.update(
                                        "status",
                                        "Completed",
                                        "progress",
                                        100
                                    )
                                        .addOnSuccessListener {
                                            subtask.status = "Completed"
                                            subtask.progress = 100
                                            notifyItemChanged(adapterPosition)
                                            Toast.makeText(
                                                itemView.context,
                                                "Completed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(
                                                itemView.context,
                                                "Failed to mark as completed: ${e.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    return@addOnSuccessListener
                                }
                            }
                            Toast.makeText(
                                itemView.context,
                                "Subtask not found for the task",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                itemView.context,
                                "Error fetching subtasks: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    Toast.makeText(
                        itemView.context,
                        "${subtask.name} is already completed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            deleteButton.setOnClickListener {
                db.collection("tasks")
                    .document(taskId).collection("subTasks")
                    .whereEqualTo("name", subtask.name)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            for (document in documents) {
                                document.reference.delete()
                                    .addOnSuccessListener {
                                        val position = adapterPosition
                                        if (position != RecyclerView.NO_POSITION) {
                                            subtaskList.removeAt(position)
                                            notifyItemRemoved(position)
                                        }
                                        Toast.makeText(
                                            itemView.context,
                                            "Subtask eliminato con successo",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(
                                            "SubtaskAdapter",
                                            "Errore durante l'eliminazione del subtask",
                                            e
                                        )
                                        Toast.makeText(
                                            itemView.context,
                                            "Errore: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                        } else {
                            Toast.makeText(
                                itemView.context,
                                "Subtask non trovato",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("SubtaskAdapter", "Errore durante la ricerca del subtask", e)
                        Toast.makeText(itemView.context, "Errore: ${e.message}", Toast.LENGTH_SHORT)
                            .show()
                    }
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

}


