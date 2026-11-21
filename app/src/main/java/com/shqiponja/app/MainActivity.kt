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
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.BitmapFactory
import android.net.Uri
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig
import com.zegocloud.uikit.prebuilt.call.invite.widget.ZegoSendCallInvitationButton
import com.zegocloud.uikit.service.defines.ZegoUIKitUser
import java.io.File
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val storage by lazy { FirebaseStorage.getInstance() }

    // TEMA E APLIKACIONIT
    private val isLightTheme: Boolean
        get() = getSharedPreferences("shqiponja_settings", MODE_PRIVATE)
            .getString("theme", "dark") == "light"

    private val red = Color.rgb(190, 20, 35)

    private val black: Int
        get() = if (isLightTheme) {
            Color.rgb(245, 245, 248)
        } else {
            Color.rgb(20, 20, 20)
        }

    private val white: Int
        get() = if (isLightTheme) {
            Color.rgb(28, 28, 32)
        } else {
            Color.WHITE
        }

    private val green = Color.rgb(20, 155, 85)
    private val gold = Color.rgb(220, 175, 65)

    private val pageBackground: Int
        get() = if (isLightTheme) {
            Color.rgb(245, 245, 248)
        } else {
            Color.rgb(15, 15, 18)
        }

    private val cardBackground: Int
        get() = if (isLightTheme) {
            Color.WHITE
        } else {
            Color.rgb(35, 35, 40)
        }

    private val secondaryText: Int
        get() = if (isLightTheme) {
            Color.rgb(90, 90, 95)
        } else {
            Color.LTGRAY
        }

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

    private fun installCrashLogger() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = File(filesDir, "last_crash.txt")
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
            val crashFile = File(filesDir, "last_crash.txt")
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
                .setMessage("Aplikacioni u mbyll nga një gabim. Shtyp KOPJO dhe ma dërgo tekstin.")
                .setView(scrollView)
                .setPositiveButton("KOPJO") { _, _ ->
                    try {
                        val clipboard =
                            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("CHAT SHQIPONJA CRASH", crashText)
                        )
                        toast("Gabimi u kopjua. Ma dërgo tekstin.")
                    } catch (_: Throwable) {
                        toast("Nuk u kopjua gabimi.")
                    }
                }
                .setNegativeButton("MBYLL", null)
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
            listOf(
                ZegoUIKitUser(
                    toZegoUserId(otherUserId),
                    otherUsername
                )
            )
        )

        button.text = if (video) "📹 VIDEO" else "📞 TELEFONO"
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
        title.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
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
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )

        val passwordInput = makeInput(
            "🔒  Fjalëkalimi",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )

        fun makeButton(text: String, color: Int, textColor: Int): Button {
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

        val loginButton = makeButton("HYR NË LLOGARI", Color.rgb(190, 20, 35), Color.WHITE)
        val registerButton = makeButton("KRIJO LLOGARI TË RE", Color.rgb(205, 165, 65), Color.BLACK)
        val guestButton = makeButton("HYR SI VIZITOR", Color.rgb(65, 65, 65), Color.WHITE)

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
        layout.setBackgroundColor(Color.WHITE)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(16), dp(8), dp(12), dp(8))
        header.setBackgroundColor(red)

        val title = TextView(this)
        title.text = "CHAT SHQIPONJA"
        title.textSize = 22f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER_VERTICAL
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

        val settingsButton = TextView(this)
        settingsButton.text = "⋮"
        settingsButton.textSize = 34f
        settingsButton.typeface = Typeface.DEFAULT_BOLD
        settingsButton.setTextColor(Color.WHITE)
        settingsButton.gravity = Gravity.CENTER
        settingsButton.setPadding(dp(14), 0, dp(8), 0)
        settingsButton.minimumWidth = dp(45)
        settingsButton.minimumHeight = dp(45)
        settingsButton.setOnClickListener { showMainMenu(settingsButton) }
        header.addView(settingsButton)

        val content = FrameLayout(this)

        val bottomNav = LinearLayout(this)
        bottomNav.orientation = LinearLayout.HORIZONTAL
        bottomNav.gravity = Gravity.CENTER
        bottomNav.setBackgroundColor(Color.WHITE)
        bottomNav.setPadding(dp(5), dp(7), dp(5), dp(7))

        fun makeTab(icon: String, label: String, tabName: String): LinearLayout {
            val selected = selectedHomeTab == tabName
            val tab = LinearLayout(this)
            tab.orientation = LinearLayout.VERTICAL
            tab.gravity = Gravity.CENTER
            tab.setPadding(dp(10), dp(7), dp(10), dp(7))

            val tabBackground = GradientDrawable()
            tabBackground.cornerRadius = dp(18).toFloat()
            tabBackground.setColor(
                if (selected) {
                    if (isLightTheme) Color.rgb(255, 235, 235)
                    else Color.rgb(75, 25, 30)
                } else {
                    Color.TRANSPARENT
                }
            )
            tab.background = tabBackground

            val iconView = TextView(this)
            iconView.text = icon
            iconView.textSize = 21f
            iconView.gravity = Gravity.CENTER
            iconView.setTextColor(if (selected) gold else secondaryText)

            val labelView = TextView(this)
            labelView.text = label
            labelView.textSize = 13f
            labelView.typeface = Typeface.DEFAULT_BOLD
            labelView.gravity = Gravity.CENTER
            labelView.setTextColor(if (selected) gold else secondaryText)

            tab.addView(iconView)
            tab.addView(labelView)
            tab.setOnClickListener {
                if (selectedHomeTab != tabName) showHome(tabName)
            }
            return tab
        }

        bottomNav.addView(makeTab("💬", "Chats", "chats"), LinearLayout.LayoutParams(0, -2, 1f))
        bottomNav.addView(makeTab("📞", "Calls", "calls"), LinearLayout.LayoutParams(0, -2, 1f))

        layout.addView(header)
        layout.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(bottomNav)
        setContentView(layout)

        if (selectedHomeTab == "calls") {
            showCallsPage(content)
            return
        }

        showChatsPage(content, uid)
    }

    private fun showMainMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Cilësimet")
        popup.menu.add("Profili im")
        popup.menu.add("Privatësia")
        popup.menu.add("Dil nga llogaria")

        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Cilësimet" -> {
                    showSettingsPage()
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

    // FAQJA E CILËSIMEVE
    private fun showSettingsPage() {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val email = auth.currentUser?.email ?: "Vizitor"

        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Color.WHITE)
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(12), dp(10), dp(12), dp(10))
        header.setBackgroundColor(Color.rgb(160, 15, 30))

        val back = TextView(this)
        back.text = "‹"
        back.textSize = 38f
        back.setTextColor(Color.WHITE)
        back.gravity = Gravity.CENTER
        header.addView(back, LinearLayout.LayoutParams(dp(45), dp(50)))

        // Kthehu te skeda ku ndodheshe më parë
        back.setOnClickListener {
            showHome(selectedHomeTab)
        }

        val heading = TextView(this)
        heading.text = "CILËSIMET"
        heading.textSize = 21f
        heading.typeface = Typeface.DEFAULT_BOLD
        heading.setTextColor(Color.WHITE)
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))

        val scroll = ScrollView(this)
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(14), dp(15), dp(14), dp(25))

        fun section(text: String) {
            val label = TextView(this)
            label.text = text
            label.textSize = 13f
            label.typeface = Typeface.DEFAULT_BOLD
            label.setTextColor(Color.rgb(225, 175, 65))
            label.setPadding(dp(5), dp(17), dp(5), dp(8))
            list.addView(label)
        }

        fun makeCard(): LinearLayout {
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(12), dp(5), dp(12), dp(5))

            val bg = GradientDrawable()
            bg.setColor(cardBackground)
            bg.cornerRadius = dp(16).toFloat()
            bg.setStroke(
                dp(1),
                if (isLightTheme) Color.rgb(220, 220, 225)
                else Color.rgb(75, 65, 60)
            )
            card.background = bg

            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, 0, 0, dp(5))
            list.addView(card, params)
            return card
        }

        fun addRow(
            card: LinearLayout,
            icon: String,
            title: String,
            description: String,
            action: () -> Unit
        ) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(7), dp(13), dp(7), dp(13))
            row.isClickable = true
            row.isFocusable = true

            val iconView = TextView(this)
            iconView.text = icon
            iconView.textSize = 24f
            iconView.gravity = Gravity.CENTER
            row.addView(iconView, LinearLayout.LayoutParams(dp(43), dp(45)))

            val texts = LinearLayout(this)
            texts.orientation = LinearLayout.VERTICAL
            texts.setPadding(dp(8), 0, dp(5), 0)

            val titleView = TextView(this)
            titleView.text = title
            titleView.textSize = 15f
            titleView.typeface = Typeface.DEFAULT_BOLD
            titleView.setTextColor(white)

            val descView = TextView(this)
            descView.text = description
            descView.textSize = 12f
            descView.setTextColor(secondaryText)
            descView.setPadding(0, dp(4), 0, 0)

            texts.addView(titleView)
            texts.addView(descView)
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

            val arrow = TextView(this)
            arrow.text = "›"
            arrow.textSize = 28f
            arrow.setTextColor(secondaryText)
            row.addView(arrow)

            row.setOnClickListener { action() }
            card.addView(row)

            val divider = View(this)
            divider.setBackgroundColor(
                if (isLightTheme) Color.rgb(225, 225, 230)
                else Color.rgb(65, 65, 70)
            )
            card.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        }

        section("LLOGARIA")

        val accountCard = makeCard()

        val accountRow = LinearLayout(this)
        accountRow.orientation = LinearLayout.HORIZONTAL
        accountRow.gravity = Gravity.CENTER_VERTICAL
        accountRow.setPadding(dp(8), dp(14), dp(8), dp(14))

        val eagle = TextView(this)
        eagle.text = "🦅"
        eagle.textSize = 34f
        eagle.gravity = Gravity.CENTER

        val eagleBg = GradientDrawable()
        eagleBg.shape = GradientDrawable.OVAL
        eagleBg.setColor(Color.rgb(100, 12, 25))
        eagleBg.setStroke(dp(2), gold)
        eagle.background = eagleBg

        accountRow.addView(eagle, LinearLayout.LayoutParams(dp(55), dp(55)))

        val accountInfo = LinearLayout(this)
        accountInfo.orientation = LinearLayout.VERTICAL
        accountInfo.setPadding(dp(12), 0, 0, 0)

        val accountName = TextView(this)
        accountName.text = "CHAT SHQIPONJA"
        accountName.textSize = 16f
        accountName.typeface = Typeface.DEFAULT_BOLD
        accountName.setTextColor(white)

        val accountEmail = TextView(this)
        accountEmail.text = email
        accountEmail.textSize = 12f
        accountEmail.setTextColor(secondaryText)
        accountEmail.setPadding(0, dp(5), 0, 0)

        accountInfo.addView(accountName)
        accountInfo.addView(accountEmail)
        accountRow.addView(accountInfo)

        accountCard.addView(accountRow)

        addRow(
            accountCard,
            "👤",
            "Profili im",
            "Shiko informacionet e llogarisë"
        ) { showProfileDialog() }

        section("PREFERENCAT")

        val preferencesCard = makeCard()

        addRow(
            preferencesCard,
            "🔔",
            "Njoftimet",
            "Preferenca e njoftimeve"
        ) { showNotificationSettings() }

        addRow(
            preferencesCard,
            "🎨",
            "Pamja dhe tema",
            "Zgjidh preferencën e pamjes"
        ) { showThemeSettings() }

        addRow(
            preferencesCard,
            "🌍",
            "Gjuha",
            "Gjuha aktuale: Shqip"
        ) { showLanguageSettings() }

        section("SIGURIA DHE TË DHËNAT")

        val securityCard = makeCard()

        addRow(
            securityCard,
            "🔒",
            "Privatësia dhe siguria",
            "Informacion mbi privatësinë"
        ) { showPrivacyDialog() }

        addRow(
            securityCard,
            "💾",
            "Ruajtja dhe të dhënat",
            "Informacion mbi ruajtjen e të dhënave"
        ) { showDataSettings() }

        section("RRETH APLIKACIONIT")

        val aboutCard = makeCard()

        addRow(
            aboutCard,
            "🦅",
            "Rreth CHAT SHQIPONJA",
            "Informacion dhe versioni i aplikacionit"
        ) { showAboutSettings() }

        val logout = TextView(this)
        logout.text = "↪   DIL NGA LLOGARIA"
        logout.textSize = 15f
        logout.typeface = Typeface.DEFAULT_BOLD
        logout.gravity = Gravity.CENTER
        logout.setTextColor(Color.WHITE)
        logout.setPadding(dp(12), dp(16), dp(12), dp(16))

        val logoutBg = GradientDrawable()
        logoutBg.setColor(Color.rgb(170, 15, 30))
        logoutBg.cornerRadius = dp(14).toFloat()
        logout.background = logoutBg

        val logoutParams = LinearLayout.LayoutParams(-1, dp(53))
        logoutParams.setMargins(0, dp(20), 0, dp(10))
        list.addView(logout, logoutParams)

        logout.setOnClickListener { confirmLogout() }

        val footer = TextView(this)
        footer.text = "🦅 CHAT SHQIPONJA  •  VERSIONI 1.0"
        footer.textSize = 11f
        footer.gravity = Gravity.CENTER
        footer.setTextColor(Color.rgb(205, 165, 65))
        footer.setPadding(0, dp(15), 0, dp(15))
        list.addView(footer)

        scroll.addView(list)
        page.addView(header)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(page)
    }

    private fun showNotificationSettings() {
        val prefs = getSharedPreferences("shqiponja_settings", MODE_PRIVATE)
        val enabled = prefs.getBoolean("notifications_enabled", true)

        val check = CheckBox(this)
        check.text = "Aktivizo njoftimet"
        check.textSize = 16f
        check.setTextColor(white)
        check.isChecked = enabled
        check.setPadding(15, 15, 15, 15)

        AlertDialog.Builder(this)
            .setTitle("🔔 Njoftimet")
            .setView(check)
            .setPositiveButton("RUAJ") { _, _ ->
                prefs.edit()
                    .putBoolean("notifications_enabled", check.isChecked)
                    .apply()

                toast("Preferenca e njoftimeve u ruajt.")
                showSettingsPage()
            }
            .setNegativeButton("ANULO", null)
            .show()
    }

    private fun showThemeSettings() {
        val prefs = getSharedPreferences("shqiponja_settings", MODE_PRIVATE)
        val current = prefs.getString("theme", "dark") ?: "dark"

        val options = arrayOf("E errët", "E çelët")
        val selected = if (current == "light") 1 else 0

        AlertDialog.Builder(this)
            .setTitle("🎨 Pamja dhe tema")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val theme = if (which == 0) "dark" else "light"

                prefs.edit()
                    .putString("theme", theme)
                    .apply()

                dialog.dismiss()
                toast("Tema u ndryshua.")
                showSettingsPage()
            }
            .setNegativeButton("MBYLL", null)
            .show()
    }

    // Për momentin aplikacioni është në shqip.
    // Gjuhët e tjera nuk shfaqen si të aktivizuara pa përkthimin e plotë.
    private fun showLanguageSettings() {
        AlertDialog.Builder(this)
            .setTitle("🌍 Gjuha")
            .setMessage(
                "Gjuha aktuale e aplikacionit është Shqip.\n\n" +
                "Gjuhët e tjera do të aktivizohen kur të përfundojë " +
                "përkthimi i të gjithë aplikacionit."
            )
            .setPositiveButton("NË RREGULL", null)
            .show()
    }

    private fun showDataSettings() {
        val cacheSize = try {
            val size = cacheDir.walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }

            String.format(
                java.util.Locale.getDefault(),
                "%.2f MB",
                size / (1024.0 * 1024.0)
            )
        } catch (_: Exception) {
            "Nuk u llogarit"
        }

        AlertDialog.Builder(this)
            .setTitle("💾 Ruajtja dhe të dhënat")
            .setMessage(
                "CHAT SHQIPONJA\n\n" +
                "Hapësira e cache: $cacheSize\n\n" +
                "Bisedat dhe mediat ruhen përmes shërbimeve Firebase.\n\n" +
                "Mos fshi të dhënat e aplikacionit nëse nuk dëshiron të dalësh nga llogaria."
            )
            .setPositiveButton("E KUPTOVA", null)
            .show()
    }

    private fun showAboutSettings() {
        AlertDialog.Builder(this)
            .setTitle("🦅 Rreth CHAT SHQIPONJA")
            .setMessage(
                "CHAT SHQIPONJA\n\n" +
                "Versioni: 1.0\n\n" +
                "LIDHJE • KOMUNIKIM • BESIM\n\n" +
                "Një aplikacion për komunikim, biseda, foto, video dhe telefonata."
            )
            .setPositiveButton("MBYLL", null)
            .show()
    }

    private fun showPrivacyDialog() {
        val message =
            "PRIVATËSIA E LLOGARISË\n\n" +
            "Bisedat dhe të dhënat e përdoruesit ruhen përmes Firebase.\n\n" +
            "Mos i ndani fjalëkalimet ose të dhënat tuaja personale me persona të tjerë."

        AlertDialog.Builder(this)
            .setTitle("🔒 Privatësia")
            .setMessage(message)
            .setPositiveButton("E KUPTOVA", null)
            .show()
    }

    // FAQJA E BISEDAVE
    private fun showChatsPage(content: FrameLayout, uid: String?) {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val page = FrameLayout(this)
        page.setBackgroundColor(black)

        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setBackgroundColor(Color.WHITE)

        val subtitle = TextView(this)
        subtitle.text = "BISEDAT E MIA"
        subtitle.textSize = 17f
        subtitle.typeface = Typeface.DEFAULT_BOLD
        subtitle.setTextColor(white)
        subtitle.gravity = Gravity.CENTER_VERTICAL
        subtitle.setPadding(dp(16), dp(12), dp(10), dp(12))

        mainLayout.addView(subtitle, LinearLayout.LayoutParams(-1, dp(48)))

        val scroll = ScrollView(this)
        val chatList = LinearLayout(this)
        chatList.orientation = LinearLayout.VERTICAL
        chatList.setPadding(dp(10), dp(5), dp(10), dp(80))
        scroll.addView(chatList)

        mainLayout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(mainLayout, FrameLayout.LayoutParams(-1, -1))

        val plusButton = TextView(this)
        plusButton.text = "+"
        plusButton.textSize = 31f
        plusButton.typeface = Typeface.DEFAULT_BOLD
        plusButton.setTextColor(Color.BLACK)
        plusButton.gravity = Gravity.CENTER
        plusButton.includeFontPadding = false

        val plusBackground = GradientDrawable()
        plusBackground.shape = GradientDrawable.OVAL
        plusBackground.setColor(Color.rgb(32, 184, 90))
        plusButton.background = plusBackground
        plusButton.elevation = dp(8).toFloat()

        plusButton.setOnClickListener {
            askUsername()
        }

        val plusParams = FrameLayout.LayoutParams(
            dp(52), dp(52), Gravity.BOTTOM or Gravity.END
        )
        plusParams.setMargins(0, 0, dp(18), dp(20))
        page.addView(plusButton, plusParams)

        content.addView(page, FrameLayout.LayoutParams(-1, -1))

        if (uid == null) {
            chatList.addView(makeLabel("Hyr në llogari për të përdorur bisedat."))
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

                val visibleDocuments = documents.filter { document ->
                    val hiddenFor = document.get("hiddenFor") as? List<*>
                        ?: emptyList<Any>()
                    !hiddenFor.contains(uid)
                }

                if (visibleDocuments.isEmpty()) {
                    chatList.addView(
                        makeLabel("Nuk ke ende biseda.\n\nShtyp + për të kërkuar kontakte! 🦅")
                    )
                    return@addSnapshotListener
                }

                visibleDocuments.forEach { document ->
                    val participants = document.get("participants") as? List<*>
                        ?: emptyList<Any>()

                    val otherUid = participants
                        .filterIsInstance<String>()
                        .firstOrNull { it != uid } ?: return@forEach

                    db.collection("users").document(otherUid).get()
                        .addOnSuccessListener { userDocument ->

                            val username = userDocument.getString("username") ?: "Përdorues"

                            db.collection("chats")
                                .document(document.id)
                                .collection("messages")
                                .orderBy("createdAt", Query.Direction.DESCENDING)
                                .get()
                                .addOnSuccessListener { messages ->

                                    val lastDocument = messages.documents.firstOrNull { msg ->
                                        val hiddenFor = msg.get("hiddenFor") as? List<*>
                                            ?: emptyList<Any>()
                                        !hiddenFor.contains(uid)
                                    }

                                    val lastType = lastDocument?.getString("type") ?: "text"

private fun showChatsPage(content: FrameLayout, uid: String?) {
    val density = resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    val page = FrameLayout(this)
    page.setBackgroundColor(Color.WHITE)

    val mainLayout = LinearLayout(this)
    mainLayout.orientation = LinearLayout.VERTICAL
    mainLayout.setBackgroundColor(Color.WHITE)

    val subtitle = TextView(this)
    subtitle.text = "BISEDAT E MIA"
    subtitle.textSize = 17f
    subtitle.typeface = Typeface.DEFAULT_BOLD
    subtitle.setTextColor(Color.BLACK)
    subtitle.gravity = Gravity.CENTER_VERTICAL
    subtitle.setPadding(dp(16), dp(12), dp(10), dp(12))

    mainLayout.addView(subtitle, LinearLayout.LayoutParams(-1, dp(48)))

    val scroll = ScrollView(this)
    val chatList = LinearLayout(this)
    chatList.orientation = LinearLayout.VERTICAL
    chatList.setPadding(dp(10), dp(5), dp(10), dp(80))
    scroll.addView(chatList)

    mainLayout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    page.addView(mainLayout, FrameLayout.LayoutParams(-1, -1))

    val plusButton = TextView(this)
    plusButton.text = "+"
    plusButton.textSize = 31f
    plusButton.typeface = Typeface.DEFAULT_BOLD
    plusButton.setTextColor(Color.WHITE)
    plusButton.gravity = Gravity.CENTER
    plusButton.includeFontPadding = false

    val plusBackground = GradientDrawable()
    plusBackground.shape = GradientDrawable.OVAL
    plusBackground.setColor(red)
    plusButton.background = plusBackground
    plusButton.elevation = dp(8).toFloat()

    plusButton.setOnClickListener {
        askUsername()
    }

    val plusParams = FrameLayout.LayoutParams(
        dp(52), dp(52), Gravity.BOTTOM or Gravity.END
    )
    plusParams.setMargins(0, 0, dp(18), dp(20))
    page.addView(plusButton, plusParams)

    content.addView(page, FrameLayout.LayoutParams(-1, -1))

    if (uid == null) {
        chatList.addView(makeLabel("Hyr në llogari për të përdorur bisedat."))
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

            val visibleDocuments = documents.filter { document ->
                val hiddenFor = document.get("hiddenFor") as? List<*>
                    ?: emptyList<Any>()
                !hiddenFor.contains(uid)
            }

            if (visibleDocuments.isEmpty()) {
                chatList.addView(
                    makeLabel("Nuk ke ende biseda.\n\nShtyp + për të kërkuar kontakte! 🦅")
                )
                return@addSnapshotListener
            }

            visibleDocuments.forEach { document ->
                val participants = document.get("participants") as? List<*>
                    ?: emptyList<Any>()

                val otherUid = participants
                    .filterIsInstance<String>()
                    .firstOrNull { it != uid } ?: return@forEach

                db.collection("users").document(otherUid).get()
                    .addOnSuccessListener { userDocument ->

                        val username = userDocument.getString("username") ?: "Përdorues"

                        db.collection("chats")
                            .document(document.id)
                            .collection("messages")
                            .orderBy("createdAt", Query.Direction.DESCENDING)
                            .get()
                            .addOnSuccessListener { messages ->

                                val lastDocument = messages.documents.firstOrNull { msg ->
                                    val hiddenFor = msg.get("hiddenFor") as? List<*>
                                        ?: emptyList<Any>()
                                    !hiddenFor.contains(uid)
                                }

                                val lastType = lastDocument?.getString("type") ?: "text"

                                val lastMessage = when (lastType) {
                                    "audio" -> "🎤 Mesazh zanor"
                                    "image" -> "🖼️ Foto"
                                    "video" -> "🎥 Video"
                                    else -> lastDocument?.getString("text") ?: "Fillo bisedën"
                                }

                                val row = LinearLayout(this)
                                row.orientation = LinearLayout.HORIZONTAL
                                row.gravity = Gravity.CENTER_VERTICAL
                                row.setPadding(dp(12), dp(12), dp(12), dp(12))
                                row.setBackgroundColor(Color.WHITE)

                                val avatar = TextView(this)
                                avatar.text = "🦅"
                                avatar.textSize = 27f
                                avatar.gravity = Gravity.CENTER

                                val avatarBackground = GradientDrawable()
                                avatarBackground.shape = GradientDrawable.OVAL
                                avatarBackground.setColor(Color.rgb(110, 15, 25))
                                avatarBackground.setStroke(dp(2), gold)
                                avatar.background = avatarBackground

                                val avatarParams = LinearLayout.LayoutParams(dp(56), dp(56))
                                avatarParams.setMargins(0, 0, dp(14), 0)
                                row.addView(avatar, avatarParams)

                                val textLayout = LinearLayout(this)
                                textLayout.orientation = LinearLayout.VERTICAL

                                val name = TextView(this)
                                name.text = "@$username"
                                name.textSize = 18f
                                name.typeface = Typeface.DEFAULT_BOLD
                                name.setTextColor(Color.BLACK)
                                name.maxLines = 1

                                val preview = TextView(this)
                                preview.text = lastMessage
                                preview.textSize = 14f
                                preview.setTextColor(Color.DKGRAY)
                                preview.maxLines = 1
                                preview.ellipsize = android.text.TextUtils.TruncateAt.END
                                preview.setPadding(0, dp(5), 0, 0)

                                textLayout.addView(name)
                                textLayout.addView(preview)
                                row.addView(textLayout, LinearLayout.LayoutParams(0, -2, 1f))

                                val params = LinearLayout.LayoutParams(-1, -2)
                                params.setMargins(dp(3), dp(5), dp(3), dp(5))

                                row.setOnClickListener {
                                    showChat(document.id, username, otherUid)
                                }

                                chatList.addView(row, params)

val divider = View(this)
divider.setBackgroundColor(Color.rgb(230, 230, 230))

val dividerParams = LinearLayout.LayoutParams(
    -1, dp(1)
)
dividerParams.setMargins(dp(82), 0, dp(5), 0)

chatList.addView(divider, dividerParams)
                            }
                    }
            }
        }
}

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
            "Për të telefonuar një kontakt, hap kategorinë Chats dhe zgjidh personin që dëshiron të telefonosh."
        description.textSize = 15f
        description.setTextColor(secondaryText)
        description.gravity = Gravity.CENTER
        description.setPadding(0, dp(8), 0, dp(20))
        page.addView(description)

        val chatsButton = Button(this)
        chatsButton.text = "HAP CHATS"
        chatsButton.isAllCaps = false
        chatsButton.setOnClickListener { showHome("chats") }
        page.addView(chatsButton, LinearLayout.LayoutParams(-1, dp(52)))

        content.addView(page, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showProfileDialog() {
        val email = auth.currentUser?.email ?: "Vizitor"

        AlertDialog.Builder(this)
            .setTitle("Profili im 🦅")
            .setMessage("CHAT SHQIPONJA\n\nEmail: $email")
            .setPositiveButton("MBYLL", null)
            .setNeutralButton("DIL NGA LLOGARIA") { _, _ -> confirmLogout() }
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Dil nga llogaria")
            .setMessage("A dëshiron të dalësh nga llogaria?")
            .setPositiveButton("PO, DIL") { _, _ -> logout() }
            .setNegativeButton("ANULO", null)
            .show()
    }

    private fun logout() {
        stopRecording(false)
        releasePlayer()

        chatsListener?.remove()
        messagesListener?.remove()
        chatsListener = null
        messagesListener = null

        if (zegoInitialized) {
            try {
                ZegoUIKitPrebuiltCallService.unInit()
            } catch (_: Throwable) {
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
                if (query.isNotEmpty()) searchUser(query)
            }
            .setNegativeButton("ANULO", null)
            .show()
    }

    private fun saveUserProfile() {
        val user = auth.currentUser ?: return

        val userData = hashMapOf(
    "uid" to user.uid,
    "username" to (user.email?.substringBefore("@")?.lowercase() ?: "user"),
    "photoUrl" to "",
    "createdAt" to System.currentTimeMillis()
)

db.collection("users").document(user.uid).set(userData)
    .addOnSuccessListener {
        toast("Llogaria u krijua me sukses!")
    }
    .addOnFailureListener { e ->
        toast("Gabim: ${e.message}")
    }
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

                val otherUsers = documents.filter { it.id != currentUid }

                if (otherUsers.isEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Nuk u gjet")
                        .setMessage("Nuk u gjet asnjë përdorues.")
                        .setPositiveButton("MBYLL", null)
                        .show()
                } else {
                    val user = otherUsers.first()
                    val foundUsername = user.getString("username") ?: "Përdorues"

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

    private fun openChat(otherUserId: String, otherUsername: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        val participants = listOf(currentUserId, otherUserId).sorted()
        val chatId = participants.joinToString("_")

        val chatData = hashMapOf<String, Any>(
            "participants" to participants,
            "createdAt" to System.currentTimeMillis(),
            "hiddenFor" to FieldValue.arrayRemove(currentUserId)
        )

        db.collection("chats")
            .document(chatId)
            .set(chatData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                showChat(chatId, otherUsername, otherUserId)
            }
            .addOnFailureListener {
                toast("Gabim: ${it.message}")
            }
    }

    // FSHI NJË MESAZH
    private fun showMessageDeleteDialog(
        chatId: String,
        messageId: String,
        isMine: Boolean
    ) {
        val uid = auth.currentUser?.uid ?: return

        val options = if (isMine) {
            arrayOf("Fshi për mua", "Fshi për të gjithë", "ANULO")
        } else {
            arrayOf("Fshi për mua", "ANULO")
        }

        AlertDialog.Builder(this)
            .setTitle("Fshi mesazhin")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Fshi për mua" -> {
                        AlertDialog.Builder(this)
                            .setTitle("Fshi për mua")
                            .setMessage("Ky mesazh nuk do të shfaqet më në bisedën tënde. Personi tjetër do ta shohë ende.")
                            .setPositiveButton("Fshi") { _, _ ->
                                db.collection("chats")
                                    .document(chatId)
                                    .collection("messages")
                                    .document(messageId)
                                    .update("hiddenFor", FieldValue.arrayUnion(uid))
                                    .addOnSuccessListener {
                                        toast("Mesazhi u fshi për ty.")
                                    }
                                    .addOnFailureListener {
                                        toast("Nuk u fshi mesazhi: ${it.message}")
                                    }
                            }
                            .setNegativeButton("ANULO", null)
                            .show()
                    }

                    "Fshi për të gjithë" -> {
                        AlertDialog.Builder(this)
                            .setTitle("Fshi për të gjithë")
                            .setMessage("A dëshiron ta fshish këtë mesazh edhe për personin tjetër?")
                            .setPositiveButton("Fshi për të gjithë") { _, _ ->
                                db.collection("chats")
                                    .document(chatId)
                                    .collection("messages")
                                    .document(messageId)
                                    .delete()
                                    .addOnSuccessListener {
                                        toast("Mesazhi u fshi për të gjithë.")
                                    }
                                    .addOnFailureListener {
                                        toast("Nuk u fshi mesazhi: ${it.message}")
                                    }
                            }
                            .setNegativeButton("ANULO", null)
                            .show()
                    }
                }
            }
            .show()
    }

    // FSHI GJITHË BISEDËN VETËM PËR VETE
    private fun hideWholeChat(chatId: String) {
        val uid = auth.currentUser?.uid ?: return

        AlertDialog.Builder(this)
            .setTitle("Fshi gjithë bisedën")
            .setMessage(
                "Biseda do të hiqet vetëm nga lista jote.\n\n" +
                "Personi tjetër do t'i ruajë mesazhet dhe do të vazhdojë t'i shohë ato.\n\n" +
                "A dëshiron të vazhdosh?"
            )
            .setPositiveButton("Fshi për mua") { _, _ ->
                db.collection("chats")
                    .document(chatId)
                    .update("hiddenFor", FieldValue.arrayUnion(uid))
                    .addOnSuccessListener {
                        toast("Biseda u hoq nga lista jote.")
                        showHome("chats")
                    }
                    .addOnFailureListener {
                        toast("Nuk u fshi biseda: ${it.message}")
                    }
            }
            .setNegativeButton("ANULO", null)
            .show()
    }

    // FAQJA E BISEDËS
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

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(pageBackground)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(5), dp(8), dp(5), dp(8))

        val headerBg = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.rgb(75, 5, 15),
                Color.rgb(155, 12, 30),
                Color.rgb(75, 5, 15)
            )
        )
        header.background = headerBg
        header.elevation = dp(5).toFloat()

        val back = TextView(this)
        back.text = "‹"
        back.textSize = 38f
        back.setTextColor(Color.WHITE)
        back.gravity = Gravity.CENTER
        back.setOnClickListener { showHome("chats") }
        header.addView(back, LinearLayout.LayoutParams(dp(38), dp(54)))

        val avatar = TextView(this)
        avatar.text = "🦅"
        avatar.textSize = 27f
        avatar.gravity = Gravity.CENTER

        val avatarBg = GradientDrawable()
        avatarBg.shape = GradientDrawable.OVAL
        avatarBg.setColor(Color.rgb(45, 12, 18))
        avatarBg.setStroke(dp(2), gold)
        avatar.background = avatarBg

        val avatarParams = LinearLayout.LayoutParams(dp(48), dp(48))
        avatarParams.setMargins(dp(3), 0, dp(9), 0)
        header.addView(avatar, avatarParams)

        val contactInfo = LinearLayout(this)
        contactInfo.orientation = LinearLayout.VERTICAL
        contactInfo.gravity = Gravity.CENTER_VERTICAL

        val name = TextView(this)
        name.text = otherUsername.removePrefix("@")
        name.textSize = 16f
        name.typeface = Typeface.DEFAULT_BOLD
        name.setTextColor(Color.WHITE)
        name.maxLines = 1

        val status = TextView(this)
        status.text = "Bisedë në CHAT SHQIPONJA"
        status.textSize = 11f
        status.setTextColor(Color.rgb(235, 195, 115))

        contactInfo.addView(name)
        contactInfo.addView(status)
        header.addView(contactInfo, LinearLayout.LayoutParams(0, -2, 1f))

        val voiceCall = createCallButton(otherUserId, otherUsername, false)
        voiceCall.text = "📞"
        voiceCall.textSize = 20f
        voiceCall.setPadding(0, 0, 0, 0)
        val voiceParams = LinearLayout.LayoutParams(dp(43), dp(43))
        voiceParams.setMargins(dp(2), 0, dp(2), 0)
        header.addView(voiceCall, voiceParams)

        val videoCall = createCallButton(otherUserId, otherUsername, true)
        videoCall.text = "📹"
        videoCall.textSize = 20f
        videoCall.setPadding(0, 0, 0, 0)
        val videoParams = LinearLayout.LayoutParams(dp(43), dp(43))
        videoParams.setMargins(dp(2), 0, dp(2), 0)
        header.addView(videoCall, videoParams)

        val menuButton = TextView(this)
        menuButton.text = "⋮"
        menuButton.textSize = 29f
        menuButton.typeface = Typeface.DEFAULT_BOLD
        menuButton.setTextColor(Color.WHITE)
        menuButton.gravity = Gravity.CENTER
        menuButton.setOnClickListener {
            val popup = PopupMenu(this, menuButton)
            popup.menu.add("Profili i kontaktit")
            popup.menu.add("Privatësia")
            popup.menu.add("Fshi gjithë bisedën")
            popup.menu.add("Mbyll bisedën")

            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Profili i kontaktit" -> {
                        AlertDialog.Builder(this)
                            .setTitle("🦅 Profili")
                            .setMessage("Emri: ${otherUsername.removePrefix("@")}\n\nCHAT SHQIPONJA")
                            .setPositiveButton("MBYLL", null)
                            .show()
                        true
                    }
                    "Privatësia" -> {
                        showPrivacyDialog()
                        true
                    }
                    "Fshi gjithë bisedën" -> {
                        hideWholeChat(chatId)
                        true
                    }
                    "Mbyll bisedën" -> {
                        showHome("chats")
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
        header.addView(menuButton, LinearLayout.LayoutParams(dp(35), dp(48)))

        val conversation = FrameLayout(this)
        conversation.setBackgroundColor(pageBackground)

        val scrollView = ScrollView(this)
        scrollView.isFillViewport = true
        scrollView.setClipToPadding(false)

        val messageList = LinearLayout(this)
        messageList.orientation = LinearLayout.VERTICAL
        messageList.setPadding(dp(12), dp(16), dp(12), dp(16))

        scrollView.addView(messageList, FrameLayout.LayoutParams(-1, -2))
        conversation.addView(scrollView, FrameLayout.LayoutParams(-1, -1))

        val inputPanel = LinearLayout(this)
        inputPanel.orientation = LinearLayout.HORIZONTAL
        inputPanel.gravity = Gravity.CENTER_VERTICAL
        inputPanel.setPadding(dp(8), dp(9), dp(8), dp(9))
        inputPanel.setBackgroundColor(cardBackground)

        val emojiButton = TextView(this)
        emojiButton.text = "😊"
        emojiButton.textSize = 25f
        emojiButton.gravity = Gravity.CENTER
        emojiButton.setTextColor(white)

        val emojiParams = LinearLayout.LayoutParams(dp(42), dp(48))
        emojiParams.setMargins(0, 0, dp(5), 0)
        inputPanel.addView(emojiButton, emojiParams)

        val messageInput = EditText(this)
        messageInput.hint = "Mesazhe"
        messageInput.textSize = 15f
        messageInput.setTextColor(white)
        messageInput.setHintTextColor(secondaryText)
        messageInput.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        messageInput.maxLines = 4
        messageInput.setPadding(dp(14), dp(9), dp(14), dp(9))

        val inputBg = GradientDrawable()
        inputBg.setColor(
            if (isLightTheme) Color.rgb(235, 235, 240)
            else Color.rgb(45, 45, 50)
        )
        inputBg.cornerRadius = dp(25).toFloat()
        inputBg.setStroke(
            dp(1),
            if (isLightTheme) Color.rgb(210, 210, 220)
            else Color.rgb(95, 75, 65)
        )
        messageInput.background = inputBg

        inputPanel.addView(messageInput, LinearLayout.LayoutParams(0, -2, 1f))

        val voiceButton = TextView(this)
        voiceButton.text = "🎤"
        voiceButton.textSize = 23f
        voiceButton.gravity = Gravity.CENTER
        voiceButton.setTextColor(white)

        val voiceButtonParams = LinearLayout.LayoutParams(dp(42), dp(48))
        voiceButtonParams.setMargins(dp(3), 0, 0, 0)
        inputPanel.addView(voiceButton, voiceButtonParams)

        val uploadButton = TextView(this)
        uploadButton.text = "📎"
        uploadButton.textSize = 24f
        uploadButton.gravity = Gravity.CENTER
        uploadButton.setTextColor(white)

        val uploadParams = LinearLayout.LayoutParams(dp(42), dp(48))
        uploadParams.setMargins(dp(2), 0, 0, 0)
        inputPanel.addView(uploadButton, uploadParams)

        val sendButton = TextView(this)
        sendButton.text = "➤"
        sendButton.textSize = 23f
        sendButton.typeface = Typeface.DEFAULT_BOLD
        sendButton.setTextColor(Color.WHITE)
        sendButton.gravity = Gravity.CENTER

        val sendBg = GradientDrawable()
        sendBg.setColor(red)
        sendBg.cornerRadius = dp(30).toFloat()
        sendBg.setStroke(dp(1), gold)
        sendButton.background = sendBg
        sendButton.elevation = dp(3).toFloat()

        val sendParams = LinearLayout.LayoutParams(dp(46), dp(46))
        sendParams.setMargins(dp(5), 0, 0, 0)
        inputPanel.addView(sendButton, sendParams)

        emojiButton.setOnClickListener {
            val popup = PopupMenu(this, emojiButton)
            val emojis = listOf(
                "😀", "😂", "😍", "🥰", "😘", "❤️", "👍", "🙏",
                "🔥", "😎", "😭", "🤣", "😊", "😉", "👏", "💪",
                "🎉", "🦅"
            )
            emojis.forEach { popup.menu.add(it) }

            popup.setOnMenuItemClickListener { item ->
                val position = messageInput.selectionStart.coerceAtLeast(0)
                messageInput.text.insert(position, item.title.toString())
                true
            }
            popup.show()
        }

        voiceButton.setOnClickListener {
            if (isRecording) {
                stopRecording(true)
            } else {
                startVoiceRecording(chatId, voiceButton)
            }
        }

        uploadButton.setOnClickListener {
            openMediaPicker(chatId)
        }

        page.addView(header)
        page.addView(conversation, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(inputPanel)
        setContentView(page)

        fun sendMessage() {
            val text = messageInput.text.toString().trim()
            val senderId = auth.currentUser?.uid

            if (senderId == null) {
                toast("Duhet të hysh në llogari")
                return
            }

            if (text.isEmpty()) return
            if (text.length > 4000) {
                toast("Mesazhi është shumë i gjatë")
                return
            }

            val messageData = hashMapOf(
                "senderId" to senderId,
                "type" to "text",
                "text" to text,
                "createdAt" to System.currentTimeMillis()
            )

            sendButton.isEnabled = false
            sendButton.alpha = 0.5f

            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .add(messageData)
                .addOnSuccessListener {
                    messageInput.text.clear()
                    sendButton.isEnabled = true
                    sendButton.alpha = 1f
                }
                .addOnFailureListener {
                    sendButton.isEnabled = true
                    sendButton.alpha = 1f
                    toast("Mesazhi nuk u dërgua: ${it.message}")
                }
        }

        sendButton.setOnClickListener { sendMessage() }

        messageInput.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
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

                val documents = snapshots?.documents ?: emptyList()
                val currentUid = auth.currentUser?.uid

                val visibleMessages = documents.filter { document ->
                    val hiddenFor = document.get("hiddenFor") as? List<*>
                        ?: emptyList<Any>()
                    !hiddenFor.contains(currentUid)
                }

                if (visibleMessages.isEmpty()) {
                    val empty = TextView(this)
                    empty.text = if (documents.isEmpty()) {
                        "🦅\n\nKjo është biseda juaj e parë.\nDërgo një mesazh për të filluar!"
                    } else {
                        "Nuk ka mesazhe për të shfaqur."
                    }
                    empty.textSize = 15f
                    empty.gravity = Gravity.CENTER
                    empty.setTextColor(secondaryText)
                    empty.setPadding(dp(20), dp(45), dp(20), dp(45))
                    messageList.addView(empty, LinearLayout.LayoutParams(-1, -2))
                }

                visibleMessages.forEach { document ->
                    val senderId = document.getString("senderId") ?: ""
                    val type = document.getString("type") ?: "text"
                    val text = document.getString("text") ?: ""
                    val audioUrl = document.getString("audioUrl")
                    val mediaUrl = document.getString("mediaUrl")
                    val createdAt = document.getLong("createdAt") ?: 0L
                    val isMine = senderId == currentUid

                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.VERTICAL
                    row.gravity = if (isMine) Gravity.END else Gravity.START

                    val bubble = LinearLayout(this)
                    bubble.orientation = LinearLayout.VERTICAL
                    bubble.setPadding(dp(12), dp(10), dp(12), dp(8))
                    bubble.gravity = Gravity.CENTER_VERTICAL

                    val bubbleBg = GradientDrawable()
                    if (isMine) {
                        bubbleBg.setColor(Color.rgb(145, 15, 30))
                        bubbleBg.setStroke(dp(1), Color.rgb(195, 115, 65))
                    } else {
                        bubbleBg.setColor(
                            if (isLightTheme) Color.rgb(235, 235, 240)
                            else Color.rgb(43, 43, 50)
                        )
                        bubbleBg.setStroke(
                            dp(1),
                            if (isLightTheme) Color.rgb(215, 215, 225)
                            else Color.rgb(85, 75, 65)
                        )
                    }
                    bubbleBg.cornerRadius = dp(19).toFloat()
                    bubble.background = bubbleBg
                    bubble.elevation = dp(2).toFloat()

                    when (type) {
                        "audio" -> {
                            val audioButton = Button(this)
                            audioButton.text = "▶️  Dëgjo mesazhin zanor"
                            audioButton.isAllCaps = false
                            audioButton.setOnClickListener {
                                if (!audioUrl.isNullOrBlank()) {
                                    playAudio(audioUrl)
                                } else {
                                    toast("Skedari zanor nuk u gjet.")
                                }
                            }
                            bubble.addView(audioButton)
                        }

                        "image" -> {
                            val imageView = ImageView(this)
                            imageView.adjustViewBounds = true
                            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                            imageView.maxWidth = dp(250)
                            imageView.maxHeight = dp(300)
                            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                            bubble.addView(
                                imageView,
                                LinearLayout.LayoutParams(dp(240), dp(220))
                            )

                            if (!mediaUrl.isNullOrBlank()) {
                                loadImage(mediaUrl, imageView)
                                imageView.setOnClickListener {
                                    openMedia(mediaUrl, "image/*")
                                }
                            }
                        }

                        "video" -> {
                            val videoButton = Button(this)
                            videoButton.text = "▶️  Shiko videon"
                            videoButton.isAllCaps = false
                            videoButton.setOnClickListener {
                                if (!mediaUrl.isNullOrBlank()) {
                                    openMedia(mediaUrl, "video/*")
                                } else {
                                    toast("Videoja nuk u gjet.")
                                }
                            }
                            bubble.addView(videoButton)
                        }

                        else -> {
                            val textView = TextView(this)
                            textView.text = text
                            textView.textSize = 15.5f
                            textView.setTextColor(if (isMine) Color.WHITE else white)
                            textView.setPadding(dp(3), dp(2), dp(3), dp(2))
                            textView.maxWidth = dp(285)
                            bubble.addView(textView)
                        }
                    }

                    bubble.setOnLongClickListener {
                        showMessageDeleteDialog(chatId, document.id, isMine)
                        true
                    }

                    val bubbleParams = LinearLayout.LayoutParams(-2, -2)
                    bubbleParams.setMargins(dp(3), dp(3), dp(3), dp(2))
                    row.addView(bubble, bubbleParams)

                    val time = TextView(this)
                    time.text = if (createdAt > 0) {
                        java.text.SimpleDateFormat(
                            "HH:mm",
                            java.util.Locale.getDefault()
                        ).format(java.util.Date(createdAt))
                    } else {
                        ""
                    }
                    time.textSize = 10f
                    time.setTextColor(secondaryText)
                    time.gravity = if (isMine) Gravity.END else Gravity.START
                    time.setPadding(dp(7), dp(1), dp(7), dp(2))
                    row.addView(time)

                    row.setOnLongClickListener {
                        showMessageDeleteDialog(chatId, document.id, isMine)
                        true
                    }

                    val rowParams = LinearLayout.LayoutParams(-1, -2)
                    rowParams.setMargins(0, dp(3), 0, dp(3))
                    messageList.addView(row, rowParams)
                }

                scrollView.post {
                    scrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
    }

    // REGJISTRIMI I MESAZHIT ZANOR
    private fun startVoiceRecording(chatId: String, button: TextView) {
        val user = auth.currentUser
        if (user == null) {
            toast("Duhet të hysh në llogari.")
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingVoiceChatId = chatId
            activeVoiceButton = button
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                4002
            )
            return
        }

        try {
            val file = File(cacheDir, "voice_${UUID.randomUUID()}.m4a")
            recordingFile = file
            recordingChatId = chatId

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            activeVoiceButton = button
            isRecording = true
            button.text = "⏹"
            button.setTextColor(Color.RED)
            toast("Po regjistrohet... Shtyp ⏹ për ta dërguar.")
        } catch (e: Exception) {
            isRecording = false
            try {
                mediaRecorder?.release()
            } catch (_: Throwable) {
            }
            mediaRecorder = null
            recordingFile?.delete()
            recordingFile = null
            button.text = "🎤"
            button.setTextColor(Color.WHITE)
            toast("Nuk filloi regjistrimi: ${e.message}")
        }
    }

    private fun stopRecording(send: Boolean) {
        val recorder = mediaRecorder
        val file = recordingFile
        val chatId = recordingChatId
        val button = activeVoiceButton

        isRecording = false
        mediaRecorder = null
        recordingFile = null
        recordingChatId = null
        activeVoiceButton = null

        try {
            recorder?.stop()
        } catch (_: Throwable) {
            toast("Regjistrimi ishte shumë i shkurtër.")
        }

        try {
            recorder?.reset()
            recorder?.release()
        } catch (_: Throwable) {
        }

        button?.text = "🎤"
        button?.setTextColor(Color.WHITE)

        if (!send || file == null || chatId == null || !file.exists() || file.length() < 1000) {
            file?.delete()
            return
        }

        uploadVoice(file, chatId)
    }

    private fun uploadVoice(file: File, chatId: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            file.delete()
            toast("Duhet të hysh në llogari.")
            return
        }

        toast("Po dërgohet mesazhi zanor...")

        val storageRef = storage.reference.child(
            "chat_media/$chatId/${UUID.randomUUID()}.m4a"
        )

        storageRef.putFile(Uri.fromFile(file))
            .addOnSuccessListener {
                storageRef.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        val messageData = hashMapOf(
                            "senderId" to uid,
                            "type" to "audio",
                            "audioUrl" to downloadUri.toString(),
                            "text" to "🎤 Mesazh zanor",
                            "createdAt" to System.currentTimeMillis()
                        )

                        db.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .add(messageData)
                            .addOnSuccessListener {
                                toast("Mesazhi zanor u dërgua! 🎤")
                                file.delete()
                            }
                            .addOnFailureListener {
                                toast("Gabim në dërgimin e mesazhit: ${it.message}")
                                file.delete()
                            }
                    }
                    .addOnFailureListener {
                        toast("Nuk u mor lidhja e audios: ${it.message}")
                        file.delete()
                    }
            }
            .addOnFailureListener {
                toast("Ngarkimi i audios dështoi: ${it.message}")
                file.delete()
            }
    }

    // LUAJTJA E MESAZHIT ZANOR
    private fun playAudio(url: String) {
        releasePlayer()

        try {
            val player = MediaPlayer()
            mediaPlayer = player

            player.setDataSource(url)
            player.setOnPreparedListener {
                it.start()
                toast("Po luhet mesazhi zanor...")
            }
            player.setOnCompletionListener {
                releasePlayer()
            }
            player.setOnErrorListener { _, _, _ ->
                releasePlayer()
                toast("Nuk u luajt mesazhi zanor.")
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            releasePlayer()
            toast("Gabim në hapjen e audios: ${e.message}")
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Throwable) {
        }
        mediaPlayer = null
    }

    // ZGJEDHJA E FOTOVE DHE VIDEOVE
    private fun openMediaPicker(chatId: String) {
        try {
            pendingUploadChatId = chatId

            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("image/*", "video/*")
            )
            intent.addCategory(Intent.CATEGORY_OPENABLE)

            startActivityForResult(
                Intent.createChooser(intent, "Zgjidh foto ose video"),
                3001
            )
        } catch (e: Exception) {
            toast("Nuk u hap galeria: ${e.message}")
        }
    }

    @Deprecated("Përdoret për zgjedhjen e medias")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 3001 && resultCode == RESULT_OK) {
            val uri = data?.data
            val chatId = pendingUploadChatId
            pendingUploadChatId = null

            if (uri == null || chatId == null) {
                toast("Nuk u zgjodh asnjë skedar.")
                return
            }

            val mimeType = contentResolver.getType(uri) ?: ""

            if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
                uploadMedia(uri, chatId, mimeType)
            } else {
                toast("Zgjidh një foto ose video.")
            }
        }
    }

    private fun uploadMedia(uri: Uri, chatId: String, mimeType: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            toast("Duhet të hysh në llogari.")
            return
        }

        val isImage = mimeType.startsWith("image/")
        val type = if (isImage) "image" else "video"
        val extension = if (isImage) "jpg" else "mp4"

        toast(if (isImage) "Po dërgohet fotoja..." else "Po dërgohet videoja...")

        val storageRef = storage.reference.child(
            "chat_media/$chatId/${UUID.randomUUID()}.$extension"
        )

        storageRef.putFile(uri)
            .addOnSuccessListener {
                storageRef.downloadUrl
                    .addOnSuccessListener { downloadUri ->

                        val messageData = hashMapOf(
                            "senderId" to uid,
                            "type" to type,
                            "mediaUrl" to downloadUri.toString(),
                            "mimeType" to mimeType,
                            "text" to if (isImage) "🖼️ Foto" else "🎥 Video",
                            "createdAt" to System.currentTimeMillis()
                        )

                        db.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .add(messageData)
                            .addOnSuccessListener {
                                toast(if (isImage) "Fotoja u dërgua! 🖼️" else "Videoja u dërgua! 🎥")
                            }
                            .addOnFailureListener {
                                toast("Gabim në ruajtjen e medias: ${it.message}")
                            }
                    }
                    .addOnFailureListener {
                        toast("Nuk u mor lidhja e medias: ${it.message}")
                    }
            }
            .addOnFailureListener {
                toast("Ngarkimi dështoi: ${it.message}")
            }
    }

    // SHFAQJA E FOTOS DHE VIDEOS
    private fun loadImage(url: String, imageView: ImageView) {
        Thread {
            try {
                val connection = java.net.URL(url).openConnection()
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val bitmap = connection.getInputStream().use {
                    BitmapFactory.decodeStream(it)
                }

                runOnUiThread {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    } else {
                        imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                }
            }
        }.start()
    }

    private fun openMedia(url: String, mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse(url), mimeType)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                toast("Nuk u hap skedari. Kontrollo nëse ke aplikacion për ta hapur.")
            }
        }
    }

    // LEJET E MIKROFONIT
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 4002) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                val chatId = pendingVoiceChatId
                val button = activeVoiceButton
                pendingVoiceChatId = null

                if (chatId != null && button != null) {
                    startVoiceRecording(chatId, button)
                }
            } else {
                pendingVoiceChatId = null
                activeVoiceButton?.text = "🎤"
                activeVoiceButton = null
                toast("Duhet të lejosh përdorimin e mikrofonit.")
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        messagesListener?.remove()
        chatsListener?.remove()
        messagesListener = null
        chatsListener = null

        stopRecording(false)
        releasePlayer()

        super.onDestroy()
    }
}
