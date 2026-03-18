package com.nitro.tvplayer.ui.livetv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitro.tvplayer.databinding.FragmentLiveTvBinding
import com.nitro.tvplayer.ui.player.PlayerActivity
import com.nitro.tvplayer.utils.loadUrl
import com.nitro.tvplayer.utils.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LiveTvFragment : Fragment() {

    private var _binding: FragmentLiveTvBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LiveTvViewModel by viewModels()
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
        observeViewModel()
        setupSearch()
    }

    private fun setupAdapters() {
        categoryAdapter = CategoryAdapter { viewModel.filterByCategory(it.categoryId) }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }

        streamAdapter = LiveStreamAdapter { viewModel.selectStream(it) }
        binding.rvStreams.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = streamAdapter
        }

        binding.btnPlay.setOnClickListener {
            val streams  = viewModel.streams.value
            val selected = viewModel.selectedStream.value ?: return@setOnClickListener
            val index    = streams.indexOfFirst { it.streamId == selected.streamId }
            val urls     = ArrayList(streams.map { viewModel.buildStreamUrl(it.streamId) })
            val names    = ArrayList(streams.map { it.name })

            startActivity(
                Intent(requireContext(), PlayerActivity::class.java).apply {
                    putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST, urls)
                    putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, names)
                    putExtra(PlayerActivity.EXTRA_START_INDEX, index.coerceAtLeast(0))
                    putExtra(PlayerActivity.EXTRA_TYPE, "live")
                }
            )
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.categories.collect { categoryAdapter.submitList(it) }
        }
        lifecycleScope.launch {
            viewModel.streams.collect { streamAdapter.submitList(it) }
        }
        lifecycleScope.launch {
            viewModel.selectedStream.collect { stream ->
                stream?.let {
                    binding.tvChannelName.text = it.name
                    binding.ivChannelLogo.loadUrl(it.streamIcon)
                    binding.tvLiveBadge.visible()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.loading.collect {
                binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            viewModel.error.collect { err ->
                err?.let { binding.tvError.text = it; binding.tvError.visible() }
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

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
