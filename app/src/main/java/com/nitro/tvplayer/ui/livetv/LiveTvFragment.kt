package com.nitro.tvplayer.ui.livetv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
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
    private var searchVisible = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLiveTvBinding.inflate(inflater, container, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        initPlayer()
        setupPreviewClick()
        observeViewModel()
        setupSearch()
    }

    // ── Search icon toggle ───────────────────────────────────
    private fun setupSearch() {
        // Tap 🔍 icon → show/hide search bar
        binding.btnSearch.setOnClickListener {
            searchVisible = !searchVisible
            if (searchVisible) {
                binding.searchBarContainer.visible()
                binding.etSearch.requestFocus()
                showKeyboard(binding.etSearch)
            } else {
                collapseSearch()
            }
        }

        // X button → clear and collapse
        binding.btnClearSearch.setOnClickListener {
            collapseSearch()
        }

        // IME "Search" key or text change → filter
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search(binding.etSearch.text.toString())
                hideKeyboard()
                true
            } else false
        }

        // Live filter as you type
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.search(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun collapseSearch() {
        searchVisible = false
        binding.etSearch.setText("")
        binding.searchBarContainer.gone()
        viewModel.search("")
        hideKeyboard()
    }

    private fun showKeyboard(view: View) {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    // ── Player setup ─────────────────────────────────────────
    private fun initPlayer() {
        if (playerHolder.player == null)
            playerHolder.player = ExoPlayer.Builder(requireContext()).build()
        if (!playerHolder.isInPip) binding.previewPlayerView.player = playerHolder.player
        else binding.previewPlayerView.player = null
        binding.previewPlayerView.useController = false
        if (playerHolder.isInFullscreen && !playerHolder.isInPip) {
            playerHolder.isInFullscreen = false
            binding.previewPlayerView.player = playerHolder.player
            if (playerHolder.currentUrl != null) showPreviewActive()
        }
    }

    private fun setupPreviewClick() {
        val launch: () -> Unit = launch@{
            val stream    = viewModel.selectedStream.value ?: return@launch
            val streams   = viewModel.streams.value
            val index     = streams.indexOfFirst { it.streamId == stream.streamId }
            val urls      = ArrayList(streams.map { viewModel.buildStreamUrl(it.streamId) })
            val names     = ArrayList(streams.map { it.name })
            val sIds      = ArrayList(streams.map { it.streamId })
            val iconsList = ArrayList(streams.map { it.streamIcon ?: "" })
            playerHolder.isInFullscreen = true
            binding.previewPlayerView.player = null
            startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST, urls)
                putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, names)
                putIntegerArrayListExtra(PlayerActivity.EXTRA_STREAM_IDS, sIds)
                putStringArrayListExtra(PlayerActivity.EXTRA_ICONS, iconsList)
                putExtra(PlayerActivity.EXTRA_START_INDEX, index.coerceAtLeast(0))
                putExtra(PlayerActivity.EXTRA_TYPE, "live")
                putExtra(PlayerActivity.EXTRA_USE_EXISTING, true)
            })
        }
        binding.previewPlayerView.setOnClickListener { launch() }
        binding.previewContainer.setOnClickListener { if (playerHolder.currentUrl != null) launch() }
    }

    private fun playInPreview(url: String) {
        if (playerHolder.isInPip) return
        if (playerHolder.currentUrl == url) {
            if (binding.previewPlayerView.player == null)
                binding.previewPlayerView.player = playerHolder.player
            playerHolder.player?.play(); showPreviewActive(); return
        }
        playerHolder.currentUrl = url
        playerHolder.player?.apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url))); prepare(); playWhenReady = true
        }
        if (binding.previewPlayerView.player == null)
            binding.previewPlayerView.player = playerHolder.player
        showPreviewActive()
    }

    private fun showPreviewActive() {
        val b = _binding ?: return
        b.previewPlayerView.visible(); b.tapHint.gone(); b.tvClickToWatch.visible()
    }

    private fun setupAdapters() {
        categoryAdapter = CategoryAdapter { viewModel.filterByCategory(it) }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext()); adapter = categoryAdapter
        }
        streamAdapter = LiveStreamAdapter(
            onClick = { stream ->
                viewModel.selectStream(stream)
                playerHolder.currentChannelIcon = stream.streamIcon
                playInPreview(viewModel.buildStreamUrl(stream.streamId))
            },
            onLongPress = { stream ->
                val added = favouritesManager.toggle(FavouriteItem(
                    id = "live_${stream.streamId}", name = stream.name, icon = stream.streamIcon,
                    type = "live", streamUrl = viewModel.buildStreamUrl(stream.streamId),
                    categoryId = stream.categoryId))
                Toast.makeText(requireContext(),
                    if (added) "⭐ Added to Favourites" else "Removed from Favourites",
                    Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvStreams.apply {
            layoutManager = LinearLayoutManager(requireContext()); adapter = streamAdapter
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
                launch { viewModel.streams.collect { s -> _binding?.let { streamAdapter.submitList(s) } } }
                launch {
                    viewModel.selectedStream.collect { stream ->
                        stream ?: return@collect
                        val b = _binding ?: return@collect
                        b.tvChannelName.text = stream.name
                        b.ivChannelLogo.loadUrl(stream.streamIcon, stream.name)
                        b.tvLiveBadge.visible()
                    }
                }
                launch {
                    viewModel.loading.collect { loading ->
                        _binding?.progressBar?.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            collapseSearch()
            if (!playerHolder.isInPip) { binding.previewPlayerView.player = null; playerHolder.player?.stop() }
            else binding.previewPlayerView.player = null
        } else {
            if (!playerHolder.isInPip && playerHolder.currentUrl != null) {
                if (playerHolder.player == null) playerHolder.player = ExoPlayer.Builder(requireContext()).build()
                binding.previewPlayerView.player = playerHolder.player
                playerHolder.player?.apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(playerHolder.currentUrl!!))); prepare(); playWhenReady = true
                }
                showPreviewActive()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!playerHolder.isInPip && !playerHolder.isInFullscreen &&
            playerHolder.currentUrl != null && playerHolder.player != null) {
            if (binding.previewPlayerView.player == null)
                binding.previewPlayerView.player = playerHolder.player
            playerHolder.player?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!playerHolder.isInFullscreen && !playerHolder.isInPip) playerHolder.player?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView(); binding.previewPlayerView.player = null; _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!playerHolder.isInFullscreen && !playerHolder.isInPip) {
            playerHolder.player?.release(); playerHolder.clear()
        }
    }
}
