package com.example.videozoomplayer

import android.animation.ValueAnimator
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.roundToInt

class PlayerZoomController(
    private val playerView: PlayerView,
    private val minZoom: Float = 1f,
    private val maxZoom: Float = 4f
) : View.OnTouchListener {

    interface Listener {
        fun onViewportCenterChanged(centerX: Int, sourceWidth: Int)
        fun onViewportCenterUnavailable()
    }

    var listener: Listener? = null

    private val scaleDetector = ScaleGestureDetector(playerView.context, ScaleListener())
    private val gestureDetector = GestureDetector(playerView.context, GestureListener())
    private var animator: ValueAnimator? = null

    private var scale = 1f
    private var translationX = 0f
    private var translationY = 0f
    private var videoWidth = 0
    private var videoHeight = 0

    fun attach() {
        playerView.setOnTouchListener(this)
        playerView.post { notifyViewportCenter() }
    }

    fun detach() {
        animator?.cancel()
        playerView.setOnTouchListener(null)
    }

    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        notifyViewportCenter()
    }

    fun reset() {
        val textureView = playerView.videoSurfaceView as? TextureView
        if (textureView == null) {
            scale = minZoom
            translationX = 0f
            translationY = 0f
            notifyViewportCenter()
            return
        }
        resetWithoutAnimation(textureView)
    }

    override fun onTouch(view: View?, event: MotionEvent): Boolean {
        val textureView = playerView.videoSurfaceView as? TextureView ?: return false
        val scaling = scaleDetector.onTouchEvent(event)
        val gesturing = gestureDetector.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (abs(scale - minZoom) < 0.001f) {
                resetWithoutAnimation(textureView)
            }
        }

        val shouldConsume = scaling || gesturing || scale > minZoom
        if (!shouldConsume && event.actionMasked == MotionEvent.ACTION_UP) {
            view?.performClick()
        }
        return shouldConsume
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val textureView = playerView.videoSurfaceView as? TextureView ?: return false

            val previousScale = scale
            scale = (scale * detector.scaleFactor).coerceIn(minZoom, maxZoom)

            val scaleRatio = scale / previousScale
            val focusX = detector.focusX - (textureView.width / 2f)
            val focusY = detector.focusY - (textureView.height / 2f)
            translationX = (translationX - focusX) * scaleRatio + focusX
            translationY = (translationY - focusY) * scaleRatio + focusY

            clampTranslation(textureView)
            applyTransform(textureView)
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val textureView = playerView.videoSurfaceView as? TextureView ?: return false
            if (scale <= minZoom) return false

            translationX -= distanceX
            translationY -= distanceY
            clampTranslation(textureView)
            applyTransform(textureView)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val textureView = playerView.videoSurfaceView as? TextureView ?: return false
            if (scale > minZoom) {
                animateTo(minZoom, 0f, 0f, textureView)
            } else {
                val targetScale = 2f
                val focusX = e.x - (textureView.width / 2f)
                val focusY = e.y - (textureView.height / 2f)
                val targetTx = -focusX * (targetScale - 1f)
                val targetTy = -focusY * (targetScale - 1f)
                animateTo(targetScale, targetTx, targetTy, textureView)
            }
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (playerView.isControllerFullyVisible) {
                playerView.hideController()
            } else {
                playerView.showController()
            }
            return true
        }
    }

    private fun animateTo(targetScale: Float, targetTx: Float, targetTy: Float, textureView: TextureView) {
        animator?.cancel()
        val startScale = scale
        val startTx = translationX
        val startTy = translationY

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                scale = lerp(startScale, targetScale, progress).coerceIn(minZoom, maxZoom)
                translationX = lerp(startTx, targetTx, progress)
                translationY = lerp(startTy, targetTy, progress)
                clampTranslation(textureView)
                applyTransform(textureView)
            }
            start()
        }
    }

    private fun resetWithoutAnimation(textureView: TextureView) {
        scale = minZoom
        translationX = 0f
        translationY = 0f
        applyTransform(textureView)
    }

    private fun clampTranslation(textureView: TextureView) {
        val maxTranslateX = (textureView.width * (scale - 1f)) / 2f
        val maxTranslateY = (textureView.height * (scale - 1f)) / 2f
        translationX = translationX.coerceIn(-maxTranslateX, maxTranslateX)
        translationY = translationY.coerceIn(-maxTranslateY, maxTranslateY)
    }

    private fun applyTransform(textureView: TextureView) {
        textureView.scaleX = scale
        textureView.scaleY = scale
        textureView.translationX = translationX
        textureView.translationY = translationY
        notifyViewportCenter(textureView)
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float {
        return start + (end - start) * progress
    }

    private fun notifyViewportCenter(textureView: TextureView? = playerView.videoSurfaceView as? TextureView) {
        val currentListener = listener ?: return
        if (videoWidth <= 0 || videoHeight <= 0 || textureView == null) {
            currentListener.onViewportCenterUnavailable()
            return
        }
        if (textureView.width == 0 || textureView.height == 0 || scale <= 0f) {
            currentListener.onViewportCenterUnavailable()
            return
        }

        val sourceRect = calculateSourceDisplayRect(textureView.width.toFloat(), textureView.height.toFloat())
        if (sourceRect.width() <= 0f) {
            currentListener.onViewportCenterUnavailable()
            return
        }

        val localCenterX = (textureView.width / 2f - translationX) / scale
        val normalizedX = ((localCenterX - sourceRect.left) / sourceRect.width()).coerceIn(0f, 1f)
        val sourceX = (normalizedX * videoWidth).roundToInt().coerceIn(0, videoWidth)
        currentListener.onViewportCenterChanged(sourceX, videoWidth)
    }

    private fun calculateSourceDisplayRect(viewWidth: Float, viewHeight: Float): RectF {
        if (videoWidth <= 0 || videoHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) {
            return RectF(0f, 0f, viewWidth, viewHeight)
        }

        val viewAspect = viewWidth / viewHeight
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        return if (videoAspect > viewAspect) {
            val displayedHeight = viewWidth / videoAspect
            val top = (viewHeight - displayedHeight) / 2f
            RectF(0f, top, viewWidth, top + displayedHeight)
        } else {
            val displayedWidth = viewHeight * videoAspect
            val left = (viewWidth - displayedWidth) / 2f
            RectF(left, 0f, left + displayedWidth, viewHeight)
        }
    }
}
