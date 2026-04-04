package com.oxoghost.hexapic.ui.albums

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.oxoghost.hexapic.databinding.FragmentAlbumsBinding
import com.oxoghost.hexapic.ui.deleted.RecentlyDeletedActivity
import com.oxoghost.hexapic.ui.library.LibraryViewModel

class AlbumsFragment : Fragment() {

    private var _binding: FragmentAlbumsBinding? = null
    private val binding get() = _binding!!

    private val libraryViewModel: LibraryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowRecentlyDeleted.setOnClickListener {
            startActivity(Intent(requireContext(), RecentlyDeletedActivity::class.java))
        }

        // Show count of items in Recently Deleted
        libraryViewModel.deletedIds.observe(viewLifecycleOwner) { ids ->
            val count = ids.size
            binding.tvRecentlyDeletedCount.text = if (count > 0) count.toString() else ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
