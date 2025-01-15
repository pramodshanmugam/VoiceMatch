package com.example.voicematchapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Vibrator
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SOSActivity : AppCompatActivity() {

    private val phoneNumber = "+919035755519"
    private val callPermissionRequestCode = 1
    private lateinit var timer: CountDownTimer
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the root layout
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }

        // Create the "SOS" TextView
        val sosTextView = TextView(this).apply {
            text = "SOS"
            setTextColor(Color.RED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            setPadding(0, 0, 0, 16)
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }

        // Create the timer TextView
        val timerTextView = TextView(this).apply {
            text = "Calling in 30 seconds..."
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, 0, 0, 16)
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }

        // Create the Cancel button
        val cancelButton = Button(this).apply {
            text = "CANCEL"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            textSize = 16f
            setOnClickListener {
                timer.cancel()
                vibrator.cancel()
                finish() // Close the activity
            }
        }

        // Add views to the root layout
        rootLayout.addView(sosTextView)
        rootLayout.addView(timerTextView)
        rootLayout.addView(cancelButton)

        // Set the content view to the root layout
        setContentView(rootLayout)

        // Initialize the vibrator
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        // Start the countdown with vibration
        timer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerTextView.text = "Calling in ${millisUntilFinished / 1000} seconds..."
                vibrator.vibrate(500) // Vibrate for 500ms
            }

            override fun onFinish() {
                timerTextView.text = "Calling now..."
                callPhoneNumber(phoneNumber)
            }
        }.start()
    }

    private fun callPhoneNumber(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                callPermissionRequestCode
            )
        } else {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            try {
                startActivity(callIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer.cancel() // Stop the timer if the activity is destroyed
        vibrator.cancel() // Stop vibration
    }
}
