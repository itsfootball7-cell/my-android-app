package com.nitro.tvplayer.ui.livetv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitro.tvplayer.databinding.FragmentLiveTvBinding
import com.nitro.tvplayer.ui.player.PlayerActivity
import com.nitro.tvplayer.utils.FavouriteItem
import com.nitro.tvplayer.utils.FavouritesManager
import com.nitro.tvplayer.utils.PlayerHolder
import com.nitro.tvplayer.utils.loadUrl
import com.nitro.tvplayer.utils.visible
import com.nitro.tvplayer.utils.gone
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LiveTvFragment : Fragment() {

    private val viewModel: LiveTvViewModel by activityViewModels()
    @Inject lateinit var favouritesManager: FavouritesManager
    @Inject lateinit var playerHolder: PlayerHolder

    private var _binding: FragmentLiveTvBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var streamAdapter: LiveStreamAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLiveTvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        attachPreviewPlayer()
        setupPreviewClickListener()
        observeViewModel()
        setupSearch()
    }

    // ─── Attach shared player to mini preview view ────────────
    private fun attachPreviewPlayer() {
        val holder = playerHolder

        if (holder.player == null) {
            // Create new player and store in holder
            holder.player = ExoPlayer.Builder(requireContext()).build()
        }

        // Attach to preview view — no reload if same stream
        binding.previewPlayerView.player = holder.player
        binding.previewPlayerView.useController = false

        // If returning from fullscreen, stream is already playing — just show it
        if (holder.isInFullscreen) {
            holder.isInFullscreen = false
            holder.currentUrl?.let { showPreviewPlaying() }
        }
    }

    private fun setupPreviewClickListener() {
        // ── Tap preview box → go fullscreen WITHOUT stopping stream ──
        val goFullscreen = {
            val stream = viewModel.selectedStream.value ?: return@let
            val streams = viewModel.streams.value
            val index   = streams.indexOfFirst { it.streamId == stream.streamId }
            val urls    = ArrayList(streams.map { viewModel.buildStreamUrl(it.streamId) })
            val names   = ArrayList(streams.map { it.name })

            // Mark player as going to fullscreen — DON'T release it
            playerHolder.isInFullscreen = true

            // Detach from preview before handing to PlayerActivity
            binding.previewPlayerView.player = null

            startActivity(
                Intent(requireContext(), PlayerActivity::class.java).apply {
                    putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST, urls)
                    putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, names)
                    putExtra(PlayerActivity.EXTRA_START_INDEX, index.coerceAtLeast(0))
                    putExtra(PlayerActivity.EXTRA_TYPE, "live")
                    // Signal to PlayerActivity to use existing player
                    putExtra("use_existing_player", true)
                }
            )
        }

        binding.previewPlayerView.setOnClickListener { goFullscreen() }
        binding.previewContainer.setOnClickListener {
            if (playerHolder.currentUrl != null) goFullscreen()
        }
    }

    private fun playInPreview(url: String) {
        val holder = playerHolder
        if (holder.currentUrl == url) {
            // Already playing this stream — just make sure view is attached
            if (binding.previewPlayerView.player == null) {
                binding.previewPlayerView.player = holder.player
            }
            showPreviewPlaying()
            return
        }

        // New stream — set media on existing player
        holder.currentUrl = url
        holder.player?.apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
        showPreviewPlaying()
    }

    private fun showPreviewPlaying() {
        _binding?.let { b ->
            b.previewPlayerView.visible()
            b.ivChannelLogo.gone()
            b.tvClickToWatch.visible()
            b.tapHint.gone()
        }
    }

    // ─── Adapters ─────────────────────────────────────────────
    private fun setupAdapters() {
        categoryAdapter = CategoryAdapter { category ->
            viewModel.filterByCategory(category)
        }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }

        streamAdapter = LiveStreamAdapter(
            onClick = { stream ->
                viewModel.selectStream(stream)
                playInPreview(viewModel.buildStreamUrl(stream.streamId))
            },
            onLongPress = { stream ->
                val favItem = FavouriteItem(
                    id         = "live_${stream.streamId}",
                    name       = stream.name,
                    icon       = stream.streamIcon,
                    type       = "live",
                    streamUrl  = viewModel.buildStreamUrl(stream.streamId),
                    categoryId = stream.categoryId
                )
                val added = favouritesManager.toggle(favItem)
                val msg = if (added)
                    "⭐ \"${stream.name}\" added to Favourites"
                else
                    "\"${stream.name}\" removed from Favourites"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvStreams.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = streamAdapter
        }
    }

    // ─── Observe ──────────────────────────────────────────────
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { cats ->
                        _binding ?: return@collect
                        categoryAdapter.submitList(cats)
                        // Auto-select first real category (index 2 after All + Favourites)
                        if (cats.size > 2) categoryAdapter.setSelected(2)
                        else if (cats.isNotEmpty()) categoryAdapter.setSelected(0)
                    }
                }
                launch {
                    viewModel.streams.collect { streams ->
                        _binding ?: return@collect
                        streamAdapter.submitList(streams)
                    }
                }
                launch {
                    viewModel.selectedStream.collect { stream ->
                        stream ?: return@collect
                        _binding ?: return@collect
                        binding.tvChannelName.text = stream.name
                        binding.ivChannelLogo.loadUrl(stream.streamIcon)
                        binding.tvLiveBadge.visible()
                    }
                }
                launch {
                    viewModel.loading.collect { loading ->
                        _binding?.progressBar?.visibility =
                            if (loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.error.collect { err ->
                        err ?: return@collect
                        _binding?.tvError?.let { tv -> tv.text = err; tv.visible() }
                    }
                }
            }
        }
    }

    // ─── Search ───────────────────────────────────────────────
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.search(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // ─── Lifecycle ────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        // Re-attach player if returning from fullscreen
        if (binding.previewPlayerView.player == null) {
            binding.previewPlayerView.player = playerHolder.player
        }
        playerHolder.player?.play()
    }

    override fun onPause() {
        super.onPause()
        // Only pause if NOT going to fullscreen
        if (!playerHolder.isInFullscreen) {
            playerHolder.player?.pause()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Detach view but keep player alive in holder
        _binding?.previewPlayerView?.player = null
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only release player when fragment is truly destroyed
        if (!playerHolder.isInFullscreen) {
            playerHolder.player?.release()
            playerHolder.clear()
        }
    }
}
