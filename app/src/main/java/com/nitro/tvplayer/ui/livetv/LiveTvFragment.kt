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

    private fun attachPreviewPlayer() {
        if (playerHolder.player == null) {
            playerHolder.player = ExoPlayer.Builder(requireContext()).build()
        }
        binding.previewPlayerView.player = playerHolder.player
        binding.previewPlayerView.useController = false

        // Returning from fullscreen — stream still playing, just show it
        if (playerHolder.isInFullscreen) {
            playerHolder.isInFullscreen = false
            if (playerHolder.currentUrl != null) showPreviewPlaying()
        }
    }

    private fun setupPreviewClickListener() {
        val goFullscreen: () -> Unit = goFullscreen@{
            val stream  = viewModel.selectedStream.value ?: return@goFullscreen
            val streams = viewModel.streams.value
            val index   = streams.indexOfFirst { it.streamId == stream.streamId }
            val urls    = ArrayList(streams.map { viewModel.buildStreamUrl(it.streamId) })
            val names   = ArrayList(streams.map { it.name })
            val sIds    = ArrayList(streams.map { it.streamId })

            playerHolder.isInFullscreen = true

            // ── Detach view → black box, stream keeps running in PlayerActivity
            binding.previewPlayerView.player = null

            startActivity(
                Intent(requireContext(), PlayerActivity::class.java).apply {
                    putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST, urls)
                    putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, names)
                    putIntegerArrayListExtra(PlayerActivity.EXTRA_STREAM_IDS, sIds)
                    putExtra(PlayerActivity.EXTRA_START_INDEX, index.coerceAtLeast(0))
                    putExtra(PlayerActivity.EXTRA_TYPE, "live")
                    putExtra(PlayerActivity.EXTRA_USE_EXISTING, true)
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
            if (binding.previewPlayerView.player == null) {
                binding.previewPlayerView.player = holder.player
            }
            holder.player?.play()
            showPreviewPlaying()
            return
        }
        holder.currentUrl = url
        holder.player?.apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
        showPreviewPlaying()
    }

    private fun showPreviewPlaying() {
        val b = _binding ?: return
        b.previewPlayerView.visible()
        b.ivChannelLogo.gone()
        b.tvClickToWatch.visible()
        b.tapHint.gone()
    }

    private fun setupAdapters() {
        categoryAdapter = CategoryAdapter { viewModel.filterByCategory(it) }
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
                    id = "live_${stream.streamId}", name = stream.name,
                    icon = stream.streamIcon, type = "live",
                    streamUrl = viewModel.buildStreamUrl(stream.streamId),
                    categoryId = stream.categoryId
                )
                val added = favouritesManager.toggle(favItem)
                Toast.makeText(requireContext(),
                    if (added) "⭐ \"${stream.name}\" added to Favourites"
                    else "\"${stream.name}\" removed from Favourites",
                    Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvStreams.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = streamAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { cats ->
                        _binding ?: return@collect
                        categoryAdapter.submitList(cats)
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
                        val b = _binding ?: return@collect
                        b.tvChannelName.text = stream.name
                        b.ivChannelLogo.loadUrl(stream.streamIcon)
                        b.tvLiveBadge.visible()
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
                        val b = _binding ?: return@collect
                        b.tvError.text = err; b.tvError.visible()
                    }
                }
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.search(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // ── Tab becomes visible → re-attach player and resume ──
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            // Switched to Movies/Series → PAUSE
            if (!playerHolder.isInFullscreen) {
                playerHolder.player?.pause()
            }
        } else {
            // Switched BACK to Live TV → RESUME
            if (binding.previewPlayerView.player == null && playerHolder.currentUrl != null) {
                binding.previewPlayerView.player = playerHolder.player
            }
            if (playerHolder.currentUrl != null) {
                playerHolder.player?.play()
                showPreviewPlaying()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (binding.previewPlayerView.player == null && playerHolder.currentUrl != null) {
            binding.previewPlayerView.player = playerHolder.player
        }
        if (!playerHolder.isInFullscreen && playerHolder.currentUrl != null) {
            playerHolder.player?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!playerHolder.isInFullscreen) {
            playerHolder.player?.pause()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.previewPlayerView?.player = null
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!playerHolder.isInFullscreen) {
            playerHolder.player?.release()
            playerHolder.clear()
        }
    }
}
