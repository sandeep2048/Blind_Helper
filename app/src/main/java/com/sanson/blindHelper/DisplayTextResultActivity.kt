package com.sanson.blindHelper

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView

class DisplayTextResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_text_result)

        val message = intent.getStringExtra(TEXT_MESSAGE).toString()
        val textViewEnglish = findViewById<TextView>(R.id.textView_english).apply {
            text = message
        }

        val re = Regex("[^ A-Za-z0-9\n]")
        val filterMessage = re.replace(message, "")

        val brailleText = filterMessage.toUpperCase().map { MAP[it] }.joinToString(separator = "")

        val textViewBraille = findViewById<TextView>(R.id.textView_braille).apply {
            text = brailleText
        }
    }
}