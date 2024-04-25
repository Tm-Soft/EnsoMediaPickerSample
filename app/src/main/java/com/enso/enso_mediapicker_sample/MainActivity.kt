package com.enso.enso_mediapicker_sample

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.enso.enso_imagepicker_sample.R
import com.enso.ensomediapicker.EnsoMediaPickerActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_select_image).setOnClickListener {
            startActivity(
                Intent(this@MainActivity, EnsoMediaPickerActivity::class.java)
            )
        }
    }
}