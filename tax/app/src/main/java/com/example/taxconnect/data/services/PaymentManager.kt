package com.example.taxconnect.data.services

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.example.taxconnect.BuildConfig
import com.example.taxconnect.R
import com.example.taxconnect.core.base.BaseActivity
import com.razorpay.Checkout
import org.json.JSONObject

class PaymentManager {
    companion object {
        private const val TAG = "PaymentManager"
    }

    fun startPayment(activity: Activity, amount: String, email: String, contact: String, description: String) {
        val checkout = Checkout()
        checkout.setKeyID(BuildConfig.RAZORPAY_API_KEY)

        checkout.setImage(R.mipmap.ic_launcher)

        try {
            val options = JSONObject()
            options.put("name", "Tax Connect")
            options.put("description", description)
            options.put("image", "https://s3.amazonaws.com/rzp-mobile/images/rzp.png")
            options.put("theme.color", "#3399cc")
            options.put("currency", "INR")

            // Amount in paise (multiply by 100)
            val amt = amount.toDouble()
            options.put("amount", (amt * 100).toInt())

            val preFill = JSONObject()
            preFill.put("email", email)
            preFill.put("contact", contact)
            options.put("prefill", preFill)

            checkout.open(activity, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error in starting Razorpay Checkout", e)
            if (activity is BaseActivity<*>) {
                activity.showToast("Error in payment: " + e.message)
            } else {
                Toast.makeText(activity, "Error in payment: " + e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
