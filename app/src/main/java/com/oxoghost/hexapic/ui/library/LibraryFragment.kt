package com.oxoghost.hexapic.ui.library

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.app.ActivityOptionsCompat
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.oxoghost.hexapic.databinding.FragmentLibraryBinding
import com.oxoghost.hexapic.ui.detail.DetailActivity
import com.oxoghost.hexapic.ui.detail.DetailDataStore

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by activityViewModels()
    private val adapter = SectionedGridAdapter(::onPhotoClick)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            showGrid()
            viewModel.loadMedia()
        } else {
            showPermissionDenied()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGrid()
        setupObservers()

        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            })
        }

        if (hasPermissions()) {
            showGrid()
            if (viewModel.gridItems.value.isNullOrEmpty()) {
                viewModel.loadMedia()
            }
        } else {
            requestPermissions()
        }
    }

    private fun setupGrid() {
        val spanCount = spanCountForOrientation()
        val cellPx = resources.displayMetrics.widthPixels / spanCount
        adapter.cellSizePx = cellPx
        val lm = GridLayoutManager(requireContext(), spanCount)

        // Full-width for headers, separators, footer
        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (adapter.getItemViewType(position)) {
                    SectionedGridAdapter.TYPE_PHOTO -> 1
                    else -> spanCount
                }
            }
        }
        lm.spanSizeLookup.isSpanIndexCacheEnabled = true

        binding.recyclerView.layoutManager = lm
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(false) // variable row heights (headers differ)

        val gap = resources.displayMetrics.density.toInt()
        binding.recyclerView.addItemDecoration(GridSpacingDecoration(spanCount, gap, adapter))
        binding.recyclerView.addItemDecoration(StickyHeaderDecoration(adapter))

        binding.recyclerView.addOnScrollListener(PrefetchListener(spanCount))
    }

    private fun setupObservers() {
        viewModel.gridItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            if (items.isNotEmpty()) {
                binding.recyclerView.post { binding.recyclerView.scrollToPosition(0) }
            }
        }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val spanCount = spanCountForOrientation()
        adapter.cellSizePx = resources.displayMetrics.widthPixels / spanCount
        (binding.recyclerView.layoutManager as? GridLayoutManager)?.spanCount = spanCount
        binding.recyclerView.invalidateItemDecorations()
    }

    private fun spanCountForOrientation() =
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 4

    private fun hasPermissions() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requiredPermissions() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun requestPermissions() = permissionLauncher.launch(requiredPermissions())

    private fun showGrid() {
        binding.recyclerView.visibility = View.VISIBLE
        binding.layoutPermissionDenied.visibility = View.GONE
    }

    private fun showPermissionDenied() {
        binding.recyclerView.visibility = View.GONE
        binding.layoutPermissionDenied.visibility = View.VISIBLE
    }

    fun scrollToTop() = binding.recyclerView.scrollToPosition(0)

    private fun onPhotoClick(flatIndex: Int, thumbnail: View) {
        val photos = adapter.currentList
            .filterIsInstance<GridItem.Photo>()
            .map { it.media }
        if (photos.isEmpty()) return

        DetailDataStore.photos        = photos
        DetailDataStore.startPosition = flatIndex

        val mediaId = photos[flatIndex].id
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            requireActivity(), thumbnail, "photo_$mediaId"
        )
        startActivity(Intent(requireContext(), DetailActivity::class.java), options.toBundle())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Speculative prefetch ──────────────────────────────────────────────────

    private inner class PrefetchListener(spanCount: Int) :
            RecyclerView.OnScrollListener() {

        private val prefetchAhead = spanCount * 5 // ~5 rows ahead

        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0) return
            val lm = rv.layoutManager as? GridLayoutManager ?: return
            val lastVisible = lm.findLastVisibleItemPosition()
            val end = minOf(lastVisible + prefetchAhead, adapter.itemCount - 1)
            val imageLoader = Coil.imageLoader(rv.context)
            for (i in lastVisible + 1..end) {
                val item = adapter.currentList.getOrNull(i) as? GridItem.Photo ?: continue
                val cellPx = adapter.cellSizePx.takeIf { it > 0 } ?: continue
                imageLoader.enqueue(
                    ImageRequest.Builder(rv.context)
                        .data(MediaThumb(item.media.uri, item.media.isVideo))
                        .size(cellPx, cellPx)
                        .precision(Precision.INEXACT)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                )
            }
        }
    }
}
