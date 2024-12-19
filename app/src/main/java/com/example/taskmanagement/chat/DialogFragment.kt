package com.example.taskmanagement.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.taskmanagement.R
import com.example.taskmanagement.auth.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DialogFragment : DialogFragment() {

    private lateinit var recyclerViewUsers: RecyclerView
    private val userList = mutableListOf<User>()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_dialog, container, false)
        recyclerViewUsers = view.findViewById(R.id.recyclerViewUsers)

        setupRecyclerView()
        loadUsers()

        return view
    }

    private fun setupRecyclerView() {
        recyclerViewUsers.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewUsers.adapter = UserAdapter(userList) { selectedUser ->
            openChat(selectedUser.email)
        }
    }


    private fun loadUsers() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), getString(R.string.error_loading_users), Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                val currentUserRole = document.getString("role") ?: return@addOnSuccessListener

                val rolesToLoad = when (currentUserRole) {
                    "PM" -> listOf("PL")
                    "PL" -> listOf("PM", "Dev")
                    "Dev" -> listOf("PL", "Dev")
                    else -> emptyList()
                }

                firestore.collection("users")
                    .whereIn("role", rolesToLoad)
                    .get()
                    .addOnSuccessListener { documents ->
                        userList.clear()
                        for (document in documents) {
                            val user = document.toObject(User::class.java)
                            if (user.email != currentUser.email) {
                                userList.add(user)
                            }
                        }
                        recyclerViewUsers.adapter?.notifyDataSetChanged()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.error_loading_users, e.message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_loading_chats, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


    private fun openChat(selectedUser: String) {
        val bundle = Bundle()
        bundle.putString("selectedUser", selectedUser)
        findNavController().navigate(R.id.action_userSelectionFragment_to_chatFragment, bundle)
        dismiss()
    }
}
