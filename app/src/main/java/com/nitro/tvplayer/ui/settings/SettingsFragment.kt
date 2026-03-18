package com.nitro.tvplayer.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.nitro.tvplayer.databinding.FragmentSettingsBinding
import com.nitro.tvplayer.ui.login.LoginActivity
import com.nitro.tvplayer.utils.PlaybackPositionManager
import com.nitro.tvplayer.utils.PrefsManager
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject lateinit var prefs: PrefsManager
    @Inject lateinit var positionManager: PlaybackPositionManager

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAccountInfo()
        setupButtons()
    }

    private fun loadAccountInfo() {
        val userInfo = prefs.getUserInfo()
        binding.tvUsername.text    = userInfo?.username ?: "—"
        binding.tvServer.text      = prefs.getServerUrl().ifBlank { "—" }
        binding.tvStatus.text      = if (userInfo?.status == "Active") "Active" else (userInfo?.status ?: "Unknown")

        // Expiry date
        val expiry = userInfo?.expDate
        binding.tvExpiry.text = if (!expiry.isNullOrBlank()) {
            try {
                val ts   = expiry.toLongOrNull()
                val date = if (ts != null) Date(ts * 1000) else Date()
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
            } catch (e: Exception) { expiry }
        } else "—"

        binding.tvConnections.text = userInfo?.maxConnections ?: "—"

        // Continue Watching count
        val watchedCount = positionManager.getWatchedList().size
        binding.tvWatchedCount.text = "$watchedCount items"

        // App version from package manager (no BuildConfig needed)
        binding.tvAppVersion.text = try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) { "1.0" }
    }

    private fun setupButtons() {

        binding.btnClearWatching.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Continue Watching")
                .setMessage("Remove all saved progress?")
                .setPositiveButton("Clear") { _, _ ->
                    positionManager.clearAll()
                    binding.tvWatchedCount.text = "0 items"
                    Toast.makeText(requireContext(),
                        "Continue Watching cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnClearCache.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Cache")
                .setMessage("Content will reload on next launch.")
                .setPositiveButton("Clear") { _, _ ->
                    Toast.makeText(requireContext(),
                        "Cache will clear on next restart", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out") { _, _ ->
                    // Clear all saved credentials
                    prefs.clear()
                    startActivity(
                        Intent(requireContext(), LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
