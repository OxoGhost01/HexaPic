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
import com.google.android.material.snackbar.Snackbar
import com.oxoghost.hexapic.databinding.FragmentLibraryBinding

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by activityViewModels()
    private val adapter = PhotoGridAdapter()

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
            // Only load if not already loaded
            if (viewModel.mediaItems.value.isNullOrEmpty()) {
                viewModel.loadMedia()
            }
        } else {
            requestPermissions()
        }
    }

    private fun setupGrid() {
        val spanCount = spanCountForOrientation()
        val layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)

        // 1dp gap between cells via item decoration
        val gap = resources.displayMetrics.density.toInt() // ~1dp in px
        binding.recyclerView.addItemDecoration(GridSpacingDecoration(spanCount, gap))
    }

    private fun setupObservers() {
        viewModel.mediaItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            if (items.isNotEmpty()) {
                // Scroll to bottom (most recent = end of list sorted desc = position 0 is newest)
                // Items are sorted newest-first; bottom of list = oldest. Per spec: scroll to bottom
                // means most recent visible. Since newest is at position 0, scroll to top on first load.
                binding.recyclerView.post {
                    binding.recyclerView.scrollToPosition(0)
                }
            }
            binding.tvMediaCount.text = getString(
                com.oxoghost.hexapic.R.string.photos_count,
                viewModel.photoCount,
                viewModel.videoCount
            )
            binding.tvMediaCount.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val spanCount = spanCountForOrientation()
        (binding.recyclerView.layoutManager as? GridLayoutManager)?.spanCount = spanCount
        // Re-attach item decorations with new span count
        binding.recyclerView.invalidateItemDecorations()
    }

    private fun spanCountForOrientation(): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 4
    }

    private fun hasPermissions(): Boolean {
        val perms = requiredPermissions()
        return perms.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions())
    }

    private fun showGrid() {
        binding.recyclerView.visibility = View.VISIBLE
        binding.layoutPermissionDenied.visibility = View.GONE
    }

    private fun showPermissionDenied() {
        binding.recyclerView.visibility = View.GONE
        binding.layoutPermissionDenied.visibility = View.VISIBLE
    }

    /** Called by MainActivity when the active tab is re-tapped — scrolls to top. */
    fun scrollToTop() {
        binding.recyclerView.scrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
