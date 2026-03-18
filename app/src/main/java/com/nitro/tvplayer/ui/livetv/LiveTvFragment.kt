package com.nitro.tvplayer.ui.livetv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nitro.tvplayer.databinding.FragmentLiveTvBinding
import com.nitro.tvplayer.ui.player.PlayerActivity
import com.nitro.tvplayer.utils.loadUrl
import com.nitro.tvplayer.utils.visible
import com.nitro.tvplayer.utils.gone
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LiveTvFragment : Fragment() {

    // ── Use activityViewModels so ViewModel survives tab switches ──
    private val viewModel: LiveTvViewModel by activityViewModels()

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
        // ── repeatOnLifecycle cancels collection when fragment is STOPPED
        //    and restarts when STARTED — prevents null binding crash ──
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.categories.collect { cats ->
                        _binding?.rvCategories?.let {
                            categoryAdapter.submitList(cats)
                        }
                    }
                }

                launch {
                    viewModel.streams.collect { streams ->
                        _binding?.rvStreams?.let {
                            streamAdapter.submitList(streams)
                        }
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
                        _binding?.tvError?.let { tv ->
                            tv.text = err
                            tv.visible()
                        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null  // must null out to prevent memory leak
    }
}
