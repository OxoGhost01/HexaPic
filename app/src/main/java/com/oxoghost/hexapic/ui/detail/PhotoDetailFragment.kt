package com.oxoghost.hexapic.ui.detail

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import coil.load
import coil.request.CachePolicy
import com.oxoghost.hexapic.databinding.FragmentPhotoDetailBinding

class PhotoDetailFragment : Fragment() {

    private var _binding: FragmentPhotoDetailBinding? = null
    private val binding get() = _binding!!

    private var controller: DetailController? = null

    private val position by lazy { arguments?.getInt(ARG_POSITION) ?: 0 }
    private val media    get() = DetailDataStore.photos.getOrNull(position)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        controller = context as? DetailController
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPhotoDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val item = media ?: return

        // Shared-element transition name must match the grid thumbnail
        binding.gestureImageView.transitionName = "photo_${item.id}"

        binding.gestureImageView.gestureCallback = object : GestureImageView.GestureCallback {
            override fun onSingleTap() {
                controller?.onToggleChrome()
            }
            override fun onSwipeDownProgress(fraction: Float) {
                view.translationY = fraction * view.height
                controller?.onDismissProgress(fraction)
            }
            override fun onSwipeDownCommit() {
                controller?.onDismissCommit()
            }
            override fun onSwipeDownCancel() {
                view.animate()
                    .translationY(0f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                controller?.onDismissProgress(0f)
                binding.gestureImageView.springBack()
            }
        }

        // Load image; start postponed enter transition once ready
        binding.gestureImageView.load(item.uri) {
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            allowHardware(false) // required for shared element transition
            listener(
                onSuccess = { _, _ ->
                    if (position == DetailDataStore.startPosition) {
                        requireActivity().startPostponedEnterTransition()
                    }
                },
                onError = { _, _ ->
                    if (position == DetailDataStore.startPosition) {
                        requireActivity().startPostponedEnterTransition()
                    }
                },
            )
        }
    }

    override fun onDetach() {
        super.onDetach()
        controller = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_POSITION = "position"

        fun newInstance(position: Int) = PhotoDetailFragment().apply {
            arguments = Bundle().apply { putInt(ARG_POSITION, position) }
        }
    }
}
