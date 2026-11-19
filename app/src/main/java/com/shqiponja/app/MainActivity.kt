package com.shqiponja.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(35, 35, 35, 35)
        layout.setBackgroundColor(Color.WHITE)

        val title = TextView(this)
        title.text = "🦅 CHAT SHQIPONJA"
        title.textSize = 30f
        title.setTextColor(Color.rgb(20, 130, 70))
        title.gravity = Gravity.CENTER

        val subtitle = TextView(this)
        subtitle.text = "Mirë se erdhe!"
        subtitle.textSize = 22f
        subtitle.gravity = Gravity.CENTER

        val phone = EditText(this)
        phone.hint = "Numri yt i telefonit"
        phone.inputType = 3

        val button = Button(this)
        button.text = "VAZHDO"

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(phone)
        layout.addView(button)

        setContentView(layout)
    }
}
