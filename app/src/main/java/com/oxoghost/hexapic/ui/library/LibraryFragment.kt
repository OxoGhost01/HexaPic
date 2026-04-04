package com.oxoghost.hexapic.ui.library

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.oxoghost.hexapic.MainActivity
import com.oxoghost.hexapic.R
import com.oxoghost.hexapic.databinding.FragmentLibraryBinding
import com.oxoghost.hexapic.ui.detail.DetailActivity
import com.oxoghost.hexapic.ui.detail.DetailDataStore

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by activityViewModels()

    private val adapter = SectionedGridAdapter(
        onPhotoClick     = ::onPhotoClick,
        onPhotoToggle    = { id -> viewModel.toggleSelection(id) },
        onPhotoLongClick = { id ->
            viewModel.enterSelectionMode()
            viewModel.toggleSelection(id)
        },
    )

    // ── Permission launcher ────────────────────────────────────────────────────

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

    // ── Favorite activity result launcher ─────────────────────────────────────

    private val favoriteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.loadMedia()
        }
    }

    // ── Back-press: exit selection mode ───────────────────────────────────────

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { viewModel.exitSelectionMode() }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        setupGrid()
        setupObservers()
        setupTopBar()
        setupBottomBar()

        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            })
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.selectionBottomBar) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, nav.bottom)
            insets
        }

        if (hasPermissions()) {
            showGrid()
            if (viewModel.gridItems.value.isNullOrEmpty()) viewModel.loadMedia()
        } else {
            requestPermissions()
        }
    }

    // ── Grid setup ────────────────────────────────────────────────────────────

    private fun setupGrid() {
        val spanCount = spanCountForOrientation()
        val cellPx = resources.displayMetrics.widthPixels / spanCount
        adapter.cellSizePx = cellPx
        val lm = GridLayoutManager(requireContext(), spanCount)

        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (adapter.getItemViewType(position) == SectionedGridAdapter.TYPE_PHOTO) 1
                else spanCount
        }
        lm.spanSizeLookup.isSpanIndexCacheEnabled = true

        binding.recyclerView.layoutManager = lm
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(false)

        val gap = resources.displayMetrics.density.toInt()
        binding.recyclerView.addItemDecoration(GridSpacingDecoration(spanCount, gap, adapter))
        binding.recyclerView.addItemDecoration(StickyHeaderDecoration(adapter))

        binding.recyclerView.addOnScrollListener(PrefetchListener(spanCount))
        binding.recyclerView.addOnItemTouchListener(TwoFingerSelectionListener())
    }

    // ── Observers ─────────────────────────────────────────────────────────────

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
        viewModel.selectionMode.observe(viewLifecycleOwner) { inMode ->
            backCallback.isEnabled = inMode
            adapter.setSelectionMode(inMode)
            applySelectionModeUi(inMode)
        }
        viewModel.selectedIds.observe(viewLifecycleOwner) { ids ->
            adapter.setSelectedIds(ids)
            updateCountLabel(ids.size)
            updateSelectAllLabel(ids.size)
        }
    }

    // ── Top-bar wiring ────────────────────────────────────────────────────────

    private fun setupTopBar() {
        binding.btnSelect.setOnClickListener { viewModel.enterSelectionMode() }
        binding.btnCancel.setOnClickListener { viewModel.exitSelectionMode() }
        binding.btnSelectAll.setOnClickListener {
            val total = adapter.currentList.count { it is GridItem.Photo }
            val selected = viewModel.selectedIds.value?.size ?: 0
            if (selected >= total) viewModel.deselectAll() else viewModel.selectAll()
        }
    }

    private fun applySelectionModeUi(inMode: Boolean) {
        binding.tvTitle.visibility    = if (!inMode) View.VISIBLE else View.GONE
        binding.btnSelect.visibility  = if (!inMode) View.VISIBLE else View.GONE
        binding.btnCancel.visibility       = if (inMode) View.VISIBLE else View.GONE
        binding.tvSelectionCount.visibility = if (inMode) View.VISIBLE else View.GONE
        binding.btnSelectAll.visibility    = if (inMode) View.VISIBLE else View.GONE
        binding.selectionBottomBar.visibility =
            if (inMode) View.VISIBLE else View.GONE
        (requireActivity() as? MainActivity)?.setBottomNavVisible(!inMode)
    }

    private fun updateCountLabel(count: Int) {
        binding.tvSelectionCount.text =
            if (count == 0) getString(R.string.select_items)
            else getString(R.string.n_selected, count)
    }

    private fun updateSelectAllLabel(selected: Int) {
        val total = adapter.currentList.count { it is GridItem.Photo }
        binding.btnSelectAll.text =
            if (selected >= total && total > 0) getString(R.string.deselect_all)
            else getString(R.string.select_all)
    }

    // ── Bottom-bar wiring ──────────────────────────────────────────────────────

    private fun setupBottomBar() {
        binding.btnShareSelected.setOnClickListener { shareSelected() }
        binding.btnAddToAlbum.setOnClickListener {
            Toast.makeText(requireContext(),
                getString(R.string.add_to_album_stub),
                Toast.LENGTH_SHORT).show()
        }
        binding.btnFavoriteSelected.setOnClickListener { favoriteSelected() }
        binding.btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }
    }

    private fun shareSelected() {
        val uris = ArrayList(selectedMediaItems().map { it.uri })
        if (uris.isEmpty()) return
        val mimeType = when {
            selectedMediaItems().all { !it.isVideo } -> "image/*"
            selectedMediaItems().all {  it.isVideo } -> "video/*"
            else                                      -> "*/*"
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, null))
        viewModel.exitSelectionMode()
    }

    private fun favoriteSelected() {
        val items = selectedMediaItems()
        if (items.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val allFav = items.all { it.isFavorite }
            val req = MediaStore.createFavoriteRequest(
                requireContext().contentResolver, items.map { it.uri }, !allFav)
            favoriteRequestLauncher.launch(IntentSenderRequest.Builder(req).build())
        } else {
            Toast.makeText(requireContext(),
                getString(R.string.favorite_not_supported),
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteSelected() {
        val items = selectedMediaItems()
        if (items.isEmpty()) return
        val message = if (items.size == 1)
            getString(R.string.delete_confirm_single)
        else
            getString(R.string.delete_confirm_multi, items.size)
        AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setPositiveButton(R.string.move_to_recently_deleted) { _, _ ->
                viewModel.softDelete(items)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun selectedMediaItems() = adapter.currentList
        .filterIsInstance<GridItem.Photo>()
        .filter { viewModel.selectedIds.value?.contains(it.media.id) == true }
        .map { it.media }

    // ── Photo click (normal mode) ─────────────────────────────────────────────

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

    // ── Config changes ────────────────────────────────────────────────────────

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val spanCount = spanCountForOrientation()
        adapter.cellSizePx = resources.displayMetrics.widthPixels / spanCount
        (binding.recyclerView.layoutManager as? GridLayoutManager)?.spanCount = spanCount
        binding.recyclerView.invalidateItemDecorations()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Two-finger selection gesture ──────────────────────────────────────────

    private inner class TwoFingerSelectionListener : RecyclerView.SimpleOnItemTouchListener() {
        private var tracking = false

        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (e.pointerCount == 2) {
                        tracking = true
                        if (viewModel.selectionMode.value != true) viewModel.enterSelectionMode()
                        selectUnder(rv, e)
                        return true
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> tracking = false
            }
            return false
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE ->
                    if (tracking && e.pointerCount >= 2) selectUnder(rv, e)
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_POINTER_UP -> tracking = false
            }
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            if (disallowIntercept) tracking = false
        }

        private fun selectUnder(rv: RecyclerView, e: MotionEvent) {
            if (e.pointerCount < 2) return
            val x = (e.getX(0) + e.getX(1)) / 2f
            val y = (e.getY(0) + e.getY(1)) / 2f
            val child = rv.findChildViewUnder(x, y) ?: return
            val pos = rv.getChildAdapterPosition(child)
            val item = adapter.currentList.getOrNull(pos) as? GridItem.Photo ?: return
            viewModel.addToSelection(item.media.id)
        }
    }

    // ── Speculative prefetch ───────────────────────────────────────────────────

    private inner class PrefetchListener(spanCount: Int) : RecyclerView.OnScrollListener() {

        private val prefetchAhead = spanCount * 5

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
