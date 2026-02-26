package com.example.taxconnect.features.documents

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.taxconnect.databinding.FragmentUploadDocumentBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class UploadDocumentBottomSheet(
    private val onCategorySelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: FragmentUploadDocumentBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadDocumentBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
    }

    private fun setupListeners() {
        binding.cardIdentity.setOnClickListener {
            onCategorySelected("Identity Proof")
            dismiss()
        }

        binding.cardFinancial.setOnClickListener {
            onCategorySelected("Financial Documents")
            dismiss()
        }

        binding.cardBusiness.setOnClickListener {
            onCategorySelected("Business Documents")
            dismiss()
        }

        binding.cardOther.setOnClickListener {
            onCategorySelected("Other")
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "UploadDocumentBottomSheet"
    }
}
