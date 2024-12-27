package com.example.taskmanagement.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.taskmanagement.R
import com.example.taskmanagement.databinding.FragmentEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileFragment : Fragment() {

    private lateinit var binding: FragmentEditProfileBinding
    private var firebaseAuth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentEditProfileBinding.inflate(inflater, container, false)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()


        loadUserProfile()

        binding.buttonSaveProfile.setOnClickListener {
            saveUserProfile()
            findNavController().navigate(R.id.action_editProfileFragment_to_profileFragment)
        }
        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }

        return binding.root
    }

    private fun loadUserProfile() {
        val userId = firebaseAuth?.currentUser?.email

        userId?.let {
            firestore?.collection("usersData")
                ?.document(it)
                ?.get()
                ?.addOnSuccessListener { document ->
                    if (document.exists()) {
                        val userData = document.toObject(UserData::class.java)
                        userData?.let { user ->
                            binding.editTextName.setText(user.firstName)
                            binding.editTextSurname.setText(user.lastName)
                            binding.editTextStreet.setText(user.street)
                            binding.editTextSkills.setText(user.skills)
                        }
                    }
                }
                ?.addOnFailureListener {
                    Toast.makeText(context, "Errore nel caricamento dei dati", Toast.LENGTH_SHORT).show()
                }
        }
    }


    private fun saveUserProfile() {
        val email = firebaseAuth?.currentUser?.email
        val firstName = binding.editTextName.text.toString().trim()
        val lastName = binding.editTextSurname.text.toString().trim()
        val street = binding.editTextStreet.text.toString().trim()
        val skills = binding.editTextSkills.text.toString().trim()

        if (firstName.isEmpty() || lastName.isEmpty() || street.isEmpty() || skills.isEmpty()) {
            Toast.makeText(context, "Compila tutti i campi", Toast.LENGTH_SHORT).show()
            return
        }

        val userData = UserData(
            emailUser = email ?: "",
            firstName = firstName,
            lastName = lastName,
            street = street,
            skills = skills
        )

        email?.let {
            firestore?.collection("usersData")
                ?.document(it)
                ?.set(userData)
                ?.addOnSuccessListener {
                    Toast.makeText(context, "Profilo aggiornato", Toast.LENGTH_SHORT).show()
                }
                ?.addOnFailureListener {
                    Toast.makeText(context, "Errore nell'aggiornamento del profilo", Toast.LENGTH_SHORT).show()
                }
        }
    }

}
