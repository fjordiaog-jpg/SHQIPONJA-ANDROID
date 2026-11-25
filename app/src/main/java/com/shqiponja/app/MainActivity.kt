package com.shqiponja.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig
import com.zegocloud.uikit.prebuilt.call.invite.widget.ZegoSendCallInvitationButton
import com.zegocloud.uikit.service.defines.ZegoUIKitUser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : Activity() {

    private val PICK_PROFILE_IMAGE = 1001
    private val PICK_CHAT_IMAGE = 1002

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val storage by lazy { FirebaseStorage.getInstance() }

    // TEMA E APLIKACIONIT (STILI ORIGJINAL WHATSAPP - DARK & LIGHT)
    private val isLightTheme: Boolean
        get() = getSharedPreferences("shqiponja_settings", MODE_PRIVATE)
            .getString("theme", "light") == "light"

    private val waTealDark: Int
        get() = if (isLightTheme) Color.rgb(7, 94, 84) else Color.rgb(31, 44, 52)

    private val waTealGreen: Int
        get() = if (isLightTheme) Color.rgb(18, 140, 126) else Color.rgb(0, 168, 132)

    private val waLightGreen = Color.rgb(37, 211, 102)

    private val waChatBg: Int
        get() = if (isLightTheme) Color.rgb(236, 229, 221) else Color.rgb(11, 20, 26)

    private val waBubbleSent: Int
        get() = if (isLightTheme) Color.rgb(220, 248, 198) else Color.rgb(0, 92, 75)

    private val waBubbleReceived: Int
        get() = if (isLightTheme) Color.WHITE else Color.rgb(32, 44, 51)

    private val pageBackground: Int
        get() = if (isLightTheme) Color.WHITE else Color.rgb(17, 27, 33)

    private val cardBackground: Int
        get() = if (isLightTheme) Color.rgb(245, 247, 249) else Color.rgb(32, 44, 51)

    private val primaryText: Int
        get() = if (isLightTheme) Color.rgb(28, 30, 33) else Color.rgb(233, 237, 239)

    private val secondaryText: Int
        get() = if (isLightTheme) Color.rgb(102, 119, 129) else Color.rgb(134, 150, 160)

    private val zegoResourceId = "shqiponja_call"

    private var messagesListener: ListenerRegistration? = null
    private var chatsListener: ListenerRegistration? = null

    private var zegoInitialized = false
    private var zegoInitializing = false
    private var selectedHomeTab = "chats"

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingChatId: String? = null
    private var pendingVoiceChatId: String? = null
    private var activeVoiceButton: TextView? = null
    private var isRecording = false

    private var mediaPlayer: MediaPlayer? = null
    private var pendingUploadChatId: String? = null

    // ---------------------------------------------------------
    // 1. CRASH LOGGER DHE REGJISTRIMI I GABIMEVE
    // ---------------------------------------------------------
    private fun installCrashLogger() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = File(filesDir, "last_crash.txt")
                crashFile.writeText(
                    "CHAT SHQIPONJA - GABIM I REGJISTRUAR\n" +
                    "Koha: ${Date()}\n" +
                    "Thread: ${thread.name}\n\n" +
                    android.util.Log.getStackTraceString(throwable)
                )
            } catch (_: Throwable) {
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun showSavedCrashIfAny() {
        try {
            val crashFile = File(filesDir, "last_crash.txt")
            if (!crashFile.exists()) return

            val crashText = crashFile.readText()
            if (crashText.isBlank()) return

            val textView = TextView(this).apply {
                text = crashText
                textSize = 13f
                setTextIsSelectable(true)
                setPadding(20, 15, 20, 15)
            }

            val scrollView = ScrollView(this).apply {
                addView(textView)
            }

            AlertDialog.Builder(this)
                .setTitle("U regjistrua një gabim")
                .setMessage("Aplikacioni u mbyll nga një gabim. Shtyp KOPJO dhe ma dërgo tekstin.")
                .setView(scrollView)
                .setPositiveButton("KOPJO") { _, _ ->
                    try {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("CHAT SHQIPONJA CRASH", crashText))
                        toast("Gabimi u kopjua. Ma dërgo tekstin.")
                    } catch (_: Throwable) {
                        toast("Nuk u kopjua gabimi.")
                    }
                }
                .setNegativeButton("FSHIJ & MBYLL") { _, _ ->
                    crashFile.delete()
                }
                .show()
        } catch (_: Throwable) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger()

        try {
            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()

            if (auth.currentUser != null) {
                showHome()
            } else {
                showLogin()
            }

            window.decorView.post {
                showSavedCrashIfAny()
            }
        } catch (e: Throwable) {
            Toast.makeText(
                this,
                "Gabim në hapjen e aplikacionit: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.remove()
        chatsListener?.remove()
        try {
            mediaPlayer?.release()
            mediaRecorder?.release()
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------
    // 2. ZGJIDHJA E SIGURT PËR TIMESTAMP (NDALON CRASH-IN)
    // ---------------------------------------------------------
    private fun getSafeTimestampMillis(doc: DocumentSnapshot, field: String = "createdAt"): Long {
        val raw = doc.get(field) ?: return 0L
        return when (raw) {
            is Timestamp -> raw.toDate().time
            is Long -> raw
            is Number -> raw.toLong()
            is Date -> raw.time
            is String -> raw.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0L) return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }

    private fun toast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------
    // 3. KONFIGURIMI I ZEGOCLOUD (AUDIO & VIDEO CALLS)
    // ---------------------------------------------------------
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
    ): ZegoSendCallInvitationButton {
        val button = ZegoSendCallInvitationButton(this)
        button.setIsVideoCall(video)
        button.setResourceID(zegoResourceId)
        button.setInvitees(
            listOf(ZegoUIKitUser(toZegoUserId(otherUserId), otherUsername))
        )
        button.text = if (video) "📹" else "📞"
        button.textSize = 18f
        button.setTextColor(Color.WHITE)
        button.gravity = Gravity.CENTER
        button.setPadding(dp(8), dp(6), dp(8), dp(6))
        button.setBackgroundColor(Color.TRANSPARENT)
        return button
    }

    // ---------------------------------------------------------
    // 4. EKRANI I HYRJES (LOGIN & REGJISTRIM - STILI WHATSAPP)
    // ---------------------------------------------------------
    private fun showLogin() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(pageBackground)
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val logo = TextView(this).apply {
            text = "🦅"
            textSize = 68f
            gravity = Gravity.CENTER
        }
        content.addView(logo)

        val title = TextView(this).apply {
            text = "Chat Shqiponja"
            textSize = 28f
            setTextColor(waTealGreen)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        content.addView(title)

        val subtitle = TextView(this).apply {
            text = "Mesazhe falas, thirrje video dhe audio të sigurta"
            textSize = 14f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(24))
        }
        content.addView(subtitle)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            background = GradientDrawable().apply {
                setColor(cardBackground)
                cornerRadius = dp(18).toFloat()
            }
            elevation = dp(4).toFloat()
        }

        fun makeInput(hintText: String, type: Int): EditText {
            val input = EditText(this).apply {
                hint = hintText
                textSize = 15f
                setTextColor(primaryText)
                setHintTextColor(secondaryText)
                inputType = type
                setSingleLine(true)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = GradientDrawable().apply {
                    setColor(pageBackground)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.rgb(200, 205, 210))
                }
            }
            val params = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(6), 0, dp(12))
            }
            card.addView(input, params)
            return input
        }

        val emailInput = makeInput(
            "Adresa e emailit",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        val passwordInput = makeInput(
            "Fjalëkalimi",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )

        fun makeButton(text: String, bgColor: Int, txtColor: Int): Button {
            val button = Button(this).apply {
                this.text = text
                textSize = 15f
                setTextColor(txtColor)
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = false
                background = GradientDrawable().apply {
                    setColor(bgColor)
                    cornerRadius = dp(24).toFloat()
                }
            }
            val params = LinearLayout.LayoutParams(-1, dp(50)).apply {
                setMargins(0, dp(8), 0, dp(6))
            }
            card.addView(button, params)
            return button
        }

        val loginButton = makeButton("VAZHDO (HYR)", waTealGreen, Color.WHITE)
        val registerButton = makeButton("KRIJO LLOGARI TË RE", waLightGreen, Color.WHITE)
        val guestButton = makeButton("HYR SI VIZITOR", Color.rgb(110, 125, 135), Color.WHITE)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val pass = passwordInput.text.toString().trim()
            if (email.isEmpty() || pass.isEmpty()) {
                toast("Shkruaj emailin dhe fjalëkalimin.")
                return@setOnClickListener
            }
            auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener {
                    saveUserToFirestore(it.user?.uid ?: "", email)
                    showHome()
                }
                .addOnFailureListener { toast("Gabim: ${it.message}") }
        }

        registerButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val pass = passwordInput.text.toString().trim()
            if (email.isEmpty() || pass.length < 6) {
                toast("Shkruaj emailin dhe fjalëkalim me të paktën 6 shkronja.")
                return@setOnClickListener
            }
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener {
                    saveUserToFirestore(it.user?.uid ?: "", email)
                    showHome()
                }
                .addOnFailureListener { toast("Gabim regjistrimi: ${it.message}") }
        }

        guestButton.setOnClickListener {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    val uid = it.user?.uid ?: UUID.randomUUID().toString()
                    saveUserToFirestore(uid, "vizitor_${uid.take(5)}@shqiponja.app")
                    showHome()
                }
                .addOnFailureListener { toast("Gabim: ${it.message}") }
        }

        content.addView(card)
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun saveUserToFirestore(uid: String, email: String) {
        if (uid.isBlank()) return
        val username = email.substringBefore("@").ifBlank { "Përdorues" }
        val userMap = hashMapOf(
            "uid" to uid,
            "email" to email,
            "username" to username,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).set(userMap, com.google.firebase.firestore.SetOptions.merge())
    }

    // ---------------------------------------------------------
    // 5. EKRANI KRYESOR WHATSAPP (BISEDAT | PËRDORUESIT | PROFILI)
    // ---------------------------------------------------------
    private fun showHome() {
        initializeZego()
        requestCallPermissions()

        val rootFrame = FrameLayout(this).apply {
            setBackgroundColor(pageBackground)
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        // HEADER-I ORIGJINAL I WHATSAPP
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(waTealDark)
            elevation = dp(4).toFloat()
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(14), dp(10))
        }

        val appTitle = TextView(this).apply {
            text = "Chat Shqiponja"
            textSize = 21f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        val themeBtn = TextView(this).apply {
            text = if (isLightTheme) "🌙" else "☀️"
            textSize = 19f
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                val nextTheme = if (isLightTheme) "dark" else "light"
                getSharedPreferences("shqiponja_settings", MODE_PRIVATE)
                    .edit().putString("theme", nextTheme).apply()
                showHome()
            }
        }

        val logoutBtn = TextView(this).apply {
            text = "Dil"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(6), dp(10), dp(6))
            setOnClickListener {
                messagesListener?.remove()
                chatsListener?.remove()
                auth.signOut()
                showLogin()
            }
        }

        topRow.addView(appTitle)
        topRow.addView(themeBtn)
        topRow.addView(logoutBtn)
        header.addView(topRow)

        // TAB-ET E WHATSAPP (BISEDAT | KONTAKTET | CILËSIMET)
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }

        val contentArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }

        fun makeTab(label: String, tabKey: String): LinearLayout {
            val isSelected = selectedHomeTab == tabKey
            val tabContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val tv = TextView(this).apply {
                text = label
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(if (isSelected) Color.WHITE else Color.argb(180, 255, 255, 255))
                setPadding(0, dp(10), 0, dp(10))
            }
            val indicator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(3))
                setBackgroundColor(if (isSelected) Color.WHITE else Color.TRANSPARENT)
            }
            tabContainer.addView(tv)
            tabContainer.addView(indicator)
            tabContainer.setOnClickListener {
                selectedHomeTab = tabKey
                showHome()
            }
            return tabContainer
        }

        tabBar.addView(makeTab("BISEDAT", "chats"))
        tabBar.addView(makeTab("KONTAKTET", "users"))
        tabBar.addView(makeTab("PROFILI", "profile"))
        header.addView(tabBar)

        mainLayout.addView(header)
        mainLayout.addView(contentArea)
        rootFrame.addView(mainLayout)

        // BUTONI LUNDRUES (FAB) I GJELBËR SI WHATSAPP
        val fab = TextView(this).apply {
            text = "💬"
            textSize = 22f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(waLightGreen)
            }
            elevation = dp(6).toFloat()
            layoutParams = FrameLayout.LayoutParams(dp(58), dp(58)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, dp(20), dp(22))
            }
            setOnClickListener {
                selectedHomeTab = "users"
                showHome()
            }
        }
        rootFrame.addView(fab)

        setContentView(rootFrame)

        when (selectedHomeTab) {
            "chats" -> showChatsPage(contentArea)
            "users" -> showUsersPage(contentArea)
            "profile" -> showProfilePage(contentArea)
        }
    }

    // ---------------------------------------------------------
    // 6. LISTA E BISEDAVE (showChatsPage - ME RREGULLIMIN E TIMESTAMP)
    // ---------------------------------------------------------
    private fun showChatsPage(container: FrameLayout) {
        container.removeAllViews()
        chatsListener?.remove()

        val currentUid = auth.currentUser?.uid ?: return

        val scroll = ScrollView(this)
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(80))
        }
        scroll.addView(listLayout)
        container.addView(scroll)

        chatsListener = db.collection("chats")
            .whereArrayContains("participants", currentUid)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                listLayout.removeAllViews()

                val sortedDocs = snapshots.documents.sortedByDescending { doc ->
                    getSafeTimestampMillis(doc, "createdAt")
                }

                if (sortedDocs.isEmpty()) {
                    val emptyTv = TextView(this).apply {
                        text = "Nuk ke ende biseda.\nShtyp ikonën e gjelbër poshtë për të nisur një bisedë."
                        textSize = 15f
                        setTextColor(secondaryText)
                        gravity = Gravity.CENTER
                        setPadding(dp(24), dp(60), dp(24), dp(20))
                    }
                    listLayout.addView(emptyTv)
                    return@addSnapshotListener
                }

                for (doc in sortedDocs) {
                    val participants = doc.get("participants") as? List<*> ?: emptyList<Any>()
                    val otherUid = participants.firstOrNull { it != currentUid }?.toString() ?: currentUid
                    val chatTitle = doc.getString("otherName_$currentUid")
                        ?: doc.getString("title")
                        ?: "Bisedë"
                    val lastMessage = doc.getString("lastMessage") ?: "Kliko për të shkruar..."
                    val timeMillis = getSafeTimestampMillis(doc, "createdAt")

                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(16), dp(12), dp(16), dp(12))
                        setOnClickListener {
                            openChatRoom(doc.id, otherUid, chatTitle)
                        }
                    }

                    val avatar = TextView(this).apply {
                        text = chatTitle.take(1).uppercase()
                        textSize = 19f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(waTealGreen)
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)).apply {
                            setMargins(0, 0, dp(14), 0)
                        }
                    }

                    val textCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    }

                    val nameTv = TextView(this).apply {
                        text = chatTitle
                        textSize = 16f
                        setTextColor(primaryText)
                        typeface = Typeface.DEFAULT_BOLD
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }

                    val lastMsgTv = TextView(this).apply {
                        text = lastMessage
                        textSize = 14f
                        setTextColor(secondaryText)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(0, dp(3), 0, 0)
                    }

                    val timeTv = TextView(this).apply {
                        text = formatTime(timeMillis)
                        textSize = 12f
                        setTextColor(secondaryText)
                    }

                    textCol.addView(nameTv)
                    textCol.addView(lastMsgTv)
                    row.addView(avatar)
                    row.addView(textCol)
                    row.addView(timeTv)

                    val divider = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                            setMargins(dp(80), 0, dp(16), 0)
                        }
                        setBackgroundColor(Color.argb(30, 128, 128, 128))
                    }

                    listLayout.addView(row)
                    listLayout.addView(divider)
                }
            }
    }

    // ---------------------------------------------------------
    // 7. LISTA E KONTAKTEVE / PËRDORUESVE
    // ---------------------------------------------------------
    private fun showUsersPage(container: FrameLayout) {
        container.removeAllViews()
        val currentUid = auth.currentUser?.uid ?: return

        val scroll = ScrollView(this)
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(80))
        }
        scroll.addView(listLayout)
        container.addView(scroll)

        db.collection("users").get().addOnSuccessListener { snapshots ->
            listLayout.removeAllViews()
            for (doc in snapshots.documents) {
                val uid = doc.getString("uid") ?: doc.id
                if (uid == currentUid) continue
                val username = doc.getString("username")
                    ?: doc.getString("email")?.substringBefore("@")
                    ?: "Përdorues"

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    setOnClickListener {
                        startOrOpenDirectChat(uid, username)
                    }
                }

                val avatar = TextView(this).apply {
                    text = username.take(1).uppercase()
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(waTealDark)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                        setMargins(0, 0, dp(14), 0)
                    }
                }

                val infoCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }

                val nameTv = TextView(this).apply {
                    text = username
                    textSize = 16f
                    setTextColor(primaryText)
                    typeface = Typeface.DEFAULT_BOLD
                }

                val statusTv = TextView(this).apply {
                    text = "I disponueshëm në Chat Shqiponja"
                    textSize = 13f
                    setTextColor(secondaryText)
                }

                infoCol.addView(nameTv)
                infoCol.addView(statusTv)
                row.addView(avatar)
                row.addView(infoCol)
                listLayout.addView(row)
            }
        }
    }

    private fun startOrOpenDirectChat(otherUid: String, otherName: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val myName = auth.currentUser?.email?.substringBefore("@") ?: "Përdorues"
        val chatId = if (currentUid < otherUid) "${currentUid}_$otherUid" else "${otherUid}_$currentUid"

        val chatData = hashMapOf(
            "participants" to listOf(currentUid, otherUid),
            "otherName_$currentUid" to otherName,
            "otherName_$otherUid" to myName,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("chats").document(chatId)
            .set(chatData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                openChatRoom(chatId, otherUid, otherName)
            }
    }

    // ---------------------------------------------------------
    // 8. DHOMA E BISEDËS (WHATSAPP CHAT ROOM + CALLS + VOICE + FOTO)
    // ---------------------------------------------------------
    private fun openChatRoom(chatId: String, otherUserId: String, otherUsername: String) {
        messagesListener?.remove()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(waChatBg)
        }

        // SHIRITI I SIPËRM I BISEDËS (EMRI + VIDEO CALL + AUDIO CALL)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(waTealDark)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(12), dp(10))
            elevation = dp(4).toFloat()
        }

        val backBtn = TextView(this).apply {
            text = "←"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(4), dp(12), dp(4))
            setOnClickListener {
                messagesListener?.remove()
                showHome()
            }
        }

        val avatar = TextView(this).apply {
            text = otherUsername.take(1).uppercase()
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(waTealGreen)
            }
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                setMargins(0, 0, dp(10), 0)
            }
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        val nameTv = TextView(this).apply {
            text = otherUsername
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val onlineTv = TextView(this).apply {
            text = "online"
            textSize = 12f
            setTextColor(Color.argb(210, 255, 255, 255))
        }

        titleCol.addView(nameTv)
        titleCol.addView(onlineTv)

        topBar.addView(backBtn)
        topBar.addView(avatar)
        topBar.addView(titleCol)

        if (otherUserId.isNotBlank() && otherUserId != auth.currentUser?.uid) {
            topBar.addView(createCallButton(otherUserId, otherUsername, video = true))
            topBar.addView(createCallButton(otherUserId, otherUsername, video = false))
        }

        root.addView(topBar)

        // ZONA E MESAZHEVE
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        val messagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        scroll.addView(messagesContainer)
        root.addView(scroll)

        val currentUid = auth.currentUser?.uid ?: ""

        messagesListener = db.collection("chats").document(chatId)
            .collection("messages")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                messagesContainer.removeAllViews()

                val sortedMsgs = snapshots.documents.sortedBy { doc ->
                    getSafeTimestampMillis(doc, "createdAt")
                }

                for (doc in sortedMsgs) {
                    val senderId = doc.getString("senderId") ?: ""
                    val text = doc.getString("text") ?: ""
                    val type = doc.getString("type") ?: "text"
                    val mediaUrl = doc.getString("mediaUrl") ?: ""
                    val timeMillis = getSafeTimestampMillis(doc, "createdAt")
                    val isMine = senderId == currentUid

                    val rowWrapper = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = if (isMine) Gravity.END else Gravity.START
                        setPadding(0, dp(4), 0, dp(4))
                    }

                    val bubble = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(14), dp(8), dp(14), dp(8))
                        background = GradientDrawable().apply {
                            setColor(if (isMine) waBubbleSent else waBubbleReceived)
                            cornerRadius = dp(16).toFloat()
                        }
                        elevation = dp(1).toFloat()
                    }

                    when (type) {
                        "audio" -> {
                            val playBtn = TextView(this).apply {
                                this.text = "▶️ Dëgjo mesazhin zanor"
                                textSize = 15f
                                setTextColor(primaryText)
                                typeface = Typeface.DEFAULT_BOLD
                                setOnClickListener {
                                    playAudioUrl(mediaUrl)
                                }
                            }
                            bubble.addView(playBtn)
                        }
                        "image" -> {
                            val imgLabel = TextView(this).apply {
                                this.text = "🖼️ Foto e dërguar (Kliko për ta hapur)"
                                textSize = 15f
                                setTextColor(waTealGreen)
                                typeface = Typeface.DEFAULT_BOLD
                                setOnClickListener {
                                    if (mediaUrl.isNotBlank()) {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mediaUrl)))
                                    }
                                }
                            }
                            bubble.addView(imgLabel)
                        }
                        else -> {
                            val msgTv = TextView(this).apply {
                                this.text = text
                                textSize = 16f
                                setTextColor(primaryText)
                            }
                            bubble.addView(msgTv)
                        }
                    }

                    val timeTv = TextView(this).apply {
                        this.text = if (isMine) "${formatTime(timeMillis)} ✓✓" else formatTime(timeMillis)
                        textSize = 11f
                        setTextColor(secondaryText)
                        gravity = Gravity.END
                        setPadding(0, dp(2), 0, 0)
                    }
                    bubble.addView(timeTv)

                    rowWrapper.addView(bubble)
                    messagesContainer.addView(rowWrapper)
                }

                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }

        // SHIRITI I SHKRIMIT POSHTË (WHATSAPP INPUT BAR: FOTO + TEKST + MIC/DËRGO)
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
        }

        val inputCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            background = GradientDrawable().apply {
                setColor(if (isLightTheme) Color.WHITE else Color.rgb(42, 57, 66))
                cornerRadius = dp(26).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins(0, 0, dp(8), 0)
            }
        }

        val input = EditText(this).apply {
            hint = "Mesazh"
            textSize = 16f
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }

        val attachBtn = TextView(this).apply {
            text = "📎"
            textSize = 20f
            setPadding(dp(8), dp(6), dp(6), dp(6))
            setOnClickListener {
                pendingUploadChatId = chatId
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
                startActivityForResult(intent, PICK_CHAT_IMAGE)
            }
        }

        inputCard.addView(input)
        inputCard.addView(attachBtn)

        val actionBtn = TextView(this).apply {
            text = "🎤"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(waTealGreen)
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                actionBtn.setText(if (s.isNullOrBlank()) "🎤" else "➤")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        actionBtn.setOnClickListener {
            val msgText = input.text.toString().trim()
            if (msgText.isNotEmpty()) {
                input.setText("")
                sendTextMessage(chatId, msgText)
            } else {
                toggleVoiceRecording(chatId, actionBtn)
            }
        }

        bottomBar.addView(inputCard)
        bottomBar.addView(actionBtn)
        root.addView(bottomBar)

        setContentView(root)
    }

    private fun sendTextMessage(chatId: String, text: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val msgData = hashMapOf(
            "senderId" to currentUid,
            "text" to text,
            "type" to "text",
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("chats").document(chatId).collection("messages").add(msgData)
        db.collection("chats").document(chatId).update(
            mapOf(
                "lastMessage" to text,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
    }

    // ---------------------------------------------------------
    // 9. REGJISTRIMI I ZËRIT (VOICE MESSAGES) DHE DËRGIMI I FOTOVE
    // ---------------------------------------------------------
    private fun toggleVoiceRecording(chatId: String, btn: TextView) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingVoiceChatId = chatId
            activeVoiceButton = btn
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2002)
            return
        }

        if (!isRecording) {
            try {
                val outFile = File(cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                recordingFile = outFile
                recordingChatId = chatId
                mediaRecorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(outFile.absolutePath)
                    prepare()
                    start()
                }
                isRecording = true
                btn.setText("⏹")
                toast("Duke regjistruar zërin... Shtyp përsëri për ta dërguar.")
            } catch (e: Throwable) {
                isRecording = false
                toast("Gabim mikrofon: ${e.message}")
            }
        } else {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
                btn.setText("🎤")

                val file = recordingFile ?: return
                val targetChat = recordingChatId ?: return
                uploadVoiceFile(targetChat, file)
            } catch (e: Throwable) {
                isRecording = false
                btn.setText("🎤")
                toast("Gabim ndalimi: ${e.message}")
            }
        }
    }

    private fun uploadVoiceFile(chatId: String, file: File) {
        val currentUid = auth.currentUser?.uid ?: return
        val ref = storage.reference.child("voices/${UUID.randomUUID()}.m4a")
        ref.putFile(Uri.fromFile(file)).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { uri ->
                val msgData = hashMapOf(
                    "senderId" to currentUid,
                    "text" to "🎤 Mesazh zanor",
                    "type" to "audio",
                    "mediaUrl" to uri.toString(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("chats").document(chatId).collection("messages").add(msgData)
                db.collection("chats").document(chatId).update(
                    mapOf("lastMessage" to "🎤 Mesazh zanor", "createdAt" to FieldValue.serverTimestamp())
                )
            }
        }.addOnFailureListener {
            toast("Gabim ngarkimi i zërit: ${it.message}")
        }
    }

    private fun playAudioUrl(url: String) {
        if (url.isBlank()) return
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener { start() }
            }
            toast("Duke luajtur mesazhin zanor...")
        } catch (e: Throwable) {
            toast("Nuk mund të luhet audio: ${e.message}")
        }
    }

    private fun chooseProfilePhoto() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(intent, PICK_PROFILE_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data ?: return

        if (requestCode == PICK_PROFILE_IMAGE) {
            val uid = auth.currentUser?.uid ?: return
            val ref = storage.reference.child("profiles/$uid.jpg")
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    db.collection("users").document(uid)
                        .update("photoUrl", downloadUri.toString())
                    toast("Fotoja e profilit u përditësua!")
                }
            }
        } else if (requestCode == PICK_CHAT_IMAGE) {
            val chatId = pendingUploadChatId ?: return
            val currentUid = auth.currentUser?.uid ?: return
            val ref = storage.reference.child("chat_images/${UUID.randomUUID()}.jpg")
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    val msgData = hashMapOf(
                        "senderId" to currentUid,
                        "text" to "🖼️ Foto",
                        "type" to "image",
                        "mediaUrl" to downloadUri.toString(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    db.collection("chats").document(chatId).collection("messages").add(msgData)
                    db.collection("chats").document(chatId).update(
                        mapOf("lastMessage" to "🖼️ Foto", "createdAt" to FieldValue.serverTimestamp())
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // 10. PROFILI I PËRDORUESIT
    // ---------------------------------------------------------
    private fun showProfilePage(container: FrameLayout) {
        container.removeAllViews()
        val user = auth.currentUser

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }

        val avatar = TextView(this).apply {
            text = (user?.email?.take(1) ?: "S").uppercase()
            textSize = 36f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(waTealGreen)
            }
            layoutParams = LinearLayout.LayoutParams(dp(100), dp(100))
            setOnClickListener { chooseProfilePhoto() }
        }
        layout.addView(avatar)

        val changePhotoHint = TextView(this).apply {
            text = "Kliko rrethin për të ndryshuar foton e profilit"
            textSize = 13f
            setTextColor(waTealGreen)
            setPadding(0, dp(10), 0, dp(20))
            setOnClickListener { chooseProfilePhoto() }
        }
        layout.addView(changePhotoHint)

        val emailTv = TextView(this).apply {
            text = "Llogaria: ${user?.email ?: "Vizitor"}"
            textSize = 17f
            setTextColor(primaryText)
            typeface = Typeface.DEFAULT_BOLD
        }
        layout.addView(emailTv)

        val uidTv = TextView(this).apply {
            text = "ID: ${user?.uid ?: "-"}"
            textSize = 12f
            setTextColor(secondaryText)
            setPadding(0, dp(6), 0, dp(24))
        }
        layout.addView(uidTv)

        container.addView(layout)
    }
}
