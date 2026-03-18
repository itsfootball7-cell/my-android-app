package com.nitro.tvplayer.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nitro.tvplayer.databinding.FragmentSettingsBinding
import com.nitro.tvplayer.ui.login.LoginActivity
import com.nitro.tvplayer.utils.PrefsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    @Inject lateinit var prefs: PrefsManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userInfo = prefs.getUserInfo()
        binding.tvUsername.text   = userInfo?.username ?: "N/A"
        binding.tvServer.text     = prefs.getServerUrl()
        binding.tvStatus.text     = userInfo?.status ?: "N/A"
        binding.tvExpiry.text     = userInfo?.expDate ?: "N/A"
        binding.tvMaxConn.text    = userInfo?.maxConnections ?: "N/A"
        binding.tvActiveCons.text = userInfo?.activeCons ?: "N/A"
        binding.tvAppVersion.text = "Nitro TV Player v1.0.0"

        binding.btnLogout.setOnClickListener {
            prefs.clear()
            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            requireActivity().finish()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
