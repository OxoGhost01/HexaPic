package com.oxoghost.hexapic.ui.deleted

import android.app.Activity
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.oxoghost.hexapic.R
import com.oxoghost.hexapic.databinding.ActivityRecentlyDeletedBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RecentlyDeletedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecentlyDeletedBinding
    private val viewModel: RecentlyDeletedViewModel by viewModels()

    private val adapter = DeletedGridAdapter(
        onItemClick = { id ->
            if (viewModel.selectionMode.value == true) viewModel.toggleSelection(id)
            else { viewModel.enterSelectionMode(); viewModel.toggleSelection(id) }
        },
        onItemLongClick = { id ->
            viewModel.enterSelectionMode()
            viewModel.toggleSelection(id)
        },
    )

    // ── Permanent-delete launcher (API 30+) ───────────────────────────────────

    private var pendingPermDeleteIds = emptyList<Long>()

    private val permDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.removeFromDb(pendingPermDeleteIds)
        }
        pendingPermDeleteIds = emptyList()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecentlyDeletedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Hide content until biometric passes
        binding.contentLayout.visibility = View.INVISIBLE

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomBar) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, nav.bottom)
            insets
        }

        setupGrid()
        setupObservers()
        setupTopBar()
        setupBottomBar()
        authenticate()
    }

    // ── Biometric ─────────────────────────────────────────────────────────────

    private fun authenticate() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                binding.contentLayout.visibility = View.VISIBLE
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE -> {
                        // No biometric enrolled — show content without auth
                        binding.contentLayout.visibility = View.VISIBLE
                    }
                    else -> finish()
                }
            }
            override fun onAuthenticationFailed() { /* user can retry */ }
        })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.recently_deleted))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(info)
    }

    // ── Grid ──────────────────────────────────────────────────────────────────

    private fun setupGrid() {
        val spanCount = spanCount()
        val cellPx = resources.displayMetrics.widthPixels / spanCount
        adapter.cellSizePx = cellPx
        val lm = GridLayoutManager(this, spanCount)
        binding.recyclerView.layoutManager = lm
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)
    }

    private fun spanCount() =
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 4

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val spanCount = spanCount()
        adapter.cellSizePx = resources.displayMetrics.widthPixels / spanCount
        (binding.recyclerView.layoutManager as? GridLayoutManager)?.spanCount = spanCount
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.items.observe(this) { items ->
            adapter.submitList(items)
            binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.selectionMode.observe(this) { inMode ->
            adapter.setSelectionMode(inMode)
            applySelectionUi(inMode)
        }
        viewModel.selectedIds.observe(this) { ids ->
            adapter.setSelectedIds(ids)
            val count = ids.size
            binding.tvTitle.text = if (!viewModel.selectionMode.value!!)
                getString(R.string.recently_deleted)
            else if (count == 0) getString(R.string.select_items)
            else getString(R.string.n_selected, count)
            binding.btnRecover.alpha = if (count > 0) 1f else 0.4f
            binding.btnDeletePermanently.alpha = if (count > 0) 1f else 0.4f
        }
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSelect.setOnClickListener { viewModel.enterSelectionMode() }
        binding.btnCancel.setOnClickListener { viewModel.exitSelectionMode() }
        binding.btnSelectAll.setOnClickListener {
            val total = viewModel.items.value?.size ?: 0
            val selected = viewModel.selectedIds.value?.size ?: 0
            if (selected >= total) viewModel.deselectAll() else viewModel.selectAll()
        }
    }

    private fun applySelectionUi(inMode: Boolean) {
        binding.tvTitle.text   = if (!inMode) getString(R.string.recently_deleted)
                                 else getString(R.string.select_items)
        binding.btnSelect.visibility    = if (!inMode) View.VISIBLE else View.GONE
        binding.btnCancel.visibility    = if (inMode) View.VISIBLE else View.GONE
        binding.btnSelectAll.visibility = if (inMode) View.VISIBLE else View.GONE
        binding.bottomBar.visibility    = if (inMode) View.VISIBLE else View.GONE
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────

    private fun setupBottomBar() {
        binding.btnRecover.setOnClickListener {
            val ids = viewModel.selectedIds.value?.toList() ?: return@setOnClickListener
            if (ids.isEmpty()) return@setOnClickListener
            viewModel.recover(ids)
        }
        binding.btnDeletePermanently.setOnClickListener {
            val ids = viewModel.selectedIds.value?.toList() ?: return@setOnClickListener
            if (ids.isEmpty()) return@setOnClickListener
            confirmPermanentDelete(ids)
        }
    }

    private fun confirmPermanentDelete(ids: List<Long>) {
        val message = if (ids.size == 1)
            getString(R.string.delete_permanently_confirm_single)
        else
            getString(R.string.delete_permanently_confirm_multi, ids.size)

        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(R.string.delete_permanently) { _, _ -> doPermanentDelete(ids) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun doPermanentDelete(ids: List<Long>) {
        lifecycleScope.launch {
            val items = viewModel.getItemsByIds(ids)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val uris = items.map { Uri.parse(it.uri) }
                if (uris.isEmpty()) return@launch
                val req = MediaStore.createDeleteRequest(contentResolver, uris)
                pendingPermDeleteIds = ids
                permDeleteLauncher.launch(IntentSenderRequest.Builder(req).build())
            } else {
                for (item in items) {
                    runCatching { contentResolver.delete(Uri.parse(item.uri), null, null) }
                }
                viewModel.removeFromDb(ids)
            }
        }
    }
}
