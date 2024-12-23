package com.example.taskmanagement.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.taskmanagement.R
import com.google.firebase.auth.FirebaseAuth

class ChatViewAdapter(
    private val chatList: List<Chat>,
    private val onChatClicked: (Chat) -> Unit
) : RecyclerView.Adapter<ChatViewAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chatName: TextView = view.findViewById(R.id.textViewChatName)
        val lastMessage: TextView = view.findViewById(R.id.textViewLastMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chatList[position]
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

        val otherParticipant = chat.participants.firstOrNull { it != currentUserEmail }

        holder.chatName.text = otherParticipant ?:
                holder.itemView.context.getString(R.string.user_not_authenticated)
        holder.lastMessage.text = chat.lastMessage?.text ?:
                holder.itemView.context.getString(R.string.no_message)

        holder.itemView.setOnClickListener {
            onChatClicked(chat)
        }
    }




    override fun getItemCount() = chatList.size
}
