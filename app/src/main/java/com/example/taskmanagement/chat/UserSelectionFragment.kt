package com.example.taskmanagement.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
    private lateinit var noChatsMessage: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user_selection, container, false)

        recyclerViewChats = view.findViewById(R.id.recyclerViewChats)
        btnNewChat = view.findViewById(R.id.newChatButton)
        noChatsMessage= view.findViewById(R.id.noChatsMessage)

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
                    if (documents.isEmpty) {
                        noChatsMessage.visibility = View.VISIBLE
                        recyclerViewChats.visibility = View.GONE
                    } else {
                        noChatsMessage.visibility = View.GONE
                        recyclerViewChats.visibility = View.VISIBLE

                        for (document in documents) {
                            val chat = document.toObject(Chat::class.java).apply {
                                id = document.id
                            }
                            loadLastMessage(chat)
                        }
                    }

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

    private fun loadLastMessage(chat: Chat) {
        firestore.collection("chats")
            .document(chat.id)
            .collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { messages ->
                if (isAdded) {
                    if (!messages.isEmpty) {
                        val lastMessage = messages.documents[0].toObject(Message::class.java)
                        chat.lastMessage = lastMessage
                    }
                    chatList.add(chat)
                    chatAdapter.notifyDataSetChanged()
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_loading_last_message, e.message ?: "Unknown error"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }




    private fun openChat(chat: Chat) {
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

        if (!chat.participants.contains(currentUserId)) {
            if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.user_not_found),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        val otherParticipant = chat.participants
            .filter { it != currentUserId }
            .sorted()
            .joinToString("_")

        if (otherParticipant.isEmpty()) {
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
            .addOnSuccessListener {
                if (isAdded) {
                    val chatUser = chat.participants.firstOrNull { it != currentUser?.email }
                    val bundle = Bundle().apply {
                        putString("selectedUser", chatUser)
                    }
                    findNavController().navigate(
                        R.id.action_userSelectionFragment_to_chatFragment,
                        bundle
                    )

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

}
