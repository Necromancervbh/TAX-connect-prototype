package com.example.taxconnect.features.videocall

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.taxconnect.databinding.ActivityVideoPlayerBinding
import com.example.taxconnect.R

import android.view.LayoutInflater
import com.example.taxconnect.core.base.BaseActivity

class VideoPlayerActivity : BaseActivity<ActivityVideoPlayerBinding>() {

    override val bindingInflater: (LayoutInflater) -> ActivityVideoPlayerBinding = ActivityVideoPlayerBinding::inflate

    private var player: ExoPlayer? = null
    private var videoUrl: String? = null

    override fun initViews() {
        videoUrl = getIntent().getStringExtra("VIDEO_URL")
        if (videoUrl.isNullOrEmpty()) {
            showToast("Video URL is missing")
            finish()
            return
        }
        
        binding.btnClose.setOnClickListener { finish() }
        
        initializePlayer()
    }

    override fun setupListeners() {
        // Listeners set in initViews or inline
    }

    override fun observeViewModel() {
        // No ViewModel
    }

    private fun initializePlayer() {
        val currentVideoUrl = videoUrl ?: return
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            binding.playerView.player = exoPlayer
            val mediaItem = MediaItem.fromUri(Uri.parse(currentVideoUrl))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    override fun onStart() {
        super.onStart()
        if (player == null) {
            initializePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.let {
            it.release()
            player = null
        }
    }
}
