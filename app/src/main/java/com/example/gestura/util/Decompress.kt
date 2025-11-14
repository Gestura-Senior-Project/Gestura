// util/Decompress.kt
package com.example.gestura.util

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

object Decompress {
    fun decodeFloatArray(b64: String, count: Int): FloatArray {
        val compressed = Base64.decode(b64, Base64.DEFAULT)
        val gis = GZIPInputStream(ByteArrayInputStream(compressed))
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var n: Int
        while (gis.read(buf).also { n = it } != -1) out.write(buf, 0, n)
        gis.close()
        val bytes = out.toByteArray()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN) // Python default is big-endian for bytes? If you used .tobytes(), it's native-endian. If needed, set to LITTLE_ENDIAN on both sides.
        bb.order(ByteOrder.LITTLE_ENDIAN) // <- set this to match server. If server is native little-endian (most), keep LITTLE.
        val fa = FloatArray(count)
        for (i in 0 until count) fa[i] = bb.float
        return fa
    }
}
