package com.pagewhisper.app.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Ambient sounds made on the device (nothing to download). Filtered noise plus event layers, rendered to a seamless 30 second loop.
 */
object AmbientDsp {
    const val SR = 22050
    private const val SEC = 30
    private const val N = SR * SEC
    private const val TAU = 2.0 * PI

    /** mulberry32, a small seeded random generator. */
    private class Rng(seed: Int) {
        private var a = seed
        fun next(): Double {
            a += 0x6D2B79F5
            var t = (a xor (a ushr 15)) * (1 or a)
            t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
        }
    }

    private fun lp(x: FloatArray, fc: Double): FloatArray {
        val a = (1.0 - exp(-TAU * fc / SR)).toFloat()
        val y = FloatArray(x.size)
        var s = 0f
        for (i in x.indices) { s += a * (x[i] - s); y[i] = s }
        return y
    }

    /** Cross-fades the tail into the head so the loop has no seam. */
    private fun loopify(x: FloatArray, off: Int, n: Int, f: Int): FloatArray {
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = x[off + i]
        for (i in 0 until f) {
            val t = i.toDouble() / f
            out[i] = (x[off + i] * sin(t * PI / 2) + x[off + n + i] * cos(t * PI / 2)).toFloat()
        }
        return out
    }

    private fun pNoise(r: Rng, lo: Double, hi: Double): FloatArray {
        val f = (SR * 1.5).toInt()
        val w0 = 3000
        val len = N + f + w0
        val w = FloatArray(len) { (r.next() * 2 - 1).toFloat() }
        val y = lp(w, hi)
        if (lo > 0.0) { val z = lp(w, lo); for (i in 0 until len) y[i] -= z[i] }
        return loopify(y, w0, N, f)
    }

    private fun norm(x: FloatArray): FloatArray {
        var e = 0.0
        for (v in x) e += v.toDouble() * v
        val g = (1.0 / sqrt(e / x.size + 1e-12)).toFloat()
        return FloatArray(x.size) { x[it] * g }
    }

    private fun addAt(out: FloatArray, pos: Double, sig: FloatArray, gain: Float) {
        val n = out.size
        var p = (floor(pos).toLong() % n).toInt()
        if (p < 0) p += n
        for (j in sig.indices) out[(p + j) % n] += sig[j] * gain
    }

    private fun finish(out: FloatArray): FloatArray {
        var e = 0.0
        for (v in out) e += v.toDouble() * v
        val g = 0.16 / sqrt(e / out.size + 1e-12)
        for (i in out.indices) out[i] = (tanh(out[i] * g * 1.1) * 0.92).toFloat()
        return out
    }

    private fun genRain(r: Rng, heavy: Boolean): FloatArray {
        val out = FloatArray(N)
        val hiss = norm(pNoise(r, if (heavy) 500.0 else 900.0, if (heavy) 7500.0 else 6500.0))
        val body = norm(pNoise(r, 0.0, if (heavy) 260.0 else 180.0))
        val slow = norm(pNoise(r, 0.0, 0.7))
        val hg = if (heavy) 1.0f else 0.45f
        val bg = if (heavy) 0.45f else 0.1f
        for (i in 0 until N) out[i] = hiss[i] * hg * (1f + 0.14f * slow[i]) + body[i] * bg
        val drops = floor((if (heavy) 240 else 95).toDouble() * SEC).toInt()
        for (d in 0 until drops) {
            val f = 1500 + r.next() * 4200
            val len = floor(SR * (0.005 + r.next() * 0.012)).toInt()
            val amp = (if (heavy) 0.5 else 0.8) + r.next() * (if (heavy) 1.2 else 2.2)
            val sig = FloatArray(len) { j -> (sin(TAU * f * j / SR) * exp(-j / (len * 0.3))).toFloat() }
            addAt(out, r.next() * N, sig, (amp * 0.5).toFloat())
        }
        return out
    }

    private fun genStorm(r: Rng): FloatArray {
        val out = genRain(r, true)
        for (t in doubleArrayOf(5.5, 16.0, 25.5)) {
            val len = SR * 7
            val w = FloatArray(len) { (r.next() * 2 - 1).toFloat() }
            val lo = lp(w, 120.0)
            val mid = lp(w, 700.0)
            val sig = FloatArray(len)
            for (j in 0 until len) {
                val s = j.toDouble() / SR
                val env = (if (s < 0.12) sin(s / 0.12 * PI / 2) else 1.0) * exp(-s / 2.2)
                sig[j] = ((lo[j] * 9 + (mid[j] - lo[j]) * 3 * exp(-s / 0.5)) * env).toFloat()
            }
            addAt(out, (t + (r.next() - 0.5)) * SR, norm(sig), 2.4f)
        }
        return out
    }

    private fun genOcean(r: Rng): FloatArray {
        val low = norm(pNoise(r, 0.0, 420.0))
        val mid = norm(pNoise(r, 250.0, 1700.0))
        val hiss = norm(pNoise(r, 2000.0, 8000.0))
        val out = FloatArray(N)
        val waves = 4
        val amps = DoubleArray(waves) { 0.75 + r.next() * 0.25 }
        fun sh(u: Double) = if (u < 0.55) sin(PI / 2 * u / 0.55).pow(2) else cos(PI / 2 * (u - 0.55) / 0.45).pow(2)
        for (i in 0 until N) {
            val t = i.toDouble() / N * waves
            val k = min(waves - 1, floor(t).toInt())
            val s = (sh(t - floor(t)) * amps[k]).toFloat()
            out[i] = low[i] * (0.3f + s) + mid[i] * (0.1f + 0.55f * s) + hiss[i] * 0.9f * s * s
        }
        return out
    }

    private fun genWind(r: Rng): FloatArray {
        val a = norm(pNoise(r, 90.0, 800.0))
        val b = norm(pNoise(r, 450.0, 1300.0))
        val out = FloatArray(N)
        val ph = doubleArrayOf(r.next() * TAU, r.next() * TAU, r.next() * TAU)
        for (i in 0 until N) {
            val t = i.toDouble() / N
            val g = max(0.3, 0.68 + 0.22 * sin(TAU * 3 * t + ph[0]) + 0.12 * sin(TAU * 7 * t + ph[1]) + 0.07 * sin(TAU * 13 * t + ph[2]))
            out[i] = (a[i] * g.pow(1.4) + b[i] * 0.4 * g.pow(3.0)).toFloat()
        }
        return out
    }

    private fun genInsects(r: Rng): FloatArray {
        val out = FloatArray(N)
        val air = norm(pNoise(r, 0.0, 2500.0))
        for (i in 0 until N) out[i] = air[i] * 0.04f
        for (c in 0 until 7) { // crickets: short trains of pulses, each with its own pitch and rhythm
            val f = 3700 + r.next() * 1500
            val k = 34 + floor(r.next() * 26).toInt()
            val period = N.toDouble() / k
            val phase = r.next() * period
            val pulses = 3 + floor(r.next() * 3).toInt()
            val gap = SR * (0.017 + r.next() * 0.008)
            val plen = floor(SR * 0.011).toInt()
            val amp = 0.22 + r.next() * 0.4
            val m = 1 + floor(r.next() * 4).toInt()
            val ph = r.next() * TAU
            val pulse = FloatArray(plen) { j -> (sin(TAU * f * j / SR) * sin(PI * j / plen).pow(2)).toFloat() }
            for (q in 0 until k) {
                val base = phase + q * period + (r.next() - 0.5) * period * 0.04
                val vol = amp * (0.55 + 0.45 * sin(TAU * m * q / k + ph))
                for (p in 0 until pulses) addAt(out, base + p * gap, pulse, (vol * (1 - 0.07 * p)).toFloat())
            }
        }
        for (c in 0 until 2) { // trillers
            val f = 2500 + r.next() * 900
            val k = 8 + floor(r.next() * 4).toInt()
            val period = N.toDouble() / k
            val burst = floor(SR * (1.2 + r.next() * 0.6)).toInt()
            val phase = r.next() * period
            val amp = 0.2 + r.next() * 0.15
            val sig = FloatArray(burst) { j ->
                (sin(TAU * f * j / SR) * sin(PI * j / burst).pow(0.6) * (0.5 + 0.5 * sin(TAU * 42 * j / SR))).toFloat()
            }
            for (q in 0 until k) addAt(out, phase + q * period, sig, amp.toFloat())
        }
        return out
    }

    private fun genFire(r: Rng): FloatArray {
        val bed = norm(pNoise(r, 0.0, 420.0))
        val slow = norm(pNoise(r, 0.0, 1.2))
        val out = FloatArray(N)
        for (i in 0 until N) out[i] = bed[i] * (0.35f + 0.1f * slow[i])
        for (c in 0 until 26 * SEC) {
            val len = floor(SR * (0.001 + r.next() * 0.003)).toInt()
            val sig = FloatArray(len) { j -> ((r.next() * 2 - 1) * exp(-j / (len * 0.4))).toFloat() }
            addAt(out, r.next() * N, sig, (0.8 + r.next() * 1.6).toFloat())
        }
        for (c in 0 until 42) {
            val len = floor(SR * (0.01 + r.next() * 0.02)).toInt()
            val f = 600 + r.next() * 1800
            val sig = FloatArray(len) { j -> ((sin(TAU * f * j / SR) * 0.6 + (r.next() * 2 - 1) * 0.7) * exp(-j / (len * 0.25))).toFloat() }
            addAt(out, r.next() * N, sig, (2 + r.next() * 3).toFloat())
        }
        return out
    }

    fun generate(key: String): ShortArray {
        val seed = mapOf("rain" to 11, "heavy" to 12, "storm" to 13, "ocean" to 14, "insects" to 15, "forest" to 16, "wind" to 17, "fire" to 18, "brown" to 19)[key] ?: 1
        val r = Rng(seed)
        val raw = when (key) {
            "rain" -> genRain(r, false)
            "heavy" -> genRain(r, true)
            "storm" -> genStorm(r)
            "ocean" -> genOcean(r)
            "insects" -> genInsects(r)
            "forest" -> { val a = norm(genInsects(r)); val b = norm(genWind(r)); FloatArray(N) { a[it] + b[it] * 0.35f } }
            "wind" -> genWind(r)
            "fire" -> genFire(r)
            else -> norm(pNoise(r, 0.0, 160.0))
        }
        val f = finish(raw)
        return ShortArray(f.size) { (f[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
    }
}

/** Plays the chosen ambient loop on its own audio track, mixed with the narration. */
object Ambient {
    private val cache = HashMap<String, ShortArray>()
    private var track: AudioTrack? = null
    private var loaded = ""
    private var playing = false
    private var genJob: Job? = null

    @Synchronized
    fun sync(scope: CoroutineScope, key: String, volume: Float, want: Boolean, onReady: () -> Unit) {
        if (key == "off" || !want) { pauseLocked(); return }
        val data = cache[key]
        if (data == null) {
            if (genJob?.isActive != true) {
                genJob = scope.launch(Dispatchers.Default) {
                    val d = AmbientDsp.generate(key)
                    synchronized(this@Ambient) { cache[key] = d }
                    onReady()
                }
            }
            return
        }
        if (loaded != key) {
            releaseLocked()
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(AmbientDsp.SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(data, 0, data.size)
            t.setLoopPoints(0, data.size, -1)
            track = t
            loaded = key
            playing = false
        }
        track?.setVolume(volume.coerceIn(0f, 1f))
        if (!playing) { track?.play(); playing = true }
    }

    private fun pauseLocked() {
        if (playing) { runCatching { track?.pause() }; playing = false }
    }

    private fun releaseLocked() {
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null; loaded = ""; playing = false
    }

    @Synchronized fun setVolume(v: Float) { track?.setVolume(v.coerceIn(0f, 1f)) }
    @Synchronized fun release() = releaseLocked()
}
