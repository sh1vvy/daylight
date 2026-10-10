package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

/** Locate the offline fixture's sleeve, including the moving header's top edge. */
internal object NativeCoverFrames {
    fun hasRoundedTopCorners(bitmap: Bitmap, sleeve: Rect): Boolean {
        if (sleeve.width() < 24 || sleeve.height() < 24) return false
        fun clear(x: Int, y: Int): Boolean {
            val pixel = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1))
            return kotlin.math.abs(Color.red(pixel) - 184) >= 12 ||
                kotlin.math.abs(Color.green(pixel) - 104) >= 12 ||
                kotlin.math.abs(Color.blue(pixel) - 137) >= 12
        }
        return clear(sleeve.left + 4, sleeve.top + 4) && clear(sleeve.right - 5, sleeve.top + 4)
    }

    /** The center of the fixture must remain visible, rather than a cropped strip of its edge. */
    fun hasCenteredFixtureHub(bitmap: Bitmap, sleeve: Rect): Boolean {
        if (sleeve.isEmpty) return false
        // The restored bottom veil changes the detected pink region's height.
        // Find the purple hub along the cover's horizontal centre, then require
        // its cream disc on both sides. This still rejects the cropped-strip bug.
        val halfWidth = (sleeve.width() * 0.06f).toInt()
        val ringOffset = (sleeve.width() * 0.16f).toInt()
        val top = (sleeve.top + sleeve.width() * 0.16f).toInt().coerceIn(0, bitmap.height - 1)
        val bottom = (sleeve.top + sleeve.width() * 0.85f).toInt().coerceIn(top, bitmap.height - 1)
        fun cream(x: Int, y: Int): Boolean {
            val pixel = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y)
            return Color.red(pixel) > 160 && Color.green(pixel) > 110 &&
                Color.red(pixel) > Color.green(pixel) + 15 && Color.green(pixel) > Color.blue(pixel) + 8
        }
        var samples = 0
        for (y in top..bottom step 4) for (x in (sleeve.centerX() - halfWidth).coerceAtLeast(0)..
            (sleeve.centerX() + halfWidth).coerceAtMost(bitmap.width - 1) step 4) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.red(pixel) in 70..165 && Color.green(pixel) in 35..125 &&
                Color.blue(pixel) > Color.green(pixel) + 10 &&
                Color.red(pixel) < Color.blue(pixel) + 25 &&
                cream(x - ringOffset, y) && cream(x + ringOffset, y)) samples++
        }
        return samples >= 4
    }

    /** Once the veil fades in, the pink rim is no longer a reliable geometry marker. */
    fun hasFixtureHubAtHeaderCenter(bitmap: Bitmap): Boolean {
        val sleeve = Rect(0, 0, bitmap.width, bitmap.width)
        return hasCenteredFixtureHub(bitmap, sleeve)
    }

    fun titleIsVisible(bitmap: Bitmap, bounds: Rect, dark: Boolean): Boolean {
        var samples = 0
        for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(bitmap.height) step 2)
            for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(bitmap.width) step 2) {
                val pixel = bitmap.getPixel(x, y)
                val channels = listOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
                if (if (dark) channels.all { it > 220 } else channels.all { it < 70 }) samples++
            }
        return samples > 15
    }

    fun widestFixtureSleeve(bitmap: Bitmap): Rect {
        val step = 4
        val width = bitmap.width / step
        val height = bitmap.height / step
        val cells = BooleanArray(width * height) { index ->
            val pixel = bitmap.getPixel(index % width * step, index / width * step)
            kotlin.math.abs(Color.red(pixel) - 184) < 12 &&
                kotlin.math.abs(Color.green(pixel) - 104) < 12 &&
                kotlin.math.abs(Color.blue(pixel) - 137) < 12
        }
        val queue = IntArray(cells.size)
        var largest = Rect()
        for (start in cells.indices) {
            if (!cells[start]) continue
            var head = 0
            var tail = 1
            var left = width
            var right = 0
            var top = height
            var bottom = 0
            queue[0] = start
            cells[start] = false
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
                fun add(next: Int) {
                    if (cells[next]) { cells[next] = false; queue[tail++] = next }
                }
                if (x > 0) add(index - 1)
                if (x < width - 1) add(index + 1)
                if (y > 0) add(index - width)
                if (y < height - 1) add(index + width)
            }
            // The destination adds a bottom fade. Area or disc size can select
            // a static neighboring card; the expanding sleeve's width cannot.
            if ((right - left + 1) * step > largest.width()) {
                largest = Rect(left * step, top * step, (right + 1) * step, (bottom + 1) * step)
            }
        }
        return largest
    }
}
