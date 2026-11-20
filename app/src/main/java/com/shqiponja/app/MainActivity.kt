package com.shqiponja.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : Activity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val green = Color.rgb(20, 130, 70)
    private var messagesListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser != null) {
            showHome()
        } else {
            showLogin()
        }
    }

    private fun showLogin() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(35, 35, 35, 35)
        layout.setBackgroundColor(Color.WHITE)

        val title = TextView(this)
        title.text = "🦅 CHAT SHQIPONJA"
        title.textSize = 28f
        title.setTextColor(green)
        title.gravity = Gravity.CENTER

        val subtitle = TextView(this)
        subtitle.text = "Regjistrohu ose hyr në llogarinë tënde"
        subtitle.textSize = 18f
        subtitle.gravity = Gravity.CENTER

        val emailInput = EditText(this)
        emailInput.hint = "Adresa e emailit"
        emailInput.inputType =
            InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        val passwordInput = EditText(this)
        passwordInput.hint = "Fjalëkalimi"
        passwordInput.inputType =
            InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_PASSWORD

        val registerButton = Button(this)
        registerButton.text = "REGJISTROHU"

        val loginButton = Button(this)
        loginButton.text = "HYR NË LLOGARI"

        val guestButton = Button(this)
        guestButton.text = "HYR SI VIZITOR 🦅"

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(emailInput)
        layout.addView(passwordInput)
        layout.addView(registerButton)
        layout.addView(loginButton)
        layout.addView(guestButton)

        setContentView(layout)

        registerButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(
                    this,
                    "Plotëso emailin dhe fjalëkalimin",
                    Toast.LENGTH_LONG
                ).show()
            } else if (password.length < 6) {
                Toast.makeText(
                    this,
                    "Fjalëkalimi duhet të ketë të paktën 6 karaktere",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            saveUserProfile()
                            showHome()
                        } else {
                            Toast.makeText(
                                this,
                                "Regjistrimi dështoi: ${task.exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
        }

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(
                    this,
                    "Plotëso emailin dhe fjalëkalimin",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            saveUserProfile()
                            showHome()
                        } else {
                            Toast.makeText(
                                this,
                                "Hyrja dështoi: ${task.exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
        }

        guestButton.setOnClickListener {
            showHome()
        }
    }

    private fun showHome() {
        messagesListener?.remove()
        messagesListener = null

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(Color.WHITE)

        val header = TextView(this)
        header.text = "🦅 CHAT SHQIPONJA"
        header.textSize = 26f
        header.setTextColor(Color.WHITE)
        header.setBackgroundColor(green)
        header.gravity = Gravity.CENTER
        header.setPadding(15, 25, 15, 25)

        val subtitle = TextView(this)
        subtitle.text = "Bisedat e tua"
        subtitle.textSize = 21f
        subtitle.setTextColor(Color.BLACK)
        subtitle.setPadding(20, 20, 20, 10)

        val profileButton = Button(this)
        profileButton.text = "PROFILI IM"

        profileButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Profili im 🦅")
                .setMessage(
                    "CHAT SHQIPONJA\n\nEmail: ${
                        auth.currentUser?.email ?: "Vizitor"
                    }"
                )
                .setPositiveButton("MBYLL", null)
                .show()
        }

        val searchButton = Button(this)
        searchButton.text = "🔎 KËRKO KONTAKT"

        searchButton.setOnClickListener {
            val input = EditText(this)
            input.hint = "Shkruaj username"

            AlertDialog.Builder(this)
                .setTitle("Kërko kontakt")
                .setView(input)
                .setPositiveButton("KËRKO") { _, _ ->
                    val query = input.text.toString().trim()

                    if (query.isNotEmpty()) {
                        searchUser(query)
                    } else {
                        Toast.makeText(
                            this,
                            "Shkruaj username",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("ANULO", null)
                .show()
        }

        val newChatButton = Button(this)
        newChatButton.text = "＋ BISEDË E RE"

        newChatButton.setOnClickListener {
            val input = EditText(this)
            input.hint = "Username i kontaktit"

            AlertDialog.Builder(this)
                .setTitle("Bisedë e re")
                .setView(input)
                .setPositiveButton("KËRKO") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        searchUser(name)
                    }
                }
                .setNegativeButton("ANULO", null)
                .show()
        }

        val chatList = TextView(this)
        chatList.text = "Nuk ke ende biseda.\nFillo një bisedë të re!"
        chatList.textSize = 17f
        chatList.gravity = Gravity.CENTER
        chatList.setPadding(10, 50, 10, 50)

        val logoutButton = Button(this)
        logoutButton.text = "DIL NGA LLOGARIA"

        logoutButton.setOnClickListener {
            messagesListener?.remove()
            auth.signOut()
            showLogin()
        }

        layout.addView(header)
        layout.addView(subtitle)
        layout.addView(profileButton)
        layout.addView(newChatButton)
        layout.addView(searchButton)
        layout.addView(chatList)
        layout.addView(logoutButton)

        setContentView(layout)
    }

    private fun saveUserProfile() {
        val user = auth.currentUser ?: return

        val userData = hashMapOf(
            "uid" to user.uid,
            "username" to (
                user.email?.substringBefore("@")?.lowercase() ?: "user"
            ),
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users")
            .document(user.uid)
            .set(userData)
    }

    private fun searchUser(query: String) {
        val currentUid = auth.currentUser?.uid

        if (currentUid == null) {
            Toast.makeText(
                this,
                "Duhet të hysh në llogari",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val username = query.removePrefix("@").trim().lowercase()

        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { documents ->

                val otherUsers = documents.filter {
                    it.id != currentUid
                }

                if (otherUsers.isEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Nuk u gjet")
                        .setMessage("Nuk u gjet asnjë përdorues.")
                        .setPositiveButton("MBYLL", null)
                        .show()
                } else {
                    val user = otherUsers.first()
                    val foundUsername =
                        user.getString("username") ?: "Përdorues"

                    AlertDialog.Builder(this)
                        .setTitle("Përdoruesi u gjet! 🦅")
                        .setMessage("Username: @$foundUsername")
                        .setPositiveButton("HAP BISEDË") { _, _ ->
                            openChat(user.id, foundUsername)
                        }
                        .setNegativeButton("MBYLL", null)
                        .show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Gabim gjatë kërkimit: ${it.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun openChat(
        otherUserId: String,
        otherUsername: String
    ) {
        val currentUserId = auth.currentUser?.uid ?: return

        val participants = listOf(
            currentUserId,
            otherUserId
        ).sorted()

        val chatId = participants.joinToString("_")

        val chatData = hashMapOf(
            "participants" to participants,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("chats")
            .document(chatId)
            .set(
                chatData,
                com.google.firebase.firestore.SetOptions.merge()
            )
            .addOnSuccessListener {
                showChat(chatId, otherUsername)
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Gabim: ${it.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun showChat(
        chatId: String,
        otherUsername: String
    ) {
        messagesListener?.remove()

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(Color.WHITE)

        val header = TextView(this)
        header.text = "‹   @$otherUsername"
        header.textSize = 22f
        header.setTextColor(Color.WHITE)
        header.setBackgroundColor(green)
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(20, 22, 20, 22)

        header.setOnClickListener {
            messagesListener?.remove()
            showHome()
        }

        val scrollView = ScrollView(this)

        val messageList = LinearLayout(this)
        messageList.orientation = LinearLayout.VERTICAL
        messageList.setPadding(12, 12, 12, 12)

        scrollView.addView(messageList)

        val inputLayout = LinearLayout(this)
        inputLayout.orientation = LinearLayout.HORIZONTAL
        inputLayout.gravity = Gravity.CENTER_VERTICAL
        inputLayout.setPadding(8, 8, 8, 8)

        val messageInput = EditText(this)
        messageInput.hint = "Shkruaj mesazhin..."
        messageInput.inputType =
            InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        messageInput.maxLines = 3

        val sendButton = Button(this)
        sendButton.text = "DËRGO"

        inputLayout.addView(
            messageInput,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        inputLayout.addView(sendButton)

        layout.addView(header)

        layout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        layout.addView(inputLayout)

        setContentView(layout)

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            val senderId = auth.currentUser?.uid

            if (senderId == null) {
                Toast.makeText(
                    this,
                    "Duhet të hysh në llogari",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (text.isEmpty()) {
                return@setOnClickListener
            }

            if (text.length > 4000) {
                Toast.makeText(
                    this,
                    "Mesazhi është shumë i gjatë",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val messageData = hashMapOf(
                "senderId" to senderId,
                "text" to text,
                "createdAt" to System.currentTimeMillis()
            )

            sendButton.isEnabled = false

            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .add(messageData)
                .addOnSuccessListener {
                    messageInput.text.clear()
                    sendButton.isEnabled = true
                }
                .addOnFailureListener {
                    sendButton.isEnabled = true

                    Toast.makeText(
                        this,
                        "Mesazhi nuk u dërgua: ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        messagesListener = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("createdAt")
            .addSnapshotListener { snapshots, error ->

                if (error != null) {
                    Toast.makeText(
                        this,
                        "Gabim gjatë marrjes së mesazheve: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addSnapshotListener
                }

                messageList.removeAllViews()

                snapshots?.documents?.forEach { document ->

                    val senderId =
                        document.getString("senderId") ?: ""

                    val text =
                        document.getString("text") ?: ""

                    val message = TextView(this)
                    message.text = text
                    message.textSize = 17f
                    message.setTextColor(Color.BLACK)
                    message.setPadding(18, 12, 18, 12)
                    message.setBackgroundColor(
                        if (senderId == auth.currentUser?.uid) {
                            Color.rgb(210, 245, 220)
                        } else {
                            Color.rgb(235, 235, 235)
                        }
                    )

                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.gravity =
                        if (senderId == auth.currentUser?.uid) {
                            Gravity.END
                        } else {
                            Gravity.START
                        }

                    val params = LinearLayout.LayoutParams(
                        -2,
                        -2
                    )

                    params.setMargins(5, 6, 5, 6)

                    row.addView(message, params)
                    messageList.addView(row)
                }

                scrollView.post {
                    scrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
    }

    override fun onDestroy() {
        messagesListener?.remove()
        messagesListener = null
        super.onDestroy()
    }
}
