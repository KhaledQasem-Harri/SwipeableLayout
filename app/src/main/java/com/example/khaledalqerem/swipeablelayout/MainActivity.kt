package com.example.khaledalqerem.swipeablelayout

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.Toolbar

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        val toolbar = findViewById<Toolbar>(R.id.toolbar)
//        setSupportActionBar(toolbar)

        fun layoutOneOnClick(v: View) {
            Toast.makeText(this@MainActivity, "Layout 1 clicked", Toast.LENGTH_SHORT).show()
        }

        fun moreOnClick(v: View) {
            Toast.makeText(this@MainActivity, "More clicked", Toast.LENGTH_SHORT).show()
        }

        fun deleteOnClick(v: View) {
            Toast.makeText(this@MainActivity, "Delete clicked", Toast.LENGTH_SHORT).show()
        }
    }
}
