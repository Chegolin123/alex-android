package com.alex.app.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File

/** Запись с микрофона в WAV 16 кГц mono PCM16 — формат, который понимает сервер. */
class VoiceRecorder {

    private var recorder: AudioRecord? = null
    private var recording = false
    private val pcm = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun start() {
        val rate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, 4096)
        )
        pcm.reset()
        recording = true
        recorder?.startRecording()
        Thread {
            val buffer = ByteArray(4096)
            while (recording) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) pcm.write(buffer, 0, read)
            }
        }.start()
    }

    fun stop(): ByteArray {
        recording = false
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        return wavBytes(pcm.toByteArray(), 16000)
    }

    fun cancel() {
        recording = false
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
    }

    private fun wavBytes(pcm: ByteArray, rate: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val dataSize = pcm.size
        val total = dataSize + 36
        fun le16(v: Int) { out.write(v); out.write(v shr 8) }
        fun le32(v: Long) {
            out.write((v and 0xFF).toInt()); out.write(((v shr 8) and 0xFF).toInt())
            out.write(((v shr 16) and 0xFF).toInt()); out.write(((v shr 24) and 0xFF).toInt())
        }
        out.write('R'.code); out.write('I'.code); out.write('F'.code); out.write('F'.code)
        le32(total.toLong())
        out.write('W'.code); out.write('A'.code); out.write('V'.code); out.write('E'.code)
        out.write('f'.code); out.write('m'.code); out.write('t'.code); out.write(' '.code)
        le32(16); le16(1); le16(1); le32(rate.toLong())
        le32((rate * 2).toLong()); le16(2); le16(16)
        out.write('d'.code); out.write('a'.code); out.write('t'.code); out.write('a'.code)
        le32(dataSize.toLong())
        out.write(pcm)
        return out.toByteArray()
    }

    companion object {
        fun wavToFile(wav: ByteArray, dir: File, name: String = "reply.wav"): File {
            val file = File(dir, name)
            file.writeBytes(wav)
            return file
        }
    }
}
