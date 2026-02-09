package com.modernrdp.rdp

import android.app.Activity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Helps manage display resolution for foldable devices like Samsung Galaxy Fold.
 *
 * Handles:
 * - Detecting fold/unfold state changes
 * - Computing optimal RDP resolution for current screen configuration
 * - Providing screen metrics accounting for hinge/fold areas
 */
class FoldableDisplayHelper(private val activity: Activity) {

    private val windowInfoTracker = WindowInfoTracker.getOrCreate(activity)

    data class DisplayInfo(
        val widthPx: Int,
        val heightPx: Int,
        val widthDp: Int,
        val heightDp: Int,
        val density: Float,
        val foldState: FoldState,
        val hingeOrientation: HingeOrientation?,
        val hingeBounds: HingeBounds?,
    )

    data class HingeBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    enum class FoldState {
        FLAT,           // Fully unfolded (inner screen)
        HALF_OPENED,    // Partially folded (tabletop/book mode)
        FOLDED,         // Cover screen
    }

    enum class HingeOrientation {
        HORIZONTAL,     // Hinge runs left-right (tabletop mode)
        VERTICAL,       // Hinge runs top-bottom (book mode)
    }

    /**
     * Get current display metrics for RDP resolution negotiation.
     */
    fun getCurrentDisplayInfo(): DisplayInfo {
        val metrics = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(activity)
        val bounds = metrics.bounds
        val density = activity.resources.displayMetrics.density

        return DisplayInfo(
            widthPx = bounds.width(),
            heightPx = bounds.height(),
            widthDp = (bounds.width() / density).toInt(),
            heightDp = (bounds.height() / density).toInt(),
            density = density,
            foldState = FoldState.FLAT,
            hingeOrientation = null,
            hingeBounds = null,
        )
    }

    /**
     * Observe fold state changes. Emits new DisplayInfo whenever
     * the device folds/unfolds so the RDP session can resize.
     */
    fun observeDisplayChanges(): Flow<DisplayInfo> {
        return windowInfoTracker.windowLayoutInfo(activity).map { layoutInfo ->
            val metrics = WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(activity)
            val bounds = metrics.bounds
            val density = activity.resources.displayMetrics.density

            val foldingFeature = layoutInfo.displayFeatures
                .filterIsInstance<FoldingFeature>()
                .firstOrNull()

            val foldState = when {
                foldingFeature == null -> FoldState.FLAT
                foldingFeature.state == FoldingFeature.State.HALF_OPENED -> FoldState.HALF_OPENED
                else -> FoldState.FLAT
            }

            val hingeOrientation = foldingFeature?.let {
                when (it.orientation) {
                    FoldingFeature.Orientation.HORIZONTAL -> HingeOrientation.HORIZONTAL
                    FoldingFeature.Orientation.VERTICAL -> HingeOrientation.VERTICAL
                    else -> null
                }
            }

            val hingeBounds = foldingFeature?.let {
                HingeBounds(
                    left = it.bounds.left,
                    top = it.bounds.top,
                    right = it.bounds.right,
                    bottom = it.bounds.bottom,
                )
            }

            DisplayInfo(
                widthPx = bounds.width(),
                heightPx = bounds.height(),
                widthDp = (bounds.width() / density).toInt(),
                heightDp = (bounds.height() / density).toInt(),
                density = density,
                foldState = foldState,
                hingeOrientation = hingeOrientation,
                hingeBounds = hingeBounds,
            )
        }
    }

    /**
     * Compute the optimal RDP resolution for the given display config.
     * Accounts for device DPI scaling so remote desktop content is readable.
     */
    fun computeRdpResolution(displayInfo: DisplayInfo, scaleFactor: Float = 1.0f): Pair<Int, Int> {
        // Scale down from physical pixels to a usable remote resolution.
        // On high-DPI foldable screens, sending raw pixel dimensions would make
        // remote desktop content tiny and unusable.
        val effectiveScale = when {
            displayInfo.density >= 3.5f -> 0.5f  // xxxhdpi (Fold inner screen)
            displayInfo.density >= 2.5f -> 0.6f  // xxhdpi
            displayInfo.density >= 2.0f -> 0.75f // xhdpi
            else -> 1.0f
        } * scaleFactor

        val width = ((displayInfo.widthPx * effectiveScale).toInt() / 4) * 4   // align to 4px
        val height = ((displayInfo.heightPx * effectiveScale).toInt() / 4) * 4

        return width to height
    }
}
