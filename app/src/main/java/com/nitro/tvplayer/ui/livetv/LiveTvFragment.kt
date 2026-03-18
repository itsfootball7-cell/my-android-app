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

    private var _binding: FragmentLiveTvBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var streamAdapter: LiveStreamAdapter

    private var previewPlayer: ExoPlayer? = null
    private var currentPreviewUrl: String? = null

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
        setupPreviewPlayer()
        observeViewModel()
        setupSearch()
    }

    private fun setupPreviewPlayer() {
        previewPlayer = ExoPlayer.Builder(requireContext()).build().also { exo ->
            binding.previewPlayerView.player = exo
            binding.previewPlayerView.useController = false
        }
        binding.previewPlayerView.setOnClickListener {
            val stream = viewModel.selectedStream.value ?: return@setOnClickListener
            openFullscreen(stream)
        }
        binding.previewContainer.setOnClickListener {
            val stream = viewModel.selectedStream.value ?: return@setOnClickListener
            if (currentPreviewUrl != null) openFullscreen(stream)
        }
    }

    private fun playInPreview(url: String) {
        if (currentPreviewUrl == url) return
        currentPreviewUrl = url
        previewPlayer?.apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
        binding.previewPlayerView.visible()
        binding.ivChannelLogo.gone()
        binding.tvClickToWatch.visible()
        binding.tapHint.gone()
    }

    private fun openFullscreen(stream: com.nitro.tvplayer.data.model.LiveStream) {
        val streams = viewModel.streams.value
        val index   = streams.indexOfFirst { it.streamId == stream.streamId }
        val urls    = ArrayList(streams.map { viewModel.buildStreamUrl(it.streamId) })
        val names   = ArrayList(streams.map { it.name })
        previewPlayer?.pause()
        startActivity(
            Intent(requireContext(), PlayerActivity::class.java).apply {
                putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST, urls)
                putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, names)
                putExtra(PlayerActivity.EXTRA_START_INDEX, index.coerceAtLeast(0))
                putExtra(PlayerActivity.EXTRA_TYPE, "live")
            }
        )
    }

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
                val url = viewModel.buildStreamUrl(stream.streamId)
                playInPreview(url)
            },
            onLongPress = { stream ->
                // ── Long press → Add to Favourites ──
                val favItem = FavouriteItem(
                    id        = "live_${stream.streamId}",
                    name      = stream.name,
                    icon      = stream.streamIcon,
                    type      = "live",
                    streamUrl = viewModel.buildStreamUrl(stream.streamId),
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

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { cats ->
                        _binding ?: return@collect
                        categoryAdapter.submitList(cats)
                        if (cats.isNotEmpty()) categoryAdapter.setSelected(0)
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
                        _binding?.let { b ->
                            b.tvChannelName.text = stream.name
                            b.ivChannelLogo.loadUrl(stream.streamIcon)
                            b.tvLiveBadge.visible()
                        }
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

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.search(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onResume() { super.onResume(); currentPreviewUrl?.let { previewPlayer?.play() } }
    override fun onPause()  { super.onPause();  previewPlayer?.pause() }

    override fun onDestroyView() {
        super.onDestroyView()
        previewPlayer?.release()
        previewPlayer     = null
        currentPreviewUrl = null
        _binding = null
    }
}
