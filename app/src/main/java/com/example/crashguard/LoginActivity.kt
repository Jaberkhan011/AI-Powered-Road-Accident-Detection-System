package com.example.crashguard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var etUser: EditText
    private lateinit var etPhone: EditText
    private lateinit var etPass: EditText
    private lateinit var btnLogin: Button
    private lateinit var cbRemember: CheckBox


    private val REQ = 77

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auto-login if previously saved
        if (AuthManager.isLoggedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)
        supportActionBar?.hide()
        etUser = findViewById(R.id.etUser)
        etPhone = findViewById(R.id.etPhone)
        etPass = findViewById(R.id.etPass)
        btnLogin = findViewById(R.id.btnLogin)
        cbRemember = findViewById(R.id.cbRemember)



        btnLogin.setOnClickListener {
            val user = etUser.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val pass = etPass.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Enter username, phone and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Demo: accept any credentials
            if (cbRemember.isChecked) {
                AuthManager.saveLogin(this, user, phone)
            }

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }


}