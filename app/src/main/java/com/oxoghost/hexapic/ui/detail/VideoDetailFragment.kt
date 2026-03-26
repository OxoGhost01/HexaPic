package com.oxoghost.hexapic.ui.detail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.oxoghost.hexapic.R
import com.oxoghost.hexapic.databinding.FragmentVideoDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class VideoDetailFragment : Fragment() {

    private var _binding: FragmentVideoDetailBinding? = null
    private val binding get() = _binding!!

    private var controller: DetailController? = null
    private var player: ExoPlayer? = null

    private val position by lazy { arguments?.getInt(ARG_POSITION) ?: 0 }
    private val media get() = DetailDataStore.photos.getOrNull(position)

    private var isMuted = false
    private var controlsVisible = true
    private var isDragging = false
    private var retrieverReady = false
    private val retriever = MediaMetadataRetriever()
    private var thumbJob: Job? = null

    // ── Progress updater ──────────────────────────────────────────────────────

    private val progressRunnable = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (!isDragging) {
                val pos = p.currentPosition
                binding.seekBar.progress = pos.toInt()
                binding.tvPosition.text = formatDuration(pos)
            }
            binding.seekBar.postDelayed(this, 500)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onAttach(context: Context) {
        super.onAttach(context)
        controller = context as? DetailController
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentVideoDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val item = media ?: run {
            if (position == DetailDataStore.startPosition) {
                requireActivity().startPostponedEnterTransition()
            }
            return
        }

        setupPlayer(item)
        setupControls()

        // Tapping the video area toggles chrome visibility + our own overlay
        binding.tapInterceptor.setOnClickListener {
            controlsVisible = !controlsVisible
            setControlsVisible(controlsVisible)
            controller?.onToggleChrome()
        }

        // Videos have no shared-element image — unblock the transition immediately
        if (position == DetailDataStore.startPosition) {
            requireActivity().startPostponedEnterTransition()
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDetach() {
        super.onDetach()
        controller = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.seekBar.removeCallbacks(progressRunnable)
        thumbJob?.cancel()
        @Suppress("NewApi")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { retriever.release() } catch (_: Exception) {}
        }
        player?.release()
        player = null
        _binding = null
    }

    // ── Player setup ──────────────────────────────────────────────────────────

    private fun setupPlayer(item: com.oxoghost.hexapic.data.MediaItem) {
        val exo = ExoPlayer.Builder(requireContext()).build()
        player = exo
        binding.playerView.player = exo

        exo.setMediaItem(MediaItem.fromUri(item.uri))
        exo.repeatMode = Player.REPEAT_MODE_OFF
        exo.prepare()
        // No autoplay

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val dur = exo.duration.coerceAtLeast(0L)
                    binding.seekBar.max = dur.toInt()
                    binding.tvDuration.text = formatDuration(dur)

                    // Prime the retriever for seek thumbnails on a background thread
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            retriever.setDataSource(requireContext(), item.uri)
                            retrieverReady = true
                        } catch (_: Exception) {}
                    }
                }
                if (state == Player.STATE_ENDED) {
                    exo.seekTo(0)
                    exo.pause()
                    updatePlayPauseIcon(false)
                    stopProgressUpdates()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseIcon(isPlaying)
                if (isPlaying) startProgressUpdates() else stopProgressUpdates()
            }
        })
    }

    // ── Controls setup ────────────────────────────────────────────────────────

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener { togglePlayback() }
        binding.btnMute.setOnClickListener { toggleMute() }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar) {
                isDragging = true
                binding.seekThumbnailContainer.isVisible = true
            }

            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvPosition.text = formatDuration(progress.toLong())
                    updateThumbnailPosition(sb, progress)
                    loadSeekThumbnail(progress.toLong())
                }
            }

            override fun onStopTrackingTouch(sb: SeekBar) {
                isDragging = false
                binding.seekThumbnailContainer.isVisible = false
                player?.seekTo(sb.progress.toLong())
            }
        })

        updatePlayPauseIcon(false)
        setMuteIcon(false)
    }

    // ── Playback control ──────────────────────────────────────────────────────

    private fun togglePlayback() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    private fun toggleMute() {
        isMuted = !isMuted
        player?.volume = if (isMuted) 0f else 1f
        setMuteIcon(isMuted)
    }

    private fun updatePlayPauseIcon(playing: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause_large else R.drawable.ic_play_large
        )
    }

    private fun setMuteIcon(muted: Boolean) {
        binding.btnMute.setImageResource(
            if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
        )
    }

    private fun setControlsVisible(visible: Boolean) {
        val alpha = if (visible) 1f else 0f
        binding.btnPlayPause.animate().alpha(alpha).setDuration(200).start()
        binding.videoControls.animate().alpha(alpha).setDuration(200).start()
    }

    // ── Progress updates ──────────────────────────────────────────────────────

    private fun startProgressUpdates() {
        binding.seekBar.removeCallbacks(progressRunnable)
        binding.seekBar.post(progressRunnable)
    }

    private fun stopProgressUpdates() {
        binding.seekBar.removeCallbacks(progressRunnable)
    }

    // ── Seek thumbnail ────────────────────────────────────────────────────────

    private fun updateThumbnailPosition(seekBar: SeekBar, progress: Int) {
        val ratio = if (seekBar.max > 0) progress.toFloat() / seekBar.max.toFloat() else 0f
        val trackWidth = (seekBar.width - seekBar.paddingLeft - seekBar.paddingRight).toFloat()
        val thumbCenterX = seekBar.x + seekBar.paddingLeft + trackWidth * ratio
        val containerW = binding.seekThumbnailContainer.width.toFloat()
        val maxX = (binding.videoRoot.width - containerW).coerceAtLeast(0f)
        binding.seekThumbnailContainer.translationX = (thumbCenterX - containerW / 2f).coerceIn(0f, maxX)
    }

    private fun loadSeekThumbnail(positionMs: Long) {
        if (!retrieverReady) return
        thumbJob?.cancel()
        thumbJob = lifecycleScope.launch {
            val bmp: Bitmap? = withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            positionMs * 1000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            120.dpToPx(), 68.dpToPx()
                        )
                    } else {
                        retriever.getFrameAtTime(
                            positionMs * 1000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                    }
                } catch (_: Exception) { null }
            }
            val b = _binding ?: return@launch
            if (bmp != null) b.ivSeekThumbnail.setImageBitmap(bmp)
        }
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    // ── Duration formatting ───────────────────────────────────────────────────

    private fun formatDuration(ms: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            val mins = minutes % 60
            "%d:%02d:%02d".format(hours, mins, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    companion object {
        private const val ARG_POSITION = "position"

        fun newInstance(position: Int) = VideoDetailFragment().apply {
            arguments = Bundle().apply { putInt(ARG_POSITION, position) }
        }
    }
}
