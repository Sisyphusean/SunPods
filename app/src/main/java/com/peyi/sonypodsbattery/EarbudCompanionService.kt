package com.peyi.sonypodsbattery

import android.companion.CompanionDeviceService

class EarbudCompanionService : CompanionDeviceService() {
    @Suppress("DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        EarbudCompanionManager.handleDeviceAppeared(applicationContext, address)
    }

    @Suppress("DEPRECATION")
    override fun onDeviceDisappeared(address: String) {
        EarbudCompanionManager.handleDeviceDisappeared(applicationContext, address)
    }
}
