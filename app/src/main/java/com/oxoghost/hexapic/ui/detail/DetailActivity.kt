package com.oxoghost.hexapic.ui.detail

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.ChangeImageTransform
import android.transition.ChangeTransform
import android.transition.TransitionSet
import android.view.View
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.oxoghost.hexapic.R
import com.oxoghost.hexapic.databinding.ActivityDetailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface DetailController {
    fun onToggleChrome()
    fun onDismissProgress(fraction: Float)
    fun onDismissCommit()
}

class DetailActivity : AppCompatActivity(), DetailController {

    private lateinit var binding: ActivityDetailBinding
    private val bgDrawable = ColorDrawable(0xFF000000.toInt())
    private var chromeVisible = true
    private val dateFormat = SimpleDateFormat("d MMMM yyyy · HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        // Shared-element enter transition (spring-feel curve, 280ms)
        val sharedTransition = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(ChangeBounds())
            addTransition(ChangeImageTransform())
            addTransition(ChangeTransform())
            duration = 280
            interpolator = FastOutSlowInInterpolator()
        }
        window.sharedElementEnterTransition  = sharedTransition
        window.sharedElementReturnTransition = sharedTransition

        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge, black background
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(bgDrawable)
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        postponeEnterTransition()

        setupPager()
        setupChrome()
        hideSystemBars()

        onBackPressedDispatcher.addCallback(this) { finishAfterTransition() }
        binding.btnBack.setOnClickListener { finishAfterTransition() }
        binding.btnShare.setOnClickListener { shareCurrentPhoto() }
        // btnFavorite, btnInfo, btnDelete are stubs
    }

    private fun setupPager() {
        val photos = DetailDataStore.photos
        binding.viewPager.adapter = object : FragmentStateAdapter(this as FragmentActivity) {
            override fun getItemCount() = photos.size
            override fun createFragment(position: Int): Fragment {
                val media = photos.getOrNull(position)
                return if (media?.isVideo == true) VideoDetailFragment.newInstance(position)
                       else PhotoDetailFragment.newInstance(position)
            }
        }
        binding.viewPager.setCurrentItem(DetailDataStore.startPosition, false)
        binding.viewPager.offscreenPageLimit = 1

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateChromeMeta(position)
                val media = photos.getOrNull(position)
                binding.btnFavorite.setImageResource(
                    if (media?.isFavorite == true) R.drawable.ic_favorite
                    else R.drawable.ic_favorite_outline
                )
                // Hide bottom action buttons for video pages (they have their own controls)
                binding.bottomChrome.visibility =
                    if (media?.isVideo == true) android.view.View.GONE
                    else android.view.View.VISIBLE
            }
        })

        updateChromeMeta(DetailDataStore.startPosition)
        val startMedia = photos.getOrNull(DetailDataStore.startPosition)
        binding.btnFavorite.setImageResource(
            if (startMedia?.isFavorite == true) R.drawable.ic_favorite
            else R.drawable.ic_favorite_outline
        )
        binding.bottomChrome.visibility =
            if (startMedia?.isVideo == true) android.view.View.GONE
            else android.view.View.VISIBLE
    }

    private fun updateChromeMeta(position: Int) {
        val media = DetailDataStore.photos.getOrNull(position) ?: return
        binding.tvDateTime.text = dateFormat.format(Date(media.dateAdded * 1000L))
    }

    private fun setupChrome() {
        binding.topChrome.alpha = 1f
        binding.bottomChrome.alpha = 1f
    }

    private fun shareCurrentPhoto() {
        val pos = binding.viewPager.currentItem
        val media = DetailDataStore.photos.getOrNull(pos) ?: return
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = media.mimeType
            putExtra(android.content.Intent.EXTRA_STREAM, media.uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, null))
    }

    // ── Controller ────────────────────────────────────────────────────────────

    override fun onToggleChrome() {
        chromeVisible = !chromeVisible
        val alpha = if (chromeVisible) 1f else 0f
        binding.topChrome.animate().alpha(alpha).setDuration(200).start()
        // Only animate bottomChrome if it is currently shown (not suppressed by a video page)
        if (binding.bottomChrome.visibility == View.VISIBLE) {
            binding.bottomChrome.animate().alpha(alpha).setDuration(200).start()
        }
        if (chromeVisible) showSystemBars() else hideSystemBars()
    }

    override fun onDismissProgress(fraction: Float) {
        bgDrawable.alpha = ((1f - fraction) * 255).toInt()
        val chromeAlpha = (1f - fraction * 3f).coerceIn(0f, 1f)
        binding.topChrome.alpha    = chromeAlpha
        binding.bottomChrome.alpha = chromeAlpha
    }

    override fun onDismissCommit() {
        showSystemBars()
        finishAfterTransition()
    }

    // ── System bars ───────────────────────────────────────────────────────────

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, binding.root).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun showSystemBars() {
        WindowInsetsControllerCompat(window, binding.root)
            .show(WindowInsetsCompat.Type.systemBars())
    }
}
