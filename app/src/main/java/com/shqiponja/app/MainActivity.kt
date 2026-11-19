
package com.shqiponja.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(35, 35, 35, 35)
        layout.setBackgroundColor(Color.WHITE)

        val title = TextView(this)
        title.text = "🦅 CHAT SHQIPONJA"
        title.textSize = 28f
        title.setTextColor(Color.rgb(20, 130, 70))
        title.gravity = Gravity.CENTER

        val subtitle = TextView(this)
        subtitle.text = "Regjistrohu me numrin tënd"
        subtitle.textSize = 20f
        subtitle.gravity = Gravity.CENTER

        val phoneInput = EditText(this)
        phoneInput.hint = "Numri yt, p.sh. +355691234567"
        phoneInput.inputType = 3

        val sendButton = Button(this)
        sendButton.text = "DËRGO KODIN SMS"

        codeInput = EditText(this)
        codeInput.hint = "Shkruaj kodin SMS"
        codeInput.inputType = 2
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
                    Toast.makeText(
                        this@MainActivity,
                        "Gabim: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
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
                    "Shkruaj numrin me prefiksin e shtetit, p.sh. +355",
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
                    Toast.makeText(
                        this,
                        "Regjistrimi u krye me sukses! 🦅",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Verifikimi dështoi. Provo përsëri.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}
