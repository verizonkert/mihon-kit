package mihon.kit.ui.reader.filter

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Filter mode applied to manga pages during rendering.
 */
enum class ReaderFilterMode {
    None,
    Invert,
    Grayscale,
    InvertAndGrayscale,
}

/**
 * Advanced color filters matching Mihon's reader image post-processing pipeline.
 */
object ReaderColorFilter {

    /**
     * Invert colors matrix (swaps black and white, useful for night reading on white-background manga).
     */
    val InvertMatrix = ColorMatrix(
        floatArrayOf(
            -1f,  0f,  0f, 0f, 255f,
             0f, -1f,  0f, 0f, 255f,
             0f,  0f, -1f, 0f, 255f,
             0f,  0f,  0f, 1f,   0f,
        )
    )

    /**
     * Grayscale matrix (converts tinted scans into clean monochrome).
     */
    val GrayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }

    /**
     * Inverted Grayscale matrix.
     */
    val InvertedGrayscaleMatrix = ColorMatrix().apply {
        setToSaturation(0f)
        // Multiply with inversion
        timesAssign(InvertMatrix)
    }

    /**
     * Produces a [ColorFilter] combining filter mode, contrast boost, and brightness adjustment.
     *
     * @param mode Selected [ReaderFilterMode].
     * @param contrast Contrast multiplier (1.0f = normal, 1.2f = enhanced text, 0.8f = softer).
     * @param brightness Brightness offset (-50f to +50f, 0f = neutral).
     */
    fun createFilter(
        mode: ReaderFilterMode = ReaderFilterMode.None,
        contrast: Float = 1.0f,
        brightness: Float = 0.0f,
    ): ColorFilter? {
        if (mode == ReaderFilterMode.None && contrast == 1.0f && brightness == 0.0f) {
            return null
        }

        val baseMatrix = when (mode) {
            ReaderFilterMode.None -> ColorMatrix()
            ReaderFilterMode.Invert -> ColorMatrix(InvertMatrix.values.copyOf())
            ReaderFilterMode.Grayscale -> ColorMatrix().apply { setToSaturation(0f) }
            ReaderFilterMode.InvertAndGrayscale -> ColorMatrix(InvertedGrayscaleMatrix.values.copyOf())
        }

        if (contrast != 1.0f || brightness != 0.0f) {
            // Contrast adjustment formula: scale = contrast, translate = (1 - contrast) * 128 + brightness
            val t = (1.0f - contrast) * 128f + brightness
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
            baseMatrix.timesAssign(contrastMatrix)
        }

        return ColorFilter.colorMatrix(baseMatrix)
    }
}
