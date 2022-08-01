package com.example.plantdiseasedetector.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.plantdiseasedetector.activities.MainActivity.Companion.TAG
import com.example.plantdiseasedetector.databinding.ActivitySendOtpBinding
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit


class SendOtpActivity : AppCompatActivity() {
    private lateinit var binding:ActivitySendOtpBinding
    private lateinit var mAuth:FirebaseAuth

    override fun onStart() {
        super.onStart()
        val user = Firebase.auth.currentUser
        if (user != null) {
            val intent = Intent(this@SendOtpActivity, MainActivity::class.java)
            startActivity(intent)
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySendOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)


        mAuth = FirebaseAuth.getInstance()

        binding.getOtp.setOnClickListener {
            if (binding.inputMobile.text.toString().trim().isEmpty()){
                Toast.makeText(this, "Enter Mobile Number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.progressBar.visibility = View.VISIBLE
            binding.getOtp.visibility = View.INVISIBLE
            val phone = "+91"+binding.inputMobile.text.toString()
            sendVerificationCode(phone)
        }
    }

    private fun sendVerificationCode(phone: String) {
        val options = PhoneAuthOptions.newBuilder(mAuth)
            .setPhoneNumber(phone)     
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
        
    }
    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            binding.progressBar.visibility = View.GONE
            binding.getOtp.visibility = View.VISIBLE

            Log.d(TAG, "onVerificationCompleted:$credential")

        }

        override fun onVerificationFailed(e: FirebaseException) {
            binding.progressBar.visibility = View.GONE
            binding.getOtp.visibility = View.VISIBLE
            Toast.makeText(this@SendOtpActivity, e.message, Toast.LENGTH_SHORT).show()
            if (e is FirebaseAuthInvalidCredentialsException) {
                // Invalid request
            } else if (e is FirebaseTooManyRequestsException) {
                Toast.makeText(this@SendOtpActivity, "Sms Quota Exceeded", Toast.LENGTH_SHORT).show()
            }

        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            Log.d(TAG, "onCodeSent:$verificationId")
            binding.progressBar.visibility = View.GONE
            binding.getOtp.visibility = View.VISIBLE

            val intent = Intent(applicationContext, VerifyOtpActivity::class.java)
            intent.putExtra("mobile",binding.inputMobile.text.toString())
            intent.putExtra("verificationId",verificationId)
            intent.putExtra("resendToken",token)
            startActivity(intent)

        }
    }
}