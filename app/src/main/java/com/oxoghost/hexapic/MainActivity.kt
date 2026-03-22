package com.oxoghost.hexapic

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.oxoghost.hexapic.BuildConfig
import com.oxoghost.hexapic.databinding.ActivityMainBinding
import com.oxoghost.hexapic.ui.albums.AlbumsFragment
import com.oxoghost.hexapic.ui.foryou.ForYouFragment
import com.oxoghost.hexapic.ui.library.LibraryFragment
import com.oxoghost.hexapic.ui.search.SearchFragment
import com.oxoghost.hexapic.update.ReleaseInfo
import com.oxoghost.hexapic.update.UpdateChecker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Fragment instances kept alive across tab switches (no recreation)
    private val libraryFragment = LibraryFragment()
    private val forYouFragment = ForYouFragment()
    private val albumsFragment = AlbumsFragment()
    private val searchFragment = SearchFragment()

    private var activeFragment: Fragment = libraryFragment
    private var activeTabId: Int = R.id.nav_library

    private val tabFragments: Map<Int, Fragment> by lazy {
        mapOf(
            R.id.nav_library to libraryFragment,
            R.id.nav_for_you to forYouFragment,
            R.id.nav_albums to albumsFragment,
            R.id.nav_search to searchFragment,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyWindowInsets()

        setupFragments(savedInstanceState)
        setupBottomNav()
        checkForUpdates()
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Push fragment content below the status bar
            binding.fragmentContainer.setPadding(0, systemBars.top, 0, 0)
            // Set statusBarBg height to cover the status bar area on top
            binding.statusBarBg.layoutParams = binding.statusBarBg.layoutParams.also {
                it.height = systemBars.top
            }
            binding.bottomNav.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }

    private fun setupFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().apply {
                add(R.id.fragment_container, libraryFragment, TAG_LIBRARY)
                add(R.id.fragment_container, forYouFragment, TAG_FOR_YOU)
                add(R.id.fragment_container, albumsFragment, TAG_ALBUMS)
                add(R.id.fragment_container, searchFragment, TAG_SEARCH)
                hide(forYouFragment)
                hide(albumsFragment)
                hide(searchFragment)
            }.commitNow()
            activeFragment = libraryFragment
            activeTabId = R.id.nav_library
        } else {
            // Restore references after config change — fragments are already in the back stack
            val lib = supportFragmentManager.findFragmentByTag(TAG_LIBRARY) as? LibraryFragment
            val fy  = supportFragmentManager.findFragmentByTag(TAG_FOR_YOU) as? ForYouFragment
            val alb = supportFragmentManager.findFragmentByTag(TAG_ALBUMS) as? AlbumsFragment
            val src = supportFragmentManager.findFragmentByTag(TAG_SEARCH) as? SearchFragment
            // Use restored or fallback to our instances (they won't be added again)
            if (lib != null && fy != null && alb != null && src != null) {
                activeTabId = savedInstanceState.getInt(KEY_ACTIVE_TAB, R.id.nav_library)
                activeFragment = when (activeTabId) {
                    R.id.nav_for_you -> fy
                    R.id.nav_albums  -> alb
                    R.id.nav_search  -> src
                    else             -> lib
                }
            }
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = activeTabId

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == activeTabId) {
                // Tapping the active tab — scroll to top in Library, no-op elsewhere
                (activeFragment as? LibraryFragment)?.scrollToTop()
                return@setOnItemSelectedListener true
            }
            val target = tabFragments[item.itemId] ?: return@setOnItemSelectedListener false
            supportFragmentManager.beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commitNow()
            activeFragment = target
            activeTabId = item.itemId
            true
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────

    private fun checkForUpdates() {
        lifecycleScope.launch {
            val info = UpdateChecker.check(BuildConfig.VERSION_NAME) ?: return@launch
            showUpdateBanner(info)
        }
    }

    private fun showUpdateBanner(info: ReleaseInfo) {
        binding.tvUpdateVersion.text = getString(R.string.update_new_version, info.versionName)

        binding.bottomNav.doOnLayout { nav ->
            val params = binding.updateBanner.layoutParams as CoordinatorLayout.LayoutParams
            params.bottomMargin = nav.height
            binding.updateBanner.layoutParams = params

            binding.updateBanner.visibility = View.VISIBLE
            binding.updateBanner.translationY = nav.height.toFloat()
            binding.updateBanner.animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        binding.btnNotNow.setOnClickListener {
            binding.updateBanner.animate()
                .translationY(binding.updateBanner.height.toFloat())
                .setDuration(250)
                .withEndAction { binding.updateBanner.visibility = View.GONE }
                .start()
        }

        binding.btnUpdate.setOnClickListener {
            startDownload(info.apkUrl)
        }
    }

    private fun startDownload(url: String) {
        binding.btnUpdate.isEnabled = false
        binding.btnUpdate.text = getString(R.string.update_downloading)

        val request = DownloadManager.Request(url.toUri())
            .setTitle(getString(R.string.app_name))
            .setDescription(getString(R.string.update_downloading))
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(
                this,
                Environment.DIRECTORY_DOWNLOADS,
                "hexapic-update.apk"
            )

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == downloadId) {
                    unregisterReceiver(this)
                    installApk(downloadId)
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun installApk(downloadId: Long) {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(downloadId) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(intent)
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ACTIVE_TAB, activeTabId)
    }

    companion object {
        private const val TAG_LIBRARY  = "tag_library"
        private const val TAG_FOR_YOU  = "tag_for_you"
        private const val TAG_ALBUMS   = "tag_albums"
        private const val TAG_SEARCH   = "tag_search"
        private const val KEY_ACTIVE_TAB = "active_tab"
    }
}
