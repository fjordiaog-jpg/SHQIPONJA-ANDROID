
package com.shqiponja.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.text.InputType
import android.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : Activity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val green = Color.rgb(20, 130, 70)

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
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        val passwordInput = EditText(this)
        passwordInput.hint = "Fjalëkalimi"
        passwordInput.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

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
                Toast.makeText(this, "Plotëso emailin dhe fjalëkalimin", Toast.LENGTH_LONG).show()
            } else if (password.length < 6) {
                Toast.makeText(this, "Fjalëkalimi duhet të ketë të paktën 6 karaktere", Toast.LENGTH_LONG).show()
            } else {
                auth.createUserWithEmailAndPassword(email, password)
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
        }

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Plotëso emailin dhe fjalëkalimin", Toast.LENGTH_LONG).show()
            } else {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
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

        val logoutButton = Button(this)
        logoutButton.text = "DIL NGA LLOGARIA"

        logoutButton.setOnClickListener {
            auth.signOut()
            showLogin()
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
        layout.addView(logoutButton)

        setContentView(layout)
    }
}
