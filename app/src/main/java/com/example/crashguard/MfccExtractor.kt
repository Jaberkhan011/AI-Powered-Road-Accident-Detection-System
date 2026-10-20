package com.example.crashguard

import kotlin.math.*

class MfccExtractor(
    private val sampleRate: Int = 8000,
    private val numMfccs: Int = 13,
    private val frameLength: Int = 200, // 25ms @ 8000Hz
    private val frameStep: Int = 80,    // 10ms @ 8000Hz
    private val numMelFilters: Int = 26,
    private val fftSize: Int = 256
) {

    private val melFilters: Array<FloatArray> = createMelFilters()

    private val hammingWindow: FloatArray = FloatArray(frameLength) { i ->
        (0.54 - 0.46 * cos(2.0 * PI * i / (frameLength - 1))).toFloat()
    }

    fun extract(audio: FloatArray, targetFrames: Int): FloatArray {

        val neededSamples = (targetFrames - 1) * frameStep + frameLength
        val paddedAudio = FloatArray(max(audio.size, neededSamples))
        System.arraycopy(audio, 0, paddedAudio, 0, min(audio.size, paddedAudio.size))

        val mfccFeatures = FloatArray(targetFrames * numMfccs)

        for (f in 0 until targetFrames) {

            val start = f * frameStep
            val frame = FloatArray(fftSize)

            for (i in 0 until frameLength) {
                if (start + i < paddedAudio.size) {
                    frame[i] = paddedAudio[start + i] * hammingWindow[i]
                }
            }

            // FFT + Power Spectrum
            val powerSpectrum = calculatePowerSpectrum(frame)

            // Mel Filter Bank
            val melEnergies = FloatArray(numMelFilters)
            for (m in 0 until numMelFilters) {
                for (k in powerSpectrum.indices) {
                    melEnergies[m] += powerSpectrum[k] * melFilters[m][k]
                }

                if (melEnergies[m] < 1e-10f) melEnergies[m] = 1e-10f

                // Use log10 instead of ln
                melEnergies[m] = log10(melEnergies[m])
            }

            // DCT-II with orthonormal scaling
            for (i in 0 until numMfccs) {

                var sum = 0f
                for (m in 0 until numMelFilters) {
                    sum += melEnergies[m] *
                            cos(PI.toFloat() * i * (m + 0.5f) / numMelFilters)
                }

                val scale = if (i == 0) {
                    sqrt(1f / numMelFilters)
                } else {
                    sqrt(2f / numMelFilters)
                }

                mfccFeatures[f * numMfccs + i] = sum * scale
            }
        }

        // Global Mean-Variance Normalization
        val mean = mfccFeatures.average().toFloat()

        var variance = 0f
        for (v in mfccFeatures) {
            variance += (v - mean) * (v - mean)
        }
        variance /= mfccFeatures.size

        val std = sqrt(variance + 1e-8f)

        for (i in mfccFeatures.indices) {
            mfccFeatures[i] = (mfccFeatures[i] - mean) / std
        }

        return mfccFeatures
    }

    private fun calculatePowerSpectrum(frame: FloatArray): FloatArray {

        val real = frame.copyOf()
        val imag = FloatArray(fftSize)

        fft(real, imag)

        val power = FloatArray(fftSize / 2 + 1)

        for (i in power.indices) {
            // Removed division by fftSize (librosa-like)
            power[i] = (real[i] * real[i] + imag[i] * imag[i])
        }

        return power
    }

    private fun fft(real: FloatArray, imag: FloatArray) {

        val n = real.size
        var j = 0

        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }

            var m = n shr 1
            while (m >= 1 && j >= m) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        var size = 2
        while (size <= n) {

            val halfSize = size shr 1
            val angle = -2.0 * PI / size
            val wR = cos(angle).toFloat()
            val wI = sin(angle).toFloat()

            for (i in 0 until n step size) {

                var currWR = 1f
                var currWI = 0f

                for (k in 0 until halfSize) {

                    val r = real[i + k + halfSize]
                    val im = imag[i + k + halfSize]

                    val tr = r * currWR - im * currWI
                    val ti = r * currWI + im * currWR

                    real[i + k + halfSize] = real[i + k] - tr
                    imag[i + k + halfSize] = imag[i + k] - ti

                    real[i + k] += tr
                    imag[i + k] += ti

                    val nextWR = currWR * wR - currWI * wI
                    currWI = currWR * wI + currWI * wR
                    currWR = nextWR
                }
            }
            size = size shl 1
        }
    }

    private fun createMelFilters(): Array<FloatArray> {

        val filters = Array(numMelFilters) { FloatArray(fftSize / 2 + 1) }

        val lowMel = freqToMel(0f)
        val highMel = freqToMel(sampleRate / 2f)

        val melPoints = FloatArray(numMelFilters + 2) { i ->
            lowMel + i * (highMel - lowMel) / (numMelFilters + 1)
        }

        val binPoints = IntArray(numMelFilters + 2) { i ->
            ((fftSize + 1) * melToFreq(melPoints[i]) / sampleRate).toInt()
        }

        for (m in 1..numMelFilters) {

            for (k in binPoints[m - 1] until binPoints[m]) {
                filters[m - 1][k] =
                    (k - binPoints[m - 1]).toFloat() /
                            (binPoints[m] - binPoints[m - 1])
            }

            for (k in binPoints[m] until binPoints[m + 1]) {
                filters[m - 1][k] =
                    (binPoints[m + 1] - k).toFloat() /
                            (binPoints[m + 1] - binPoints[m])
            }
        }

        return filters
    }

    private fun freqToMel(freq: Float) =
        2595f * log10(1 + freq / 700f)

    private fun melToFreq(mel: Float) =
        700f * (10.0.pow((mel / 2595f).toDouble()) - 1).toFloat()
}

