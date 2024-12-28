package com.example.taskmanagement.auth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.taskmanagement.R
import com.example.taskmanagement.databinding.FragmentProfileBinding
import com.example.taskmanagement.task.HomeFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ProfileFragment : Fragment() {

    private lateinit var binding: FragmentProfileBinding
    private var firebaseAuth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null

    private var taskList: MutableList<String> = mutableListOf()
    private var taskAdapter: ArrayAdapter<String>? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                openGallery()
            } else {
                Toast.makeText(context, getString(R.string.permission_denied), Toast.LENGTH_SHORT)
                    .show()
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { imageUri: Uri? ->
            if (imageUri != null) {
                val bitmap = BitmapFactory.decodeStream(
                    requireContext().contentResolver.openInputStream(imageUri)
                )
                saveProfileImage(bitmap)
                setProfileImage(bitmap)
            } else {
                Toast.makeText(context, getString(R.string.image_loading_error), Toast.LENGTH_SHORT)
                    .show()
            }
        }

    companion object {
        private const val PROFILE_IMAGE_FILENAME = "profile_image.jpg"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(inflater, container, false)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        taskAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, taskList)
        binding.assignedTasksList.adapter = taskAdapter
        binding.assignedTasksList.isNestedScrollingEnabled = true

        loadUserData()
        loadProfileImage()

        binding.buttonEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }

        binding.buttonLogout.setOnClickListener {
            firebaseAuth?.signOut()
            Toast.makeText(context, getString(R.string.logout_success), Toast.LENGTH_SHORT).show()
            HomeFragment.deleteSavedTasksFiles(requireContext())
            findNavController().popBackStack(R.id.welcomeFragment, false)
        }

        binding.profileImage.setOnClickListener {
            checkPermissions()
        }

        return binding.root
    }

    private fun checkPermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                if (requireContext().checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    openGallery()
                }
            }

            else -> {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                } else {
                    openGallery()
                }
            }
        }
    }


    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun saveProfileImage(bitmap: Bitmap) {
        val userEmail = firebaseAuth?.currentUser?.email ?: return
        val file = File(requireContext().filesDir, "${userEmail}_$PROFILE_IMAGE_FILENAME")

        try {
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
            }
            Toast.makeText(context, getString(R.string.profile_image_saved), Toast.LENGTH_SHORT)
                .show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(
                context,
                getString(R.string.error_saving_profile_image),
                Toast.LENGTH_SHORT
            )
                .show()
        }
    }

    private fun loadProfileImage() {
        val userEmail = firebaseAuth?.currentUser?.email ?: return
        val file = File(requireContext().filesDir, "${userEmail}_$PROFILE_IMAGE_FILENAME")

        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(file.path)
            setProfileImage(bitmap)
        } else {
            binding.profileImage.setImageResource(R.drawable.ic_baseline_account_circle_24)
        }
    }

    private fun setProfileImage(bitmap: Bitmap) {
        val circularBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, true)

        binding.profileImage.setImageBitmap(circularBitmap)
    }


    private fun loadUserData() {
        val userId = firebaseAuth!!.currentUser!!.email

        val sharedPrefs =
            requireContext().getSharedPreferences("TaskManagerPrefs", Context.MODE_PRIVATE)
        val role = sharedPrefs.getString("role", "defaultRole")
        if (role == "PM") {
            binding.taskListAssigned.visibility = View.GONE
        }

        if (userId != null) {
            firestore!!.collection("usersData")
                .document(userId)
                .get()
                .addOnSuccessListener { document: DocumentSnapshot ->
                    val firstName = document.getString("firstName")
                    val lastName = document.getString("lastName")
                    val skills = document.getString("skills")

                    if (!firstName.isNullOrEmpty() && !lastName.isNullOrEmpty()) {
                        binding.userName.text = getString(R.string.user_name, firstName, userId)
                        binding.welcomeMessage.text = getString(R.string.welcome_message, firstName)

                    } else {
                        val email = firebaseAuth!!.currentUser!!.email
                        binding.userName.text = email
                        binding.welcomeMessage.text = getString(R.string.welcome_message, email)
                    }

                    binding.skillsList.text = skills ?: "Nessuna skill disponibile"
                }
                .addOnFailureListener {
                    Toast.makeText(
                        context,
                        getString(R.string.error_loading_users),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
        firestore!!.collection("tasks")
            .whereEqualTo("assignedTo", userId)
            .get()
            .addOnSuccessListener { queryDocumentSnapshots: QuerySnapshot ->
                taskList.clear()
                for (snapshot in queryDocumentSnapshots) {
                    val taskName = snapshot.getString("name")
                    if (taskName != null) {
                        taskList.add(taskName)
                    }
                }
                taskAdapter!!.notifyDataSetChanged()
                listViewHeight(binding.assignedTasksList)
            }
            .addOnFailureListener {
                Toast.makeText(context, getString(R.string.error_loading_tasks), Toast.LENGTH_SHORT)
                    .show()

            }

        firestore!!.collection("tasks")
            .get()
            .addOnSuccessListener { tasksSnapshot ->
                var completedCount = 0
                var assignedCount = 0
                var toDoCount = 0

                val taskCount = tasksSnapshot.size()
                var tasksProcessed = 0

                if (taskCount == 0) {
                    updateStatistics(completedCount, assignedCount, toDoCount)
                    return@addOnSuccessListener
                }

                for (task in tasksSnapshot) {
                    val taskId = task.id

                    val subTaskQuery = if (role == "PL") {
                        firestore!!.collection("tasks").document(taskId).collection("subTasks")
                    } else {
                        firestore!!.collection("tasks")
                            .document(taskId)
                            .collection("subTasks")
                            .whereEqualTo("assignedTo", userId)
                    }

                    subTaskQuery.get()
                        .addOnSuccessListener { subTasksSnapshot ->
                            for (subTask in subTasksSnapshot) {
                                val subTaskStatus = subTask.getLong("status")?.toInt()
                                when (subTaskStatus) {
                                    2 -> completedCount++
                                    1 -> assignedCount++
                                    0 -> toDoCount++
                                }
                            }

                            tasksProcessed++

                            if (tasksProcessed == taskCount) {
                                updateStatistics(completedCount, assignedCount, toDoCount)
                            }
                        }
                        .addOnFailureListener {
                            Log.e(
                                "ProfileFragment",
                                "Errore nel recupero dei sottotask per il task $taskId"
                            )
                            tasksProcessed++
                            if (tasksProcessed == taskCount) {
                                updateStatistics(completedCount, assignedCount, toDoCount)
                            }
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, getString(R.string.error_loading_tasks), Toast.LENGTH_SHORT)
                    .show()
            }


    }

    private fun updateStatistics(completed: Int, assigned: Int, toDo: Int) {
        val toDoTextView = view?.findViewById<TextView>(R.id.todoSubTask)
        val assignedTextView = view?.findViewById<TextView>(R.id.assignedSubtask)
        val completedTextView = view?.findViewById<TextView>(R.id.completedSubtask)

        completedTextView?.text = "$completed"
        assignedTextView?.text = "$assigned"
        toDoTextView?.text = "$toDo"
    }

    private fun listViewHeight(listView: ListView) {
        val adapter = listView.adapter ?: return

        var totalHeight = 0
        val itemsToShow = minOf(4, adapter.count)

        for (i in 0 until itemsToShow) {
            val listItem = adapter.getView(i, null, listView)
            listItem.measure(
                View.MeasureSpec.makeMeasureSpec(listView.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            totalHeight += listItem.measuredHeight
        }

        val params = listView.layoutParams
        params.height = totalHeight + (listView.dividerHeight * (itemsToShow - 1))
        listView.layoutParams = params
        listView.requestLayout()
    }
}
