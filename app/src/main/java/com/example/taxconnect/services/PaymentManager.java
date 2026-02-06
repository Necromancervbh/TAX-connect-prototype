package com.example.taxconnect.services;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import com.example.taxconnect.R;
import com.razorpay.Checkout;

import org.json.JSONObject;

public class PaymentManager {
    private static final String TAG = "PaymentManager";

    public void startPayment(Activity activity, String amount, String email, String contact, String description) {
        Checkout checkout = new Checkout();
        // Placeholder Key - REPLACE WITH YOUR ACTUAL KEY
        checkout.setKeyID("rzp_test_placeholder"); 

        checkout.setImage(R.mipmap.ic_launcher);

        try {
            JSONObject options = new JSONObject();
            options.put("name", "Tax Connect");
            options.put("description", description);
            options.put("image", "https://s3.amazonaws.com/rzp-mobile/images/rzp.png");
            options.put("theme.color", "#3399cc");
            options.put("currency", "INR");
            
            // Amount in paise (multiply by 100)
            double amt = Double.parseDouble(amount);
            options.put("amount", (int)(amt * 100)); 
            
            JSONObject preFill = new JSONObject();
            preFill.put("email", email);
            preFill.put("contact", contact);
            options.put("prefill", preFill);

            checkout.open(activity, options);
        } catch(Exception e) {
            Log.e(TAG, "Error in starting Razorpay Checkout", e);
            Toast.makeText(activity, "Error in payment: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
