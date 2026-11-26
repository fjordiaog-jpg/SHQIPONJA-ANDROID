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
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
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
import java.util.concurrent.TimeUnit

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
    private var isChatRoomOpen = false

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingChatId: String? = null
    private var pendingVoiceChatId: String? = null
    private var activeVoiceButton: TextView? = null
    private var isRecording = false

    private var mediaPlayer: MediaPlayer? = null
    private var pendingUploadChatId: String? = null
    private var replyMessageId: String? = null
    private var replyMessageText: String? = null
    private var phoneVerificationInProgress = false

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

        try {
            installCrashLogger()

            // ÇDO hap i nisjes mbahet brenda try/catch.
            // Nëse ekrani fillestar ose Firebase dështon, shfaqim arsyen.
            showStartingScreen()

            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()

            if (auth.currentUser != null) {
                showHome()
            } else {
                showLogin()
            }

            window.decorView.post {
                try {
                    showSavedCrashIfAny()
                } catch (e: Throwable) {
                    android.util.Log.e("SHQIPONJA_STARTUP", "Crash dialog error", e)
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("SHQIPONJA_STARTUP", "Application startup failed", e)
            try {
                showStartupError(e)
            } catch (displayError: Throwable) {
                android.util.Log.e("SHQIPONJA_STARTUP", "Could not display startup error", displayError)
                try {
                    val fallback = TextView(this).apply {
                        text = "SHQIPONJA nuk mundi të hapet.\\n\\n" +
                            e.javaClass.simpleName + ": " + (e.message ?: "Gabim i panjohur") +
                            "\\n\\n" + android.util.Log.getStackTraceString(e)
                        textSize = 14f
                        setTextColor(Color.BLACK)
                        setBackgroundColor(Color.WHITE)
                        setPadding(24, 24, 24, 24)
                        setTextIsSelectable(true)
                    }
                    setContentView(fallback)
                } catch (_: Throwable) {
                    // Nëse edhe ekrani rezervë dështon, Android do të ruajë gabimin në Logcat.
                }
            }
        }
    }

    private fun showStartingScreen() {
        val starting = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(pageBackground)
        }
        val title = TextView(this).apply {
            text = "🦅 SHQIPONJA"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(waTealGreen)
            typeface = Typeface.DEFAULT_BOLD
        }
        val progress = ProgressBar(this)
        starting.addView(title)
        starting.addView(progress)
        setContentView(starting)
    }

    private fun showStartupError(error: Throwable) {
        val details = android.util.Log.getStackTraceString(error)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(pageBackground)
        }
        val title = TextView(this).apply {
            text = "SHQIPONJA nuk mundi të hapet"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryText)
            gravity = Gravity.CENTER
        }
        val message = TextView(this).apply {
            text = "${error.javaClass.simpleName}: ${error.message ?: "Pa përshkrim"}\\n\\n$details"
            textSize = 12f
            setTextColor(primaryText)
            setTextIsSelectable(true)
            setPadding(0, dp(16), 0, dp(16))
        }
        val copy = Button(this).apply {
            text = "KOPJO GABIMIN"
            setOnClickListener {
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SHQIPONJA startup error", details))
                    Toast.makeText(this@MainActivity, "Gabimi u kopjua.", Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {
                    Toast.makeText(this@MainActivity, "Nuk u kopjua gabimi.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val scroll = ScrollView(this).apply {
            addView(message)
        }
        layout.addView(title)
        layout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(copy, LinearLayout.LayoutParams(-1, -2))
        setContentView(layout)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            isChatRoomOpen -> {
                messagesListener?.remove()
                isChatRoomOpen = false
                showHome()
            }
            selectedHomeTab != "chats" -> {
                selectedHomeTab = "chats"
                showHome()
            }
            else -> {
                AlertDialog.Builder(this)
                    .setMessage("Dëshiron të dalësh nga SHQIPONJA?")
                    .setNegativeButton("QËNDRO", null)
                    .setPositiveButton("DIL") { _, _ -> finish() }
                    .show()
            }
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
        isChatRoomOpen = false
        // Shërbimet opsionale nisen vetëm pasi ndërtohet dhe shfaqet ekrani.
        val rootFrame = FrameLayout(this).apply {
            setBackgroundColor(pageBackground)
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        // HEADER I RIFRESKUAR: pamje e frymëzuar nga WhatsApp,
        // me identitetin e veçantë të SHQIPONJA-s.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(waTealDark)
            elevation = dp(3).toFloat()
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(10), dp(8))
        }

        val appTitle = TextView(this).apply {
            text = "SHQIPONJA"
            textSize = 21f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        val searchBtn = TextView(this).apply {
            text = "⌕"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener {
                toast("Kërkimi i bisedave do të shtohet në hapin tjetër.")
            }
        }

        val menuBtn = TextView(this).apply {
            text = "⋮"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener {
                val options = arrayOf("Profili", "Ndrysho pamjen", "Dil nga llogaria")
                AlertDialog.Builder(this@MainActivity)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> {
                                selectedHomeTab = "profile"
                                showHome()
                            }
                            1 -> {
                                val nextTheme = if (isLightTheme) "dark" else "light"
                                getSharedPreferences("shqiponja_settings", MODE_PRIVATE)
                                    .edit().putString("theme", nextTheme).apply()
                                showHome()
                            }
                            2 -> {
                                messagesListener?.remove()
                                chatsListener?.remove()
                                auth.signOut()
                                showLogin()
                            }
                        }
                    }
                    .show()
            }
        }

        topRow.addView(appTitle)
        topRow.addView(searchBtn)
        topRow.addView(menuBtn)
        header.addView(topRow)

        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 4f
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
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(if (isSelected) Color.WHITE else Color.argb(185, 255, 255, 255))
                setPadding(dp(2), dp(11), dp(2), dp(10))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
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
        tabBar.addView(makeTab("PËRDITËSIME", "updates"))
        tabBar.addView(makeTab("KOMUNITETE", "communities"))
        tabBar.addView(makeTab("TELEFONATA", "calls"))
        header.addView(tabBar)

        mainLayout.addView(header)
        mainLayout.addView(contentArea)
        rootFrame.addView(mainLayout)

        // Butoni lundrues ndryshon sipas skedës aktive.
        val fab = TextView(this).apply {
            text = when (selectedHomeTab) {
                "updates" -> "＋"
                "communities" -> "👥"
                "calls" -> "📞"
                else -> "＋"
            }
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
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
                when (selectedHomeTab) {
                    "chats" -> {
                        selectedHomeTab = "users"
                        showHome()
                    }
                    "updates" -> toast("Publikimi i statusit do të aktivizohet në hapin e statusit.")
                    "communities" -> toast("Krijimi i komuniteteve do të shtohet në hapin e grupeve.")
                    "calls" -> {
                        selectedHomeTab = "users"
                        showHome()
                    }
                }
            }
        }

        // Skeda e kontakteve mbetet e arritshme nga butoni +.
        if (selectedHomeTab != "users" && selectedHomeTab != "profile") {
            rootFrame.addView(fab)
        }

        setContentView(rootFrame)

        when (selectedHomeTab) {
            "chats" -> showChatsPage(contentArea)
            "updates" -> showUpdatesPage(contentArea)
            "communities" -> showCommunitiesPage(contentArea)
            "calls" -> showCallsPage(contentArea)
            "users" -> showUsersPage(contentArea)
            "profile" -> showProfilePage(contentArea)
            else -> showChatsPage(contentArea)
        }

        // ZEGO nuk inicializohet automatikisht gjatë hapjes së aplikacionit.
        // Aktivizohet vetëm kur përdoruesi hap skedën TELEFONATA.
        if (selectedHomeTab == "calls") {
            window.decorView.post {
                try {
                    initializeZego()
                    requestCallPermissions()
                } catch (e: Throwable) {
                    android.util.Log.e("SHQIPONJA_ZEGO", "ZEGO initialization failed", e)
                    toast("Shërbimi i telefonatave nuk u nis: ${e.message}")
                }
            }
        }
    }

    private fun showUpdatesPage(container: FrameLayout) {
        container.removeAllViews()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(90))
            setBackgroundColor(pageBackground)
        }

        val heading = TextView(this).apply {
            text = "Përditësimet"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryText)
            setPadding(0, 0, 0, dp(5))
        }

        val subtitle = TextView(this).apply {
            text = "Ndaj momente me kontaktet e tua"
            textSize = 13f
            setTextColor(secondaryText)
            setPadding(0, 0, 0, dp(18))
        }

        val myStatusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }

        val avatar = TextView(this).apply {
            text = "＋"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(waTealGreen)
            }
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                setMargins(0, 0, dp(14), 0)
            }
        }

        val statusInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val myStatusTitle = TextView(this).apply {
            text = "Statusi im"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryText)
        }
        val myStatusSubtitle = TextView(this).apply {
            text = "Shto një përditësim"
            textSize = 13f
            setTextColor(secondaryText)
        }
        statusInfo.addView(myStatusTitle)
        statusInfo.addView(myStatusSubtitle)
        myStatusRow.addView(avatar)
        myStatusRow.addView(statusInfo)
        myStatusRow.setOnClickListener {
            toast("Publikimi i statusit 24-orësh do të lidhet me Firebase Storage.")
        }

        val section = TextView(this).apply {
            text = "PËRDITËSIMET E KONTAKTEVE"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(waTealGreen)
            setPadding(0, dp(12), 0, dp(10))
        }

        val empty = TextView(this).apply {
            text = "Nuk ka ende përditësime të reja.\nKur kontaktet të publikojnë status, do të shfaqen këtu."
            textSize = 14f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(35), dp(20), dp(35))
        }

        layout.addView(heading)
        layout.addView(subtitle)
        layout.addView(myStatusRow)
        layout.addView(section)
        layout.addView(empty)
        container.addView(layout)
    }

    private fun showCommunitiesPage(container: FrameLayout) {
        container.removeAllViews()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(90))
            setBackgroundColor(pageBackground)
        }

        val icon = TextView(this).apply {
            text = "👥"
            textSize = 54f
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = "Komunitetet"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryText)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        }
        val description = TextView(this).apply {
            text = "Organizo grupet dhe bisedat në një vend të vetëm. Këtu do të shfaqen komunitetet ku je anëtar."
            textSize = 14f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
        }
        val createButton = TextView(this).apply {
            text = "＋  Krijo komunitet"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(22), dp(12), dp(22), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(waTealGreen)
            }
            setOnClickListener {
                toast("Krijimi i komuniteteve dhe grupeve kërkon konfigurimin e strukturës së grupeve në Firestore.")
            }
        }

        layout.addView(icon)
        layout.addView(title)
        layout.addView(description)
        layout.addView(createButton, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(24), 0, 0)
        })
        container.addView(layout)
    }

    private fun showCallsPage(container: FrameLayout) {
        container.removeAllViews()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBackground)
        }

        val title = TextView(this).apply {
            text = "Telefonatat"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryText)
            setPadding(dp(18), dp(18), dp(18), dp(12))
        }
        root.addView(title)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(90))
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        container.addView(root)

        val currentUid = auth.currentUser?.uid ?: return
        db.collection("users").get().addOnSuccessListener { snapshots ->
            list.removeAllViews()
            var count = 0

            for (doc in snapshots.documents) {
                val uid = doc.getString("uid") ?: doc.id
                if (uid == currentUid) continue

                val username = doc.getString("username")
                    ?: doc.getString("email")?.substringBefore("@")
                    ?: "Përdorues"

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(10), dp(12), dp(10))
                }

                val avatar = TextView(this).apply {
                    text = username.take(1).uppercase()
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(waTealGreen)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                        setMargins(0, 0, dp(12), 0)
                    }
                }

                val name = TextView(this).apply {
                    text = username
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(primaryText)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }

                val audioCall = createCallButton(uid, username, false)
                val videoCall = createCallButton(uid, username, true)
                row.addView(avatar)
                row.addView(name)
                row.addView(audioCall, LinearLayout.LayoutParams(dp(44), dp(44)))
                row.addView(videoCall, LinearLayout.LayoutParams(dp(44), dp(44)))
                list.addView(row)

                val divider = View(this).apply {
                    setBackgroundColor(Color.argb(30, 128, 128, 128))
                    layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                        setMargins(dp(72), 0, dp(12), 0)
                    }
                }
                list.addView(divider)
                count++
            }

            if (count == 0) {
                val empty = TextView(this).apply {
                    text = "Nuk ka ende kontakte për telefonata."
                    textSize = 14f
                    setTextColor(secondaryText)
                    gravity = Gravity.CENTER
                    setPadding(dp(20), dp(50), dp(20), dp(20))
                }
                list.addView(empty)
            }
        }.addOnFailureListener {
            toast("Nuk u ngarkuan kontaktet: ${it.message}")
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
        isChatRoomOpen = true

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
                isChatRoomOpen = false
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

        val messageSearchBtn = TextView(this).apply {
            text = "⌕"
            textSize = 25f
            setTextColor(Color.WHITE)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener {
                val searchInput = EditText(this@MainActivity).apply { hint = "Kërko në këtë bisedë" }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Kërko mesazhe")
                    .setView(searchInput)
                    .setPositiveButton("KËRKO") { _, _ ->
                        val term = searchInput.text.toString().trim()
                        if (term.isNotEmpty()) {
                            db.collection("chats").document(chatId).collection("messages")
                                .get().addOnSuccessListener { result ->
                                    val matches = result.documents.filter {
                                        (it.getString("text") ?: "").contains(term, ignoreCase = true)
                                    }
                                    if (matches.isEmpty()) toast("Nuk u gjet asnjë mesazh.")
                                    else {
                                        val lines = matches.map { "• ${it.getString("text") ?: ""}" }.toTypedArray()
                                        AlertDialog.Builder(this@MainActivity).setTitle("Rezultatet (${matches.size})")
                                            .setItems(lines, null).setPositiveButton("MBYLL", null).show()
                                    }
                                }.addOnFailureListener { toast("Kërkimi dështoi: ${it.message}") }
                        }
                    }
                    .setNegativeButton("ANULO", null).show()
            }
        }
        topBar.addView(messageSearchBtn)

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

        // Shiriti i përgjigjes shfaqet kur përdoruesi zgjedh Përgjigju.
        val replyBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setBackgroundColor(cardBackground)
            visibility = View.GONE
        }
        val replyLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(primaryText)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val cancelReply = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(secondaryText)
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener {
                replyMessageId = null
                replyMessageText = null
                replyBar.visibility = View.GONE
            }
        }
        replyBar.addView(replyLabel)
        replyBar.addView(cancelReply)
        root.addView(replyBar)

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
                                this.text = if (doc.getBoolean("edited") == true) "$text  (i modifikuar)" else text
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

                    val quotedText = doc.getString("replyText")
                    if (!quotedText.isNullOrBlank()) {
                        val quote = TextView(this).apply {
    this.text = "↪ $quotedText"
    textSize = 12f
    setTextColor(secondaryText)
    setPadding(0, dp(3), 0, 0)
    maxLines = 2
}
bubble.addView(quote, 0) 
                    }
                    bubble.setOnLongClickListener {
                        val choices = if (isMine) arrayOf("Përgjigju", "Modifiko", "Fshi") else arrayOf("Përgjigju")
                        AlertDialog.Builder(this@MainActivity)
                            .setItems(choices) { _, which ->
                                val action = choices[which]
                                when (action) {
                                    "Përgjigju" -> {
                                        replyMessageId = doc.id
                                        replyMessageText = text.ifBlank { if (type == "image") "Foto" else "Mesazh zanor" }
                                        replyLabel.text = "Përgjigje: ${replyMessageText}"
                                        replyBar.visibility = View.VISIBLE
                                    }
                                    "Modifiko" -> {
                                        if (type != "text") {
                                            toast("Mund të modifikohen vetëm mesazhet tekst.")
                                        } else {
                                            val editInput = EditText(this@MainActivity).apply { setText(text) }
                                            AlertDialog.Builder(this@MainActivity).setTitle("Modifiko mesazhin")
                                                .setView(editInput)
                                                .setPositiveButton("RUAJ") { _, _ ->
                                                    val updated = editInput.text.toString().trim()
                                                    if (updated.isNotEmpty()) {
                                                        db.collection("chats").document(chatId).collection("messages")
                                                            .document(doc.id).update("text", updated, "edited", true)
                                                            .addOnSuccessListener { toast("Mesazhi u modifikua.") }
                                                            .addOnFailureListener { toast("Nuk u modifikua: ${it.message}") }
                                                    }
                                                }.setNegativeButton("ANULO", null).show()
                                        }
                                    }
                                    "Fshi" -> {
                                        AlertDialog.Builder(this@MainActivity).setMessage("Ta fshijmë këtë mesazh?")
                                            .setPositiveButton("FSHI") { _, _ ->
                                                db.collection("chats").document(chatId).collection("messages")
                                                    .document(doc.id).delete()
                                                    .addOnSuccessListener { toast("Mesazhi u fshi.") }
                                                    .addOnFailureListener { toast("Nuk u fshi: ${it.message}") }
                                            }.setNegativeButton("ANULO", null).show()
                                    }
                                }
                            }.show()
                        true
                    }

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
                sendTextMessage(chatId, msgText, replyMessageId, replyMessageText)
                replyMessageId = null
                replyMessageText = null
                replyBar.visibility = View.GONE
            } else {
                toggleVoiceRecording(chatId, actionBtn)
            }
        }

        bottomBar.addView(inputCard)
        bottomBar.addView(actionBtn)
        root.addView(bottomBar)

        setContentView(root)
    }

    private fun sendTextMessage(
        chatId: String,
        text: String,
        replyToId: String? = null,
        replyToText: String? = null
    ) {
        val currentUid = auth.currentUser?.uid ?: return
        val msgData = hashMapOf<String, Any>(
            "senderId" to currentUid,
            "text" to text,
            "type" to "text",
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (!replyToId.isNullOrBlank() && !replyToText.isNullOrBlank()) {
            msgData["replyToId"] = replyToId
            msgData["replyText"] = replyToText
        }
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

        val phoneStatus = TextView(this).apply {
            text = if (user?.phoneNumber.isNullOrBlank()) "Numri i telefonit: Nuk është lidhur" else "Numri i telefonit: ${user?.phoneNumber}"
            textSize = 14f
            setTextColor(secondaryText)
            setPadding(0, dp(10), 0, dp(12))
        }
        layout.addView(phoneStatus)

        val linkPhoneButton = Button(this).apply {
            text = if (user?.phoneNumber.isNullOrBlank()) "LIDH NUMRIN E TELEFONIT" else "PËRDITËSO NUMRIN E TELEFONIT"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(waTealGreen)
                cornerRadius = dp(24).toFloat()
            }
            setOnClickListener { startPhoneLinkFlow() }
        }
        layout.addView(linkPhoneButton, LinearLayout.LayoutParams(-1, dp(50)).apply {
            setMargins(0, dp(8), 0, dp(12))
        })

        val phoneInfo = TextView(this).apply {
            text = "Lidhja e numrit ruan të njëjtën llogari dhe ID. Mos krijo llogari të re."
            textSize = 12f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
        }
        layout.addView(phoneInfo)

        container.addView(layout)
    }

    // Lidh numrin me llogarinë ekzistuese. UID dhe bisedat nuk ndryshohen.
    private fun startPhoneLinkFlow() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            toast("Së pari futu me llogarinë tënde ekzistuese.")
            showLogin()
            return
        }
        if (currentUser.isAnonymous) {
            toast("Llogaritë vizitor nuk mund të lidhen me këtë proces. Së pari përdor llogarinë tënde email.")
            return
        }

        val phoneInput = EditText(this).apply {
            hint = "+3556XXXXXXXX"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Lidh numrin e telefonit")
            .setMessage("Shkruaj numrin me prefiksin e shtetit, p.sh. +355...")
            .setView(phoneInput)
            .setNegativeButton("ANULO", null)
            .setPositiveButton("DËRGO KODIN", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val phone = phoneInput.text.toString().trim().replace(" ", "")
                        if (!phone.matches(Regex("^\\+[1-9][0-9]{7,14}$"))) {
                            phoneInput.error = "Përdor formatin ndërkombëtar, p.sh. +355..."
                            return@setOnClickListener
                        }
                        dialog.dismiss()
                        requestPhoneVerification(phone)
                    }
                }
                dialog.show()
            }
    }

    private fun requestPhoneVerification(phoneNumber: String) {
        if (phoneVerificationInProgress) {
            toast("Verifikimi është në proces. Prit pak.")
            return
        }
        val currentUser = auth.currentUser ?: run {
            toast("Nuk u gjet llogaria aktive.")
            return
        }
        phoneVerificationInProgress = true
        toast("Po dërgohet kodi SMS...")

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                phoneVerificationInProgress = false
                linkPhoneCredential(credential, currentUser.uid)
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                phoneVerificationInProgress = false
                toast("Verifikimi dështoi: ${e.localizedMessage ?: "Kontrollo numrin dhe konfigurimin Firebase."}")
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                phoneVerificationInProgress = false
                showPhoneCodeDialog(verificationId, currentUser.uid)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun showPhoneCodeDialog(verificationId: String, expectedUid: String) {
        val codeInput = EditText(this).apply {
            hint = "Kodi 6-shifror SMS"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Verifiko numrin")
            .setMessage("Shkruaj kodin që të erdhi me SMS.")
            .setView(codeInput)
            .setNegativeButton("ANULO", null)
            .setPositiveButton("VERIFIKO", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val code = codeInput.text.toString().trim()
                        if (code.length != 6) {
                            codeInput.error = "Kodi duhet të ketë 6 shifra."
                            return@setOnClickListener
                        }
                        dialog.dismiss()
                        val credential = PhoneAuthProvider.getCredential(verificationId, code)
                        linkPhoneCredential(credential, expectedUid)
                    }
                }
                dialog.show()
            }
    }

    private fun linkPhoneCredential(credential: PhoneAuthCredential, expectedUid: String) {
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.uid != expectedUid) {
            toast("Llogaria ka ndryshuar. Hyr përsëri me emailin e vjetër.")
            return
        }
        currentUser.linkWithCredential(credential)
            .addOnSuccessListener { result ->
                val linkedUser = result.user ?: auth.currentUser
                if (linkedUser == null || linkedUser.uid != expectedUid) {
                    toast("Nuk u verifikua ID-ja e llogarisë. Të dhënat nuk u ndryshuan.")
                    return@addOnSuccessListener
                }
                val phone = linkedUser.phoneNumber
                val update = hashMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
                if (!phone.isNullOrBlank()) update["phoneNumber"] = phone
                db.collection("users").document(expectedUid)
                    .set(update, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        toast("Numri u lidh me sukses! Llogaria dhe bisedat mbetën të njëjta.")
                        showHome()
                    }
                    .addOnFailureListener { e ->
                        toast("Numri u lidh në Firebase Auth, por profili nuk u përditësua: ${e.localizedMessage}")
                    }
            }
            .addOnFailureListener { e ->
                val message = when (e) {
                    is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "Ky numër është i lidhur me një llogari tjetër. Nuk u krijua llogari e re dhe bisedat nuk u prekën."
                    is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Kodi SMS është i pavlefshëm ose ka skaduar."
                    else -> "Nuk u lidh numri: ${e.localizedMessage ?: "Gabim i panjohur"}"
                }
                toast(message)
            }
    }
}
