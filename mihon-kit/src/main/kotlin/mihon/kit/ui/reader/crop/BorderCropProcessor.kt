package mihon.kit.ui.reader.crop

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Automatically detects and crops dead white or black margins around manga scan panels,
 * matching Mihon's dynamic border crop feature.
 */
object BorderCropProcessor {

    /**
     * Inspects bitmap pixels along the borders and returns a cropped [Bitmap] with borders removed,
     * or the original bitmap if no significant margin is detected.
     *
     * @param source The source bitmap to crop.
     * @param threshold Color variance threshold for detecting white (e.g. 240..255) or black (0..15).
     * @return Cropped or original [Bitmap].
     */
    suspend fun cropBorders(source: Bitmap, threshold: Int = 15): Bitmap = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height
        if (width <= 10 || height <= 10) return@withContext source

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // Find top margin
        var top = 0
        while (top < height && isUniformRow(pixels, width, top, threshold)) {
            top++
        }

        // Find bottom margin
        var bottom = height - 1
        while (bottom > top && isUniformRow(pixels, width, bottom, threshold)) {
            bottom--
        }

        // Find left margin
        var left = 0
        while (left < width && isUniformColumn(pixels, width, height, left, top, bottom, threshold)) {
            left++
        }

        // Find right margin
        var right = width - 1
        while (right > left && isUniformColumn(pixels, width, height, right, top, bottom, threshold)) {
            right--
        }

        val cropWidth = right - left + 1
        val cropHeight = bottom - top + 1

        // Only crop if there's at least a 2% margin to prevent unnecessary re-allocation
        val widthMargin = width - cropWidth
        val heightMargin = height - cropHeight
        if (widthMargin < width * 0.02 && heightMargin < height * 0.02) {
            return@withContext source
        }

        Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
    }

    private fun isUniformRow(pixels: IntArray, width: Int, y: Int, threshold: Int): Boolean {
        val offset = y * width
        val firstPixel = pixels[offset]
        val isLight = isLightColor(firstPixel, 255 - threshold)
        val isDark = isDarkColor(firstPixel, threshold)
        if (!isLight && !isDark) return false

        for (x in 1 until width step 4) { // Step by 4 for high-speed sampling
            val p = pixels[offset + x]
            if (isLight && !isLightColor(p, 255 - threshold)) return false
            if (isDark && !isDarkColor(p, threshold)) return false
        }
        return true
    }

    private fun isUniformColumn(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        startY: Int,
        endY: Int,
        threshold: Int,
    ): Boolean {
        val firstPixel = pixels[startY * width + x]
        val isLight = isLightColor(firstPixel, 255 - threshold)
        val isDark = isDarkColor(firstPixel, threshold)
        if (!isLight && !isDark) return false

        for (y in startY..endY step 4) {
            val p = pixels[y * width + x]
            if (isLight && !isLightColor(p, 255 - threshold)) return false
            if (isDark && !isDarkColor(p, threshold)) return false
        }
        return true
    }

    private fun isLightColor(pixel: Int, minLight: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r >= minLight && g >= minLight && b >= minLight
    }

    private fun isDarkColor(pixel: Int, maxDark: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r <= maxDark && g <= maxDark && b <= maxDark
    }
}
