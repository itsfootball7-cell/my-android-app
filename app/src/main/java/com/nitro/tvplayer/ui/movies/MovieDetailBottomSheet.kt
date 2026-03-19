package com.nitro.tvplayer.ui.movies

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nitro.tvplayer.data.model.VodStream
import com.nitro.tvplayer.databinding.BottomSheetMovieDetailBinding
import com.nitro.tvplayer.ui.player.PlayerActivity
import com.nitro.tvplayer.utils.FavouriteItem
import com.nitro.tvplayer.utils.FavouritesManager
import com.nitro.tvplayer.utils.PlaybackPositionManager
import com.nitro.tvplayer.utils.loadUrl
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MovieDetailBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: MoviesViewModel by activityViewModels()
    @Inject lateinit var favouritesManager: FavouritesManager
    @Inject lateinit var positionManager: PlaybackPositionManager

    private var _binding: BottomSheetMovieDetailBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "MovieDetail"
        fun newInstance() = MovieDetailBottomSheet()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMovieDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val movie = viewModel.selectedMovie.value ?: run { dismiss(); return }
        bindMovie(movie)
    }

    private fun bindMovie(movie: VodStream) {
        binding.ivPoster.loadUrl(movie.streamIcon)
        binding.tvTitle.text    = movie.name
        binding.tvReleaseDate.text = movie.releaseDate ?: "—"
        binding.tvGenre.text    = movie.genre ?: "—"
        binding.tvDirector.text = movie.director ?: "—"
        binding.tvCast.text     = movie.cast ?: "—"
        binding.tvPlot.text     = movie.plot ?: "No description available."

        // Duration
        val durationSecs = movie.durationSecs ?: 0
        binding.tvDuration.text = if (durationSecs > 0) {
            val h = durationSecs / 3600; val m = (durationSecs % 3600) / 60
            if (h > 0) "${h}h ${m}m" else "${m}m"
        } else "—"

        // Star rating
        val rating = movie.rating5 ?: movie.rating?.toDoubleOrNull() ?: 0.0
        binding.ratingBar.rating = rating.toFloat() / 2f   // 10-scale → 5 stars
        binding.tvRatingNumber.text = if (rating > 0) "%.1f".format(rating) else "—"

        // Favourite toggle
        val contentId  = "movie_${movie.streamId}"
        val isFav      = favouritesManager.isFavourite(contentId)
        binding.btnFavourite.text = if (isFav) "❤️" else "🤍"
        binding.btnFavourite.setOnClickListener {
            val url   = viewModel.buildStreamUrl(movie.streamId, movie.extension ?: "mp4")
            val added = favouritesManager.toggle(FavouriteItem(
                id = contentId, name = movie.name, icon = movie.streamIcon,
                type = "movie", streamUrl = url, categoryId = movie.categoryId,
                extra = movie.extension
            ))
            binding.btnFavourite.text = if (added) "❤️" else "🤍"
        }

        // Play button — with resume dialog logic
        binding.btnPlay.setOnClickListener {
            val url       = viewModel.buildStreamUrl(movie.streamId, movie.extension ?: "mp4")
            val savedPos  = positionManager.getSavedPosition(contentId)
            if (savedPos > 0L) {
                showResumeDialog(movie, url, contentId, savedPos)
            } else {
                launchPlayer(url, movie.name, contentId, movie.streamIcon)
            }
        }

        // Watch Trailer button
        val trailer = movie.youtubeTrailer
        if (!trailer.isNullOrBlank()) {
            binding.btnTrailer.visibility = View.VISIBLE
            binding.btnTrailer.setOnClickListener {
                val youtubeUrl = "https://www.youtube.com/watch?v=$trailer"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl)))
            }
        } else {
            binding.btnTrailer.visibility = View.GONE
        }

        // Progress bar for continue watching
        val progress = positionManager.getProgressPercent(contentId)
        if (progress > 0) {
            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.progress   = progress
            binding.tvResumeFrom.visibility = View.VISIBLE
            val savedMs  = positionManager.getSavedPosition(contentId)
            binding.tvResumeFrom.text = "Resume from ${formatTime(savedMs)}"
        } else {
            binding.progressBar.visibility  = View.GONE
            binding.tvResumeFrom.visibility = View.GONE
        }
    }

    private fun showResumeDialog(
        movie: VodStream, url: String, contentId: String, savedPos: Long
    ) {
        val duration = positionManager.getWatchedList()
            .find { it.contentId == contentId }?.durationMs ?: 0L
        val timeStr  = formatTime(savedPos)
        val totalStr = if (duration > 0) " / ${formatTime(duration)}" else ""
        val pct      = if (duration > 0) ((savedPos * 100f) / duration).toInt() else 0

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Resume \"${movie.name}\"?")
            .setMessage("You were at $timeStr$totalStr ($pct% watched)\n\nContinue from where you left off?")
            .setPositiveButton("▶ Resume")    { _, _ -> launchPlayer(url, movie.name, contentId, movie.streamIcon) }
            .setNegativeButton("↺ Start Over") { _, _ ->
                positionManager.clearPosition(contentId)
                launchPlayer(url, movie.name, contentId, movie.streamIcon)
            }
            .setCancelable(true)
            .show()
    }

    private fun launchPlayer(url: String, title: String, contentId: String, icon: String?) {
        dismiss()
        startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URL,   url)
            putExtra(PlayerActivity.EXTRA_TITLE, title)
            putExtra(PlayerActivity.EXTRA_TYPE,  "movie")
            putStringArrayListExtra(PlayerActivity.EXTRA_IDS,   arrayListOf(contentId))
            putStringArrayListExtra(PlayerActivity.EXTRA_ICONS, arrayListOf(icon ?: ""))
        })
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
