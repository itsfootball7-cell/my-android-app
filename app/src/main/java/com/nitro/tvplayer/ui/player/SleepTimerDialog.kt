package com.nitro.tvplayer.ui.player

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nitro.tvplayer.databinding.BottomSheetSleepTimerBinding

class SleepTimerDialog : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSleepTimerBinding? = null
    private val binding get() = _binding!!

    var onTimerSet: ((Long) -> Unit)? = null   // millis, 0 = cancel
    var onCancel:   (() -> Unit)?     = null

    companion object {
        const val TAG = "SleepTimer"
        fun newInstance() = SleepTimerDialog()

        // Minutes → millis helpers
        val OPTIONS_LABEL  = listOf("15 min", "30 min", "45 min", "60 min", "90 min", "End of episode")
        val OPTIONS_MILLIS = listOf(15L, 30L, 45L, 60L, 90L, -1L).map { it * 60_000 }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSleepTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btn15.setOnClickListener   { pick(15 * 60_000L) }
        binding.btn30.setOnClickListener   { pick(30 * 60_000L) }
        binding.btn45.setOnClickListener   { pick(45 * 60_000L) }
        binding.btn60.setOnClickListener   { pick(60 * 60_000L) }
        binding.btn90.setOnClickListener   { pick(90 * 60_000L) }
        binding.btnCancelTimer.setOnClickListener {
            onCancel?.invoke()
            dismiss()
        }
    }

    private fun pick(millis: Long) {
        onTimerSet?.invoke(millis)
        dismiss()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
