package com.peyi.sonypodsbattery

import rikka.shizuku.ShizukuProvider

/**
 * Shizuku's provider, plus: starts listening for Shizuku as soon as the process starts. When Shizuku
 * comes back it can start SunPods just to hand it its service, with no screen open, so this is the
 * one place that always runs.
 */
class SunpodsShizukuProvider : ShizukuProvider() {
    override fun onCreate(): Boolean {
        val ok = super.onCreate()
        context?.let { ShizukuWatch.start(it) }
        return ok
    }
}
