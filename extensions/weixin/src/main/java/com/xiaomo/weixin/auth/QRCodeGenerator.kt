/**
 * Local QR code bitmap generation using ZXing.
 *
 * The Weixin API returns a web page URL in qrcode_img_content, not an image.
 * We need to generate the QR image locally from the raw qrcode string.
 */
package com.xiaomo.weixin.auth

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QRCodeGenerator {

    /**
     * Generate a QR code Bitmap from the given content string.
     *
     * @param content The data to encode (e.g. the weixin qrcode_img_content URL)
     * @param size Width and height in pixels (default 512)
     * @return Bitmap or null on failure
     */
    fun generate(content: String, size: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
