package com.example.taskmanagement.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.taskmanagement.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserSelectionFragment : Fragment() {

    private lateinit var recyclerViewChats: RecyclerView
    private lateinit var btnNewChat: FloatingActionButton
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser
    private val chatList = mutableListOf<Chat>()
    private lateinit var chatAdapter: ChatViewAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user_selection, container, false)

        recyclerViewChats = view.findViewById(R.id.recyclerViewChats)
        btnNewChat = view.findViewById(R.id.newChatButton)

        setupRecyclerView()
        loadChats()

        btnNewChat.setOnClickListener {
            val userListBottomSheet = DialogFragment()
            userListBottomSheet.show(parentFragmentManager, "UserListBottomSheet")
        }

        return view
    }

    private fun setupRecyclerView() {
        try {
            chatAdapter = ChatViewAdapter(chatList) { chat ->
                openChat(chat)
            }
            recyclerViewChats.layoutManager = LinearLayoutManager(requireContext())
            recyclerViewChats.adapter = chatAdapter
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadChats() {
        val currentUserId = currentUser?.email
        if (currentUserId.isNullOrEmpty()) {
            if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.user_not_authenticated),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                if (isAdded) {
                    chatList.clear()
                    for (document in documents) {
                        val chat = document.toObject(Chat::class.java).apply {
                            id = document.id
                        }
                        chatList.add(chat)
                    }
                    chatAdapter.notifyDataSetChanged()
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_loading_chats, e.message ?: "Unknown error"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun openChat(chat: Chat) {
        val currentUserId = currentUser?.email
        val otherParticipant = chat.participants.firstOrNull { it != currentUserId }

        if (otherParticipant == null) {
            if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.no_participants_found),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        firestore.collection("users")
            .document(otherParticipant)
            .get()
            .addOnSuccessListener { document ->
                if (isAdded) {
                    val otherUserRole = document.getString("role")
                    val currentUserRole = fetchUserRole(currentUserId)

                    if (canCommunicate(currentUserRole, otherUserRole)) {
                        val bundle = Bundle().apply {
                            putString("selectedUser", otherParticipant)
                        }
                        findNavController().navigate(
                            R.id.action_userSelectionFragment_to_chatFragment,
                            bundle
                        )
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.cannot_communicate),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_loading_user_data),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun canCommunicate(currentUserRole: String?, otherUserRole: String?): Boolean {
        return when (currentUserRole) {
            "PM" -> otherUserRole == "PL"
            "PL" -> otherUserRole == "PM" || otherUserRole == "DEV"
            "DEV" -> otherUserRole == "PL" || otherUserRole == "DEV"
            else -> false
        }
    }

    private fun fetchUserRole(userId: String?): String? {
        var role: String? = null
        if (userId.isNullOrEmpty()) return role

        firestore.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (isAdded) {
                    role = document.getString("role")
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_retrieving_role),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        return role
    }
}
