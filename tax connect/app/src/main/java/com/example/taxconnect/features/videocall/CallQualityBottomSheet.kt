package com.example.taxconnect.features.videocall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.taxconnect.R
import com.example.taxconnect.data.repositories.AnalyticsRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CallQualityBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_CHANNEL = "channel_name"

        fun newInstance(channelName: String): CallQualityBottomSheet {
            return CallQualityBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHANNEL, channelName)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_call_quality, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val channel = arguments?.getString(ARG_CHANNEL) ?: ""

        fun logAndDismiss(rating: Int) {
            AnalyticsRepository.getInstance()?.log(
                "call_quality_rating",
                hashMapOf<String, Any>(
                    "rating" to rating,
                    "channel" to channel
                )
            )
            dismiss()
        }

        view.findViewById<View>(R.id.btnQualityBad).setOnClickListener { logAndDismiss(1) }
        view.findViewById<View>(R.id.btnQualityOk).setOnClickListener { logAndDismiss(2) }
        view.findViewById<View>(R.id.btnQualityGood).setOnClickListener { logAndDismiss(3) }
        view.findViewById<View>(R.id.btnQualitySkip).setOnClickListener { dismiss() }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        activity?.finish()
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        activity?.finish()
    }
}
