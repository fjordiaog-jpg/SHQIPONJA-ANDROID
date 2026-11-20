
package com.shqiponja.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig
import com.zegocloud.uikit.prebuilt.call.invite.widget.ZegoSendCallInvitationButton
import com.zegocloud.uikit.service.defines.ZegoUIKitUser

class MainActivity : Activity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val red = Color.rgb(190, 20, 35)
    private val black = Color.rgb(20, 20, 20)
    private val white = Color.WHITE
    private val green = Color.rgb(20, 155, 85)

    private val zegoResourceId = "shqiponja_call"

    private var messagesListener: ListenerRegistration? = null
    private var chatsListener: ListenerRegistration? = null

    private var zegoInitialized = false
    private var zegoInitializing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()

            if (auth.currentUser != null) {
                showHome()
            } else {
                showLogin()
            }
        } catch (e: Throwable) {
            Toast.makeText(
                this,
                "Gabim në hapjen e aplikacionit: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun toZegoUserId(firebaseUid: String): String {
        val bytes = java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(firebaseUid.toByteArray(Charsets.UTF_8))

        return bytes.take(16).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }

    private fun initializeZego() {
        val user = auth.currentUser ?: return

        if (zegoInitialized || zegoInitializing) return

        val appId = BuildConfig.ZEGO_APP_ID.toLongOrNull()
        val appSign = BuildConfig.ZEGO_APP_SIGN

        if (appId == null || appSign.isBlank()) {
            toast("ZEGO AppID ose AppSign mungon në konfigurim.")
            return
        }

        zegoInitializing = true

        try {
            val config = ZegoUIKitPrebuiltCallInvitationConfig()

            ZegoUIKitPrebuiltCallService.init(
                application,
                appId,
                appSign,
                toZegoUserId(user.uid),
                user.email?.substringBefore("@")?.lowercase() ?: "user",
                config
            )

            zegoInitialized = true
        } catch (e: Throwable) {
            toast("Gabim ZEGO: ${e.message}")
        } finally {
            zegoInitializing = false
        }
    }

    private fun requestCallPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 2001)
        }
    }

    private fun createCallButton(
        otherUserId: String,
        otherUsername: String,
        video: Boolean
    ): View {

        val button = ZegoSendCallInvitationButton(this)

        button.setIsVideoCall(video)
        button.setResourceID(zegoResourceId)

        button.setInvitees(
            listOf(
                ZegoUIKitUser(
                    toZegoUserId(otherUserId),
                    otherUsername
                )
            )
        )

        // Pamja e re e butonit ZEGO
        button.text = if (video) {
            "📹  VIDEO"
        } else {
            "📞  TELEFONO"
        }

        button.textSize = 15f
        button.setTextColor(Color.WHITE)
        button.typeface = Typeface.DEFAULT_BOLD
        button.gravity = Gravity.CENTER
        button.isAllCaps = false
        button.setPadding(10, 8, 10, 8)

        val background = GradientDrawable()
        background.setColor(green)
        background.cornerRadius = 22f

        button.background = background
        button.elevation = 5f
        button.minimumHeight = 58

        return button
    }

    private fun showLogin() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(35, 35, 35, 35)
        layout.setBackgroundColor(black)

        val title = TextView(this)
        title.text = "🦅 SHQIPONJA"
        title.textSize = 32f
        title.setTextColor(red)
        title.gravity = Gravity.CENTER

        val subtitle = TextView(this)
        subtitle.text = "Komuniko me të gjithë!"
        subtitle.textSize = 17f
        subtitle.setTextColor(white)
        subtitle.gravity = Gravity.CENTER
        subtitle.setPadding(10, 15, 10, 25)

        val emailInput = EditText(this)
        emailInput.hint = "Adresa e emailit"
        emailInput.setTextColor(white)
        emailInput.setHintTextColor(Color.LTGRAY)
        emailInput.inputType =
            InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        val passwordInput = EditText(this)
        passwordInput.hint = "Fjalëkalimi"
        passwordInput.setTextColor(white)
        passwordInput.setHintTextColor(Color.LTGRAY)
        passwordInput.inputType =
            InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_PASSWORD

        val registerButton = Button(this)
        registerButton.text = "REGJISTROHU"

        val loginButton = Button(this)
        loginButton.text = "HYR NË LLOGARI"

        val guestButton = Button(this)
        guestButton.text = "HYR SI VIZITOR"

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

            if (email.isEmpty() || password.length < 6) {
                toast("Plotëso emailin dhe fjalëkalimin (minimumi 6 karaktere)")
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        saveUserProfile()
                        showHome()
                    } else {
                        toast("Regjistrimi dështoi: ${task.exception?.message}")
                    }
                }
        }

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                toast("Plotëso të dhënat")
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        saveUserProfile()
                        showHome()
                    } else {
                        toast("Hyrja dështoi: ${task.exception?.message}")
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

        chatsListener?.remove()
        chatsListener = null

        val uid = auth.currentUser?.uid

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(black)

        val header = TextView(this)
        header.text = "🦅 SHQIPONJA"
        header.textSize = 27f
        header.setTextColor(white)
        header.setBackgroundColor(red)
        header.gravity = Gravity.CENTER
        header.setPadding(15, 25, 15, 25)

        val profileButton = Button(this)
        profileButton.text = "PROFILI IM"

        profileButton.setOnClickListener {
            val email = auth.currentUser?.email ?: "Vizitor"

            AlertDialog.Builder(this)
                .setTitle("Profili im 🦅")
                .setMessage("SHQIPONJA\n\nEmail: $email")
                .setPositiveButton("MBYLL", null)
                .show()
        }

        val newChatButton = Button(this)
        newChatButton.text = "＋ BISEDË E RE"
        newChatButton.setOnClickListener { askUsername() }

        val searchButton = Button(this)
        searchButton.text = "🔎 KËRKO KONTAKT"
        searchButton.setOnClickListener { askUsername() }

        val subtitle = TextView(this)
        subtitle.text = "BISEDAT E MIA"
        subtitle.textSize = 19f
        subtitle.setTextColor(white)
        subtitle.setPadding(18, 15, 10, 10)

        val scroll = ScrollView(this)
        val chatList = LinearLayout(this)
        chatList.orientation = LinearLayout.VERTICAL
        chatList.setPadding(10, 5, 10, 10)
        scroll.addView(chatList)

        val logoutButton = Button(this)
        logoutButton.text = "DIL NGA LLOGARIA"

        logoutButton.setOnClickListener {
            chatsListener?.remove()
            messagesListener?.remove()

            if (zegoInitialized) {
                try {
                    ZegoUIKitPrebuiltCallService.unInit()
                } catch (e: Throwable) {
                    toast("Gabim gjatë mbylljes së ZEGO-s")
                }
                zegoInitialized = false
            }

            auth.signOut()
            showLogin()
        }

        layout.addView(header)
        layout.addView(profileButton)
        layout.addView(newChatButton)
        layout.addView(searchButton)
        layout.addView(subtitle)

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        layout.addView(logoutButton)

        setContentView(layout)

        if (uid == null) {
            chatList.addView(
                makeLabel("Hyr në llogari për të përdorur bisedat.")
            )
            return
        }

        chatList.addView(makeLabel("Po ngarkohen bisedat..."))

        chatsListener = db.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snapshots, error ->

                if (error != null) {
                    toast("Gabim: ${error.message}")
                    return@addSnapshotListener
                }

                chatList.removeAllViews()

                val documents = snapshots?.documents ?: emptyList()

                if (documents.isEmpty()) {
                    chatList.addView(
                        makeLabel(
                            "Nuk ke ende biseda.\n\nShtyp BISEDË E RE për të filluar! 🦅"
                        )
                    )
                    return@addSnapshotListener
                }

                documents.forEach { document ->
                    val participants =
                        document.get("participants") as? List<*>
                            ?: emptyList<Any>()

                    val otherUid = participants
                        .filterIsInstance<String>()
                        .firstOrNull { it != uid }
                        ?: return@forEach

                    db.collection("users")
                        .document(otherUid)
                        .get()
                        .addOnSuccessListener { userDocument ->

                            val username =
                                userDocument.getString("username")
                                    ?: "Përdorues"

                            db.collection("chats")
                                .document(document.id)
                                .collection("messages")
                                .orderBy(
                                    "createdAt",
                                    Query.Direction.DESCENDING
                                )
                                .limit(1)
                                .get()
                                .addOnSuccessListener { messages ->

                                    val lastMessage =
                                        messages.documents.firstOrNull()
                                            ?.getString("text")
                                            ?: "Fillo bisedën"

                                    val row = LinearLayout(this)
                                    row.orientation = LinearLayout.VERTICAL
                                    row.setPadding(16, 15, 16, 15)
                                    row.setBackgroundColor(
                                        Color.rgb(38, 38, 38)
                                    )

                                    val name = TextView(this)
                                    name.text = "🦅 @$username"
                                    name.textSize = 19f
                                    name.setTextColor(white)

                                    val preview = TextView(this)
                                    preview.text = lastMessage
                                    preview.textSize = 15f
                                    preview.setTextColor(Color.LTGRAY)
                                    preview.setPadding(0, 7, 0, 0)

                                    row.addView(name)
                                    row.addView(preview)

                                    val params =
                                        LinearLayout.LayoutParams(-1, -2)
                                    params.setMargins(3, 5, 3, 5)

                                    row.setOnClickListener {
                                        showChat(
                                            document.id,
                                            username,
                                            otherUid
                                        )
                                    }

                                    chatList.addView(row, params)
                                }
                        }
                }
            }
    }

    private fun makeLabel(text: String): TextView {
        val label = TextView(this)
        label.text = text
        label.textSize = 17f
        label.setTextColor(white)
        label.gravity = Gravity.CENTER
        label.setPadding(15, 45, 15, 45)
        return label
    }

    private fun askUsername() {
        val input = EditText(this)
        input.hint = "Username i kontaktit"

        AlertDialog.Builder(this)
            .setTitle("Kërko kontakt 🦅")
            .setView(input)
            .setPositiveButton("KËRKO") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchUser(query)
                }
            }
            .setNegativeButton("ANULO", null)
            .show()
    }

    private fun saveUserProfile() {
        val user = auth.currentUser ?: return

        val userData = hashMapOf(
            "uid" to user.uid,
            "username" to (
                user.email?.substringBefore("@")?.lowercase()
                    ?: "user"
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
            toast("Duhet të hysh në llogari")
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
                        .setTitle("U gjet përdoruesi! 🦅")
                        .setMessage("@$foundUsername")
                        .setPositiveButton("HAP BISEDË") { _, _ ->
                            openChat(user.id, foundUsername)
                        }
                        .setNegativeButton("MBYLL", null)
                        .show()
                }
            }
            .addOnFailureListener {
                toast("Gabim: ${it.message}")
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
                showChat(chatId, otherUsername, otherUserId)
            }
            .addOnFailureListener {
                toast("Gabim: ${it.message}")
            }
    }

    private fun showChat(
        chatId: String,
        otherUsername: String,
        otherUserId: String
    ) {
        initializeZego()
        requestCallPermissions()

        chatsListener?.remove()
        chatsListener = null

        messagesListener?.remove()
        messagesListener = null

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(black)

        val header = TextView(this)
        header.text = "‹    @$otherUsername"
        header.textSize = 22f
        header.setTextColor(white)
        header.setBackgroundColor(red)
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(20, 22, 20, 22)
        header.setOnClickListener { showHome() }

        // Shiriti i ri i telefonatave
        val callLayout = LinearLayout(this)
        callLayout.orientation = LinearLayout.HORIZONTAL
        callLayout.gravity = Gravity.CENTER
        callLayout.setPadding(10, 12, 10, 12)
        callLayout.setBackgroundColor(Color.rgb(28, 28, 28))

        val voiceCall = createCallButton(
            otherUserId,
            otherUsername,
            false
        )

        val videoCall = createCallButton(
            otherUserId,
            otherUsername,
            true
        )

        val voiceParams = LinearLayout.LayoutParams(
            0,
            58,
            1f
        )
        voiceParams.setMargins(5, 0, 8, 0)

        val videoParams = LinearLayout.LayoutParams(
            0,
            58,
            1f
        )
        videoParams.setMargins(8, 0, 5, 0)

        callLayout.addView(voiceCall, voiceParams)
        callLayout.addView(videoCall, videoParams)

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
        messageInput.setTextColor(white)
        messageInput.setHintTextColor(Color.LTGRAY)
        messageInput.inputType =
            InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        messageInput.maxLines = 3

        val sendButton = Button(this)
        sendButton.text = "➤"

        inputLayout.addView(
            messageInput,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        inputLayout.addView(sendButton)

        layout.addView(header)
        layout.addView(callLayout)

        layout.addView(
            scrollView,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        layout.addView(inputLayout)

        setContentView(layout)

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            val senderId = auth.currentUser?.uid

            if (senderId == null) {
                toast("Duhet të hysh në llogari")
                return@setOnClickListener
            }

            if (text.isEmpty()) return@setOnClickListener

            if (text.length > 4000) {
                toast("Mesazhi është shumë i gjatë")
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
                    toast("Mesazhi nuk u dërgua: ${it.message}")
                }
        }

        messagesListener = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("createdAt")
            .addSnapshotListener { snapshots, error ->

                if (error != null) {
                    toast("Gabim: ${error.message}")
                    return@addSnapshotListener
                }

                messageList.removeAllViews()

                snapshots?.documents?.forEach { document ->

                    val senderId =
                        document.getString("senderId") ?: ""

                    val text =
                        document.getString("text") ?: ""

                    val isMine =
                        senderId == auth.currentUser?.uid

                    val message = TextView(this)
                    message.text = text
                    message.textSize = 16f
                    message.setTextColor(
                        if (isMine) white else Color.BLACK
                    )
                    message.setPadding(18, 13, 18, 13)
                    message.setBackgroundColor(
                        if (isMine) red
                        else Color.rgb(225, 225, 225)
                    )

                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.gravity =
                        if (isMine) Gravity.END
                        else Gravity.START

                    val params =
                        LinearLayout.LayoutParams(-2, -2)
                    params.setMargins(5, 6, 5, 6)

                    row.addView(message, params)
                    messageList.addView(row)
                }

                scrollView.post {
                    scrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        messagesListener?.remove()
        chatsListener?.remove()
        messagesListener = null
        chatsListener = null
        super.onDestroy()
    }
}
