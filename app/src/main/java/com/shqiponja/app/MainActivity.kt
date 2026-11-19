package com.shqiponja.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
import android.app.AlertDialog
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null
    private lateinit var codeInput: EditText
    private lateinit var verifyButton: Button

    private val green = Color.rgb(20, 130, 70)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

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
        subtitle.text = "Regjistrohu me numrin tënd"
        subtitle.textSize = 20f
        subtitle.gravity = Gravity.CENTER

        val phoneInput = EditText(this)
        phoneInput.hint = "Numri yt, p.sh. +355691234567"
        phoneInput.inputType = InputType.TYPE_CLASS_PHONE

        val sendButton = Button(this)
        sendButton.text = "DËRGO KODIN SMS"
        val guestButton = Button(this)
guestButton.text = "HYR SI VIZITOR 🦅"

guestButton.setOnClickListener {
    showHome()
}

        codeInput = EditText(this)
        codeInput.hint = "Shkruaj kodin SMS"
        codeInput.inputType = InputType.TYPE_CLASS_NUMBER
        codeInput.visibility = View.GONE

        verifyButton = Button(this)
        verifyButton.text = "VERIFIKO KODIN"
        verifyButton.visibility = View.GONE

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(phoneInput)
        layout.addView(sendButton)
        layout.addView(codeInput)
        layout.addView(verifyButton)

        setContentView(layout)

        val callbacks =
            object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onVerificationCompleted(
                    credential: PhoneAuthCredential
                ) {
                    signIn(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    val kodi =
                        if (e is com.google.firebase.auth.FirebaseAuthException) {
                            e.errorCode
                        } else {
                            e.javaClass.simpleName
                        }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Gabim: $kodi")
                        .setMessage(e.message)
                        .setPositiveButton("OK", null)
                        .show()
                }

                override fun onCodeSent(
                    id: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    verificationId = id
                    codeInput.visibility = View.VISIBLE
                    verifyButton.visibility = View.VISIBLE

                    Toast.makeText(
                        this@MainActivity,
                        "Kodi SMS u dërgua!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        sendButton.setOnClickListener {
            val number = phoneInput.text.toString().trim()

            if (!number.startsWith("+") || number.length < 10) {
                Toast.makeText(
                    this,
                    "Shkruaj numrin me prefiks +355",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val options = PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(number)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(this)
                    .setCallbacks(callbacks)
                    .build()

                PhoneAuthProvider.verifyPhoneNumber(options)
            }
        }

        verifyButton.setOnClickListener {
            val id = verificationId
            val code = codeInput.text.toString().trim()

            if (id != null && code.length == 6) {
                val credential =
                    PhoneAuthProvider.getCredential(id, code)
                signIn(credential)
            } else {
                Toast.makeText(
                    this,
                    "Shkruaj kodin 6-shifror",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun signIn(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
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

    private fun showHome() {
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
                    "CHAT SHQIPONJA\n\nNumri: ${
                        auth.currentUser?.phoneNumber ?: "Pa numër"
                    }"
                )
                .setPositiveButton("MBYLL", null)
                .show()
        }

        val newChatButton = Button(this)
        newChatButton.text = "＋ BISEDË E RE"

        val chatList = LinearLayout(this)
        chatList.orientation = LinearLayout.VERTICAL
        chatList.setPadding(20, 15, 20, 15)

        val emptyText = TextView(this)
        emptyText.text = "Nuk ke ende biseda.\nFillo një bisedë të re!"
        emptyText.textSize = 17f
        emptyText.gravity = Gravity.CENTER
        emptyText.setPadding(10, 50, 10, 50)
        chatList.addView(emptyText)

        newChatButton.setOnClickListener {
            val input = EditText(this)
            input.hint = "Shkruaj emrin e kontaktit"

            AlertDialog.Builder(this)
                .setTitle("Bisedë e re")
                .setView(input)
                .setPositiveButton("KRIJO") { _, _ ->
                    val name = input.text.toString().trim()

                    if (name.isNotEmpty()) {
                        if (chatList.childCount == 1 &&
                            chatList.getChildAt(0) === emptyText
                        ) {
                            chatList.removeAllViews()
                        }

                        val contact = TextView(this)
                        contact.text = "👤  $name\n      Bisedë e re"
                        contact.textSize = 18f
                        contact.setTextColor(Color.BLACK)
                        contact.setPadding(15, 20, 15, 20)
                        chatList.addView(contact)

                        Toast.makeText(
                            this,
                            "Kontakti u shtua në listën lokale",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("ANULO", null)
                .show()
        }

        layout.addView(header)
        layout.addView(subtitle)
        layout.addView(profileButton)
        layout.addView(newChatButton)
        layout.addView(chatList)

        setContentView(layout)
    }
}
