package com.example.taxconnect.data.services

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PresenceManager {

    companion object {
        private const val TAG = "PresenceManager"
        @Volatile
        private var instance: PresenceManager? = null

        fun getInstance(): PresenceManager {
            return instance ?: synchronized(this) {
                instance ?: PresenceManager().also { instance = it }
            }
        }
    }

    fun setupPresence() {
        val uid = FirebaseAuth.getInstance().uid
        if (uid == null) {
            Log.d(TAG, "No user logged in, skipping presence setup")
            return
        }

        val database = FirebaseDatabase.getInstance()
        val connectedRef = database.getReference(".info/connected")
        val presenceRef = database.getReference("status/$uid/state")

        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    // We are connected. 
                    // Set up the disconnect operation to aggressively mark the CA as offline.
                    presenceRef.onDisconnect().setValue("offline").addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "onDisconnect operation queued successfully for user $uid")
                            // We don't automatically mark them 'online' in Firestore here, 
                            // because CAs must intentionally toggle "Go Online" in the CADashboardActivity.
                            // We only want to ensure they drop offline if the connection dies.
                        } else {
                            Log.e(TAG, "Failed to queue onDisconnect operation", task.exception)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Listener was cancelled at .info/connected", error.toException())
            }
        })
    }
}
