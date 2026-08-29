package com.codigitech.belay

import android.app.Application
import com.zoho.catalyst.setup.ZCatalystApp
import com.zoho.catalyst.setup.ZCatalystSDKConfigs
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BelayApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    val environment =
      if (BuildConfig.CATALYST_ENV == "PRODUCTION") {
        ZCatalystSDKConfigs.Environment.PRODUCTION
      } else {
        ZCatalystSDKConfigs.Environment.DEVELOPMENT
      }
    ZCatalystApp.init(this, environment)
  }
}
