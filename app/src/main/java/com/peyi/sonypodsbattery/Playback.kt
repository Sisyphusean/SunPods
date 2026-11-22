package com.peyi.sonypodsbattery

import android.content.Context
import android.media.AudioManager

/** What's playing, as the earbuds see it. */
data class NowPlaying(val title: String?, val playing: Boolean?)

/**
 * Playback through the earbuds (Sony's PLAY commands, table 1):
 *   GET_STATUS 0xa2 [0x01]           -> RET 0xa3 [0x01, enabled, state, ..]  state: 1 play, 2 pause, 3 stop
 *   SET_STATUS 0xa4 [0x01, 0x00, op] op: 0x01 pause, 0x02 next, 0x03 previous, 0x07 play
 *   GET_PARAM  0xa6 [0x01]           -> RET 0xa7 [0x01, nameStatus, len, title..]  nameStatus 2 = known
 * The earbuds pass these on to the phone, like a tap on them would.
 */
object Playback {
    private const val GET_STATUS = 0xa2
    private const val SET_STATUS = 0xa4
    private const val GET_PARAM = 0xa6
    private const val CONTROL = 0x01

    enum class Action(val code: Int) { PAUSE(0x01), NEXT(0x02), PREVIOUS(0x03), PLAY(0x07) }

    internal fun read(s: SonyBatteryClient.SonySession): NowPlaying {
        val status = s.request(bytes(GET_STATUS, CONTROL), timeoutMs = 700)
        val playing = status?.takeIf { it.size >= 4 }?.let {
            when (u(it[3])) { 0x01 -> true; 0x02, 0x03 -> false; else -> null }
        }
        val name = s.request(bytes(GET_PARAM, CONTROL), timeoutMs = 700)
        val title = name?.takeIf { it.size >= 4 && u(it[2]) == 0x02 }?.let {
            val length = u(it[3])
            if (it.size >= 4 + length) String(it, 4, length, Charsets.UTF_8).trim() else null
        }
        return NowPlaying(title?.ifBlank { null }, playing)
    }

    internal fun control(s: SonyBatteryClient.SonySession, action: Action) {
        s.command(bytes(SET_STATUS, CONTROL, 0x00, action.code))
    }

    /** Media volume as a fraction, through Android: the earbuds follow the phone's volume. */
    fun volume(context: Context): Float {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setVolume(context: Context, fraction: Float) {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction.coerceIn(0f, 1f) * max).toInt(), 0)
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun u(b: Byte) = b.toInt() and 0xff
}
