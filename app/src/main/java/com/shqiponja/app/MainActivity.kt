
package com.shqiponja.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
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
    private val gold = Color.rgb(220, 175, 65)

    private val zegoResourceId = "shqiponja_call"

    private var messagesListener: ListenerRegistration? = null
    private var chatsListener: ListenerRegistration? = null

    private var zegoInitialized = false
    private var zegoInitializing = false

    private var selectedHomeTab = "chats"

    private fun installCrashLogger() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = java.io.File(filesDir, "last_crash.txt")
                crashFile.writeText(
                    "CHAT SHQIPONJA - GABIM I REGJISTRUAR\n" +
                    "Koha: ${java.util.Date()}\n" +
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
            val crashFile = java.io.File(filesDir, "last_crash.txt")
            if (!crashFile.exists()) return

            val crashText = crashFile.readText()
            if (crashText.isBlank()) return

            val textView = TextView(this)
            textView.text = crashText
            textView.textSize = 13f
            textView.setTextIsSelectable(true)
            textView.setPadding(20, 15, 20, 15)

            val scrollView = ScrollView(this)
            scrollView.addView(textView)

            AlertDialog.Builder(this)
                .setTitle("U regjistrua një gabim")
                .setMessage(
                    "Aplikacioni u mbyll nga një gabim. " +
                    "Shtyp KOPJO dhe ma dërgo tekstin."
                )
                .setView(scrollView)
                .setPositiveButton("KOPJO") { _, _ ->
                    try {
                        val clipboard =
                            getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager

                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "CHAT SHQIPONJA CRASH",
                                crashText
                            )
                        )
                        toast("Gabimi u kopjua. Ma dërgo tekstin.")
                    } catch (e: Throwable) {
                        toast("Nuk u kopjua gabimi.")
                    }
                }
                .setNegativeButton("MBYLL", null)
                .show()
        } catch (e: Throwable) {
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
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(dp(22), dp(20), dp(22), dp(20))

        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(8, 8, 8),
                Color.rgb(45, 5, 10),
                Color.rgb(12, 12, 12)
            )
        )

        val scroll = ScrollView(this)
        scroll.isFillViewport = true

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER
        content.setPadding(dp(5), dp(15), dp(5), dp(15))

        val eagle = TextView(this)
        eagle.text = "🦅"
        eagle.textSize = 75f
        eagle.gravity = Gravity.CENTER
        content.addView(eagle)

        val title = TextView(this)
        title.text = "CHAT SHQIPONJA"
        title.textSize = 29f
        title.setTextColor(Color.rgb(235, 185, 65))
        title.typeface = Typeface.create(
            "sans-serif-black",
            Typeface.BOLD
        )
        title.gravity = Gravity.CENTER
        title.letterSpacing = 0.08f
        content.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "LIDHJE  •  KOMUNIKIM  •  BESIM"
        subtitle.textSize = 12f
        subtitle.setTextColor(Color.LTGRAY)
        subtitle.gravity = Gravity.CENTER
        subtitle.letterSpacing = 0.12f
        subtitle.setPadding(0, dp(10), 0, dp(25))
        content.addView(subtitle)

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(22), dp(25), dp(22), dp(22))

        val cardBackground = GradientDrawable()
        cardBackground.setColor(Color.rgb(29, 29, 29))
        cardBackground.cornerRadius = dp(22).toFloat()
        cardBackground.setStroke(dp(1), Color.rgb(125, 95, 45))
        card.background = cardBackground
        card.elevation = dp(8).toFloat()

        val welcome = TextView(this)
        welcome.text = "MIRË SE ERDHE!"
        welcome.textSize = 21f
        welcome.setTextColor(Color.rgb(235, 185, 65))
        welcome.typeface = Typeface.DEFAULT_BOLD
        welcome.gravity = Gravity.CENTER
        card.addView(welcome)

        val description = TextView(this)
        description.text = "Hyr në llogarinë tënde dhe lidhu me të gjithë."
        description.textSize = 14f
        description.setTextColor(Color.LTGRAY)
        description.gravity = Gravity.CENTER
        description.setPadding(0, dp(8), 0, dp(22))
        card.addView(description)

        fun makeInput(hintText: String, type: Int): EditText {
            val input = EditText(this)
            input.hint = hintText
            input.textSize = 15f
            input.setTextColor(Color.WHITE)
            input.setHintTextColor(Color.LTGRAY)
            input.inputType = type
            input.setSingleLine(true)
            input.setPadding(dp(16), 0, dp(16), 0)

            val inputBackground = GradientDrawable()
            inputBackground.setColor(Color.rgb(45, 45, 45))
            inputBackground.cornerRadius = dp(13).toFloat()
            inputBackground.setStroke(dp(1), Color.rgb(90, 75, 55))
            input.background = inputBackground

            val params = LinearLayout.LayoutParams(-1, dp(55))
            params.setMargins(0, dp(7), 0, dp(12))
            card.addView(input, params)

            return input
        }

        val emailInput = makeInput(
            "✉  Adresa e emailit",
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )

        val passwordInput = makeInput(
            "🔒  Fjalëkalimi",
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        )

        fun makeButton(
            text: String,
            color: Int,
            textColor: Int
        ): Button {
            val button = Button(this)
            button.text = text
            button.textSize = 14f
            button.setTextColor(textColor)
            button.typeface = Typeface.DEFAULT_BOLD
            button.isAllCaps = false

            val buttonBackground = GradientDrawable()
            buttonBackground.setColor(color)
            buttonBackground.cornerRadius = dp(14).toFloat()
            button.background = buttonBackground
            button.elevation = dp(3).toFloat()

            val params = LinearLayout.LayoutParams(-1, dp(53))
            params.setMargins(0, dp(7), 0, dp(7))
            card.addView(button, params)

            return button
        }

        val loginButton = makeButton(
            "HYR NË LLOGARI",
            Color.rgb(190, 20, 35),
            Color.WHITE
        )

        val registerButton = makeButton(
            "KRIJO LLOGARI TË RE",
            Color.rgb(205, 165, 65),
            Color.BLACK
        )

        val guestButton = makeButton(
            "HYR SI VIZITOR",
            Color.rgb(65, 65, 65),
            Color.WHITE
        )

        content.addView(card, LinearLayout.LayoutParams(-1, -2))

        val footer = TextView(this)
        footer.text = "🦅  SHQIPONJA  •  KOMUNIKO PA KUFIJ"
        footer.textSize = 11f
        footer.setTextColor(Color.rgb(205, 165, 65))
        footer.gravity = Gravity.CENTER
        footer.setPadding(0, dp(25), 0, dp(10))
        footer.letterSpacing = 0.05f
        content.addView(footer)

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        setContentView(root)

        registerButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (email.isEmpty() || password.length < 6) {
                toast("Plotëso emailin dhe fjalëkalimin (minimumi 6 karaktere)")
                return@setOnClickListener
            }

            registerButton.isEnabled = false

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    registerButton.isEnabled = true

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

            loginButton.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    loginButton.isEnabled = true

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

    // EKRANI KRYESOR ME KATEGORITE POSHTE
    private fun showHome(tab: String = selectedHomeTab) {
        selectedHomeTab = tab

        messagesListener?.remove()
        messagesListener = null

        chatsListener?.remove()
        chatsListener = null

        val uid = auth.currentUser?.uid

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(black)

        // KREU I APLIKACIONIT
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(16), dp(8), dp(12), dp(8))
        header.setBackgroundColor(red)

        val title = TextView(this)
        title.text = "CHAT SHQIPONJA"
        title.textSize = 22f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(white)
        title.gravity = Gravity.CENTER_VERTICAL

        header.addView(
            title,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        // BUTONI ME TRE PIKA
        val settingsButton = TextView(this)
        settingsButton.text = "⋮"
        settingsButton.textSize = 34f
        settingsButton.typeface = Typeface.DEFAULT_BOLD
        settingsButton.setTextColor(white)
        settingsButton.gravity = Gravity.CENTER
        settingsButton.setPadding(dp(14), 0, dp(8), 0)
        settingsButton.minimumWidth = dp(45)
        settingsButton.minimumHeight = dp(45)

        settingsButton.setOnClickListener {
            showMainMenu(settingsButton)
        }

        header.addView(settingsButton)

        // HAPESIRA E KATEGORISE
        val content = FrameLayout(this)
        content.setBackgroundColor(black)

        // NAVIGIMI POSHTE
        val bottomNav = LinearLayout(this)
        bottomNav.orientation = LinearLayout.HORIZONTAL
        bottomNav.gravity = Gravity.CENTER
        bottomNav.setBackgroundColor(Color.rgb(30, 30, 30))
        bottomNav.setPadding(dp(5), dp(7), dp(5), dp(7))

        fun makeTab(
            icon: String,
            label: String,
            tabName: String
        ): LinearLayout {
            val selected = selectedHomeTab == tabName

            val tab = LinearLayout(this)
            tab.orientation = LinearLayout.VERTICAL
            tab.gravity = Gravity.CENTER
            tab.setPadding(dp(10), dp(7), dp(10), dp(7))

            val tabBackground = GradientDrawable()
            tabBackground.cornerRadius = dp(18).toFloat()
            tabBackground.setColor(
                if (selected) Color.rgb(75, 25, 30)
                else Color.TRANSPARENT
            )
            tab.background = tabBackground

            val iconView = TextView(this)
            iconView.text = icon
            iconView.textSize = 21f
            iconView.gravity = Gravity.CENTER
            iconView.setTextColor(
                if (selected) gold else Color.LTGRAY
            )

            val labelView = TextView(this)
            labelView.text = label
            labelView.textSize = 13f
            labelView.typeface = Typeface.DEFAULT_BOLD
            labelView.gravity = Gravity.CENTER
            labelView.setTextColor(
                if (selected) gold else Color.LTGRAY
            )

            tab.addView(iconView)
            tab.addView(labelView)

            tab.setOnClickListener {
                if (selectedHomeTab != tabName) {
                    showHome(tabName)
                }
            }

            return tab
        }

        bottomNav.addView(
            makeTab("💬", "Chats", "chats"),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        bottomNav.addView(
            makeTab("📞", "Calls", "calls"),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        layout.addView(header)

        layout.addView(
            content,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        layout.addView(bottomNav)

        setContentView(layout)

        if (selectedHomeTab == "calls") {
            showCallsPage(content)
            return
        }

        showChatsPage(content, uid)
    }

    // MENUJA ME TRE PIKA
    private fun showMainMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)

        popup.menu.add("Cilësimet")
        popup.menu.add("Profili im")
        popup.menu.add("Privatësia")
        popup.menu.add("Dil nga llogaria")

        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Cilësimet" -> {
                    showSettingsDialog()
                    true
                }

                "Profili im" -> {
                    showProfileDialog()
                    true
                }

                "Privatësia" -> {
                    showPrivacyDialog()
                    true
                }

                "Dil nga llogaria" -> {
                    confirmLogout()
                    true
                }

                else -> false
            }
        }

        popup.show()
    }

    // FAQJA E CILETIMEVE
    private fun showSettingsDialog() {
        val email = auth.currentUser?.email ?: "Vizitor"

        val message =
            "🦅 CHAT SHQIPONJA\n\n" +
            "Llogaria: $email\n\n" +
            "Versioni: 1.0\n\n" +
            "Cilësimet e tjera të aplikacionit " +
            "do të shtohen në këtë seksion."

        AlertDialog.Builder(this)
            .setTitle("⚙️ Cilësimet")
            .setMessage(message)
            .setPositiveButton("MBYLL", null)
            .setNeutralButton("PROFILI IM") { _, _ ->
                showProfileDialog()
            }
            .show()
    }

    // PRIVATESIA
    private fun showPrivacyDialog() {
        val message =
            "PRIVATËSIA E LLOGARISË\n\n" +
            "Bisedat dhe të dhënat e përdoruesit " +
            "ruhen përmes Firebase.\n\n" +
            "Mos i ndani fjalëkalimet ose të dhënat " +
            "tuaja personale me persona të tjerë."

        AlertDialog.Builder(this)
            .setTitle("🔒 Privatësia")
            .setMessage(message)
            .setPositiveButton("E KUPTOVA", null)
            .show()
    }

    // KATEGORIA CHATS
    private fun showChatsPage(
        content: FrameLayout,
        uid: String?
    ) {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(black)

        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER
        actions.setPadding(dp(8), dp(5), dp(8), dp(5))

        val newChatButton = Button(this)
        newChatButton.text = "＋ BISEDË E RE"
        newChatButton.isAllCaps = false
        newChatButton.setOnClickListener { askUsername() }

        val searchButton = Button(this)
        searchButton.text = "🔎 KËRKO"
        searchButton.isAllCaps = false
        searchButton.setOnClickListener { askUsername() }

        actions.addView(
            newChatButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        actions.addView(
            searchButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val subtitle = TextView(this)
        subtitle.text = "BISEDAT E MIA"
        subtitle.textSize = 17f
        subtitle.typeface = Typeface.DEFAULT_BOLD
        subtitle.setTextColor(white)
        subtitle.setPadding(dp(16), dp(12), dp(10), dp(8))

        val scroll = ScrollView(this)

        val chatList = LinearLayout(this)
        chatList.orientation = LinearLayout.VERTICAL
        chatList.setPadding(dp(10), dp(5), dp(10), dp(10))

        scroll.addView(chatList)

        page.addView(actions)
        page.addView(subtitle)
        page.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        content.addView(
            page,
            FrameLayout.LayoutParams(-1, -1)
        )

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
                                    row.orientation = LinearLayout.HORIZONTAL
                                    row.gravity = Gravity.CENTER_VERTICAL
                                    row.setPadding(
                                        dp(12),
                                        dp(12),
                                        dp(12),
                                        dp(12)
                                    )
                                    row.setBackgroundColor(
                                        Color.rgb(38, 38, 38)
                                    )

                                    val avatar = TextView(this)
                                    avatar.text = "🦅"
                                    avatar.textSize = 27f
                                    avatar.gravity = Gravity.CENTER

                                    val avatarBackground =
                                        GradientDrawable()

                                    avatarBackground.shape =
                                        GradientDrawable.OVAL

                                    avatarBackground.setColor(
                                        Color.rgb(110, 15, 25)
                                    )

                                    avatarBackground.setStroke(
                                        dp(2),
                                        gold
                                    )

                                    avatar.background = avatarBackground

                                    val avatarParams =
                                        LinearLayout.LayoutParams(
                                            dp(56),
                                            dp(56)
                                        )

                                    avatarParams.setMargins(
                                        0,
                                        0,
                                        dp(14),
                                        0
                                    )

                                    row.addView(avatar, avatarParams)

                                    val textLayout = LinearLayout(this)
                                    textLayout.orientation =
                                        LinearLayout.VERTICAL

                                    val name = TextView(this)
                                    name.text = "@$username"
                                    name.textSize = 18f
                                    name.typeface = Typeface.DEFAULT_BOLD
                                    name.setTextColor(white)
                                    name.maxLines = 1

                                    val preview = TextView(this)
                                    preview.text = lastMessage
                                    preview.textSize = 14f
                                    preview.setTextColor(Color.LTGRAY)
                                    preview.maxLines = 1
                                    preview.ellipsize =
                                        android.text.TextUtils.TruncateAt.END
                                    preview.setPadding(0, dp(5), 0, 0)

                                    textLayout.addView(name)
                                    textLayout.addView(preview)

                                    row.addView(
                                        textLayout,
                                        LinearLayout.LayoutParams(
                                            0,
                                            -2,
                                            1f
                                        )
                                    )

                                    val params =
                                        LinearLayout.LayoutParams(-1, -2)

                                    params.setMargins(
                                        dp(3),
                                        dp(5),
                                        dp(3),
                                        dp(5)
                                    )

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

    // KATEGORIA CALLS
    private fun showCallsPage(content: FrameLayout) {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.gravity = Gravity.CENTER
        page.setPadding(dp(25), dp(20), dp(25), dp(20))
        page.setBackgroundColor(black)

        val icon = TextView(this)
        icon.text = "📞"
        icon.textSize = 65f
        icon.gravity = Gravity.CENTER
        page.addView(icon)

        val title = TextView(this)
        title.text = "Telefonatat"
        title.textSize = 24f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(white)
        title.gravity = Gravity.CENTER
        title.setPadding(0, dp(12), 0, dp(8))
        page.addView(title)

        val description = TextView(this)
        description.text =
            "Këtu do të shfaqet historiku i telefonatave.\n\n" +
            "Për të telefonuar një kontakt, hap kategorinë Chats " +
            "dhe zgjidh personin që dëshiron të telefonosh."
        description.textSize = 15f
        description.setTextColor(Color.LTGRAY)
        description.gravity = Gravity.CENTER
        description.setPadding(0, dp(8), 0, dp(20))
        page.addView(description)

        val chatsButton = Button(this)
        chatsButton.text = "HAP CHATS"
        chatsButton.isAllCaps = false
        chatsButton.setOnClickListener {
            showHome("chats")
        }
        page.addView(
            chatsButton,
            LinearLayout.LayoutParams(-1, dp(52))
        )

        content.addView(
            page,
            FrameLayout.LayoutParams(-1, -1)
        )
    }

    private fun showProfileDialog() {
        val email = auth.currentUser?.email ?: "Vizitor"

        AlertDialog.Builder(this)
            .setTitle("Profili im 🦅")
            .setMessage("CHAT SHQIPONJA\n\nEmail: $email")
            .setPositiveButton("MBYLL", null)
            .setNeutralButton("DIL NGA LLOGARIA") { _, _ ->
                confirmLogout()
            }
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Dil nga llogaria")
            .setMessage("A dëshiron të dalësh nga llogaria?")
            .setPositiveButton("PO, DIL") { _, _ ->
                logout()
            }
            .setNegativeButton("ANULO", null)
            .show()
    }

    private fun logout() {
        chatsListener?.remove()
        messagesListener?.remove()

        chatsListener = null
        messagesListener = null

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
        header.setOnClickListener { showHome("chats") }

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

        val voiceParams = LinearLayout.LayoutParams(0, 58, 1f)
        voiceParams.setMargins(5, 0, 8, 0)

        val videoParams = LinearLayout.LayoutParams(0, 58, 1f)
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
