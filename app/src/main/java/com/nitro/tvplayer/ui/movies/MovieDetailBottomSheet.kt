package com.nitro.tvplayer.ui.movies

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
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
        binding.ivPoster.loadUrl(movie.streamIcon, movie.name)
        binding.tvTitle.text       = movie.name
        binding.tvReleaseDate.text = movie.releaseDate ?: "—"
        binding.tvGenre.text       = movie.genre ?: "—"
        binding.tvDirector.text    = movie.director ?: "—"
        binding.tvCast.text        = movie.cast ?: "—"
        binding.tvPlot.text        = movie.plot ?: "No description available."

        // Duration — VodStream doesn't have durationSecs directly,
        // so we just show "—" as the API only returns it in movie info calls
        binding.tvDuration.text = "—"

        // Star rating (rating5 is 0–10, RatingBar is 0–5)
        val rating = movie.rating5 ?: movie.rating?.toDoubleOrNull() ?: 0.0
        binding.ratingBar.rating     = (rating / 2f).toFloat()
        binding.tvRatingNumber.text  = if (rating > 0) "%.1f".format(rating) else "—"

        // Favourite toggle
        val contentId = "movie_${movie.streamId}"
        binding.btnFavourite.text = if (favouritesManager.isFavourite(contentId)) "❤️" else "🤍"
        binding.btnFavourite.setOnClickListener {
            val url   = viewModel.buildStreamUrl(movie.streamId, movie.extension ?: "mp4")
            val added = favouritesManager.toggle(FavouriteItem(
                id = contentId, name = movie.name, icon = movie.streamIcon,
                type = "movie", streamUrl = url, categoryId = movie.categoryId,
                extra = movie.extension
            ))
            binding.btnFavourite.text = if (added) "❤️" else "🤍"
        }

        // Continue watching progress
        val progress = positionManager.getProgressPercent(contentId)
        if (progress > 0) {
            binding.progressBar.visibility  = View.VISIBLE
            binding.progressBar.progress    = progress
            binding.tvResumeFrom.visibility = View.VISIBLE
            val savedMs = positionManager.getSavedPosition(contentId)
            binding.tvResumeFrom.text = "Resume from ${formatTime(savedMs)}"
        } else {
            binding.progressBar.visibility  = View.GONE
            binding.tvResumeFrom.visibility = View.GONE
        }

        // Play button
        binding.btnPlay.setOnClickListener {
            val url      = viewModel.buildStreamUrl(movie.streamId, movie.extension ?: "mp4")
            val savedPos = positionManager.getSavedPosition(contentId)
            if (savedPos > 0L) {
                showResumeDialog(movie, url, contentId, savedPos)
            } else {
                launchPlayer(url, movie.name, contentId, movie.streamIcon)
            }
        }

        // Watch Trailer
        val trailer = movie.youtubeTrailer
        if (!trailer.isNullOrBlank()) {
            binding.btnTrailer.visibility = View.VISIBLE
            binding.btnTrailer.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/watch?v=$trailer")))
            }
        } else {
            binding.btnTrailer.visibility = View.GONE
        }
    }

    private fun showResumeDialog(
        movie: VodStream, url: String, contentId: String, savedPos: Long
    ) {
        val duration = positionManager.getWatchedList()
            .find { it.contentId == contentId }?.durationMs ?: 0L
        val pct = if (duration > 0) ((savedPos * 100f) / duration).toInt() else 0
        AlertDialog.Builder(requireContext())
            .setTitle("Resume \"${movie.name}\"?")
            .setMessage("You were at ${formatTime(savedPos)}${
                if (duration > 0) " / ${formatTime(duration)}" else ""
            } ($pct% watched)\n\nContinue from where you left off?")
            .setPositiveButton("▶ Resume")     { _, _ -> launchPlayer(url, movie.name, contentId, movie.streamIcon) }
            .setNegativeButton("↺ Start Over") { _, _ ->
                positionManager.clearPosition(contentId)
                launchPlayer(url, movie.name, contentId, movie.streamIcon)
            }
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
