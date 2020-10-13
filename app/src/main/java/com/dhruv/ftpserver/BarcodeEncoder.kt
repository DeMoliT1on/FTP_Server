package com.dhruv.ftpserver

import android.graphics.Bitmap
import com.google.zxing.common.BitMatrix

object BarcodeEncoder {
    private const val WHITE = -0x1
    private const val BLACK = -0x1000000

    fun createBitmap(matrix: BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) BLACK else WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}