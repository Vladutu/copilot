package com.vladutu.copilot.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Renders [content] as a square QR [Bitmap] of [sizePx]. */
fun qrBitmap(content: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val w = matrix.width
    val h = matrix.height
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
    for (x in 0 until w) {
        for (y in 0 until h) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bmp
}
