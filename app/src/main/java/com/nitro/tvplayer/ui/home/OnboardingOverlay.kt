package com.nitro.tvplayer.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nitro.tvplayer.databinding.BottomSheetOnboardingBinding

class OnboardingOverlay : BottomSheetDialogFragment() {

    private var _binding: BottomSheetOnboardingBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG   = "Onboarding"
        const val PREF  = "nitro_prefs"
        const val KEY   = "onboarding_done"

        fun shouldShow(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            return !prefs.getBoolean(KEY, false)
        }

        fun markDone(context: Context) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, true).apply()
        }

        fun newInstance() = OnboardingOverlay()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false
        binding.btnGotIt.setOnClickListener {
            markDone(requireContext())
            dismiss()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
