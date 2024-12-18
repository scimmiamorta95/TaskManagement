package com.example.taskmanagement.chat

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
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
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: Button
    private lateinit var buttonBack: ImageButton
    private lateinit var nameChatTextView: TextView
    private lateinit var adapter: ChatAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var messagesListener: ListenerRegistration

    private var receiverId: String = ""
    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.email.orEmpty()

    private val messageList = mutableListOf<Message>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewMessages)
        editTextMessage = view.findViewById(R.id.editTextMessage)
        buttonSend = view.findViewById(R.id.buttonSend)
        buttonBack = view.findViewById(R.id.buttonBack)
        nameChatTextView = view.findViewById(R.id.nameChat)
        receiverId = requireArguments().getString("selectedUser").toString()


        if (receiverId.isEmpty()) {
            if (receiverId.isEmpty()) {
                requireActivity().onBackPressedDispatcher.onBackPressed()
                return null
            }

            return null
        }

        setupRecyclerView()
        setupSendButton()

        lifecycleScope.launch {
            loadMessages()
        }

        subscribeToMessages()
        createChat()

        nameChatTextView.text = receiverId

        buttonBack.setOnClickListener {
            findNavController().navigateUp()
        }

        return view
    }


    private fun setupRecyclerView() {
        adapter = ChatAdapter(currentUserId)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.submitList(messageList)
    }

    private fun setupSendButton() {
        buttonSend.setOnClickListener {
            val messageText = editTextMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                editTextMessage.text.clear()
            }
        }
    }

    private suspend fun loadMessages() {
        val chatId = getChatId()
        try {
            val messagesSnapshot = firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp")
                .get()
                .await()

            val messages = messagesSnapshot.toObjects(Message::class.java)

            messageList.clear()
            messageList.addAll(messages)
            adapter.submitList(ArrayList(messageList))
            recyclerView.scrollToPosition(messageList.size - 1)
        } catch (e: Exception) {
            Log.e("ChatFragment", "Error loading messages: ", e)
            Toast.makeText(
                requireContext(),
                getString(R.string.failed_to_load_messages),
                Toast.LENGTH_SHORT
            ).show()

        }
    }

    private fun sendMessage(text: String) {
        val chatId = getChatId()

        val message = Message(
            senderId = currentUserId,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        messageList.add(message)
        adapter.submitList(ArrayList(messageList))
        recyclerView.scrollToPosition(messageList.size - 1)

        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .add(message)
            .addOnSuccessListener {

            }
            .addOnFailureListener {
                messageList.remove(message)
                adapter.submitList(ArrayList(messageList))
            }
    }

    private fun subscribeToMessages() {
        val chatId = getChatId()

        messagesListener = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_getting_messages, error.message),
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addSnapshotListener
                }
                snapshot?.toObjects(Message::class.java)?.let { messages ->
                    messageList.clear()
                    messageList.addAll(messages)
                    adapter.submitList(ArrayList(messageList))
                    recyclerView.scrollToPosition(messageList.size - 1)
                }

            }
    }

    private fun createChat() {
        val chatId = getChatId()
        val chatRef = firestore.collection("chats").document(chatId)
        chatRef.get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    Log.d("ChatFragment", "Chat with ID: $chatId already exists.")
                } else {
                    val participants =
                        listOf(currentUserId, receiverId).sorted() // Ordina i partecipanti
                    val chatData = hashMapOf(
                        "participants" to participants
                    )
                    Log.d(
                        "ChatFragment",
                        "No existing chat found. Creating new chat with participants: $participants"
                    )
                    chatRef.set(chatData)
                        .addOnSuccessListener {
                            Log.d(
                                "ChatFragment",
                                "Chat created successfully with participants: $participants"
                            )
                        }
                        .addOnFailureListener {
                            Log.e(
                                "ChatFragment",
                                "Error creating chat with participants: $participants",
                                it
                            )
                        }
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_checking_chat_existence, exception.message),
                    Toast.LENGTH_SHORT
                ).show()

            }
    }

    private fun getChatId(): String {
        val sortedParticipants = listOf(currentUserId, receiverId).sorted()
        return sortedParticipants.joinToString("_")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        messagesListener.remove()
    }
}
