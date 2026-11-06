package com.qasim.nexa.launcher

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class BixSigmaVoiceInteractionService : VoiceInteractionService()

class BixSigmaVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle): VoiceInteractionSession = BixSigmaVoiceInteractionSession(this)
}

private class BixSigmaVoiceInteractionSession(private val hostContext: Context) : VoiceInteractionSession(hostContext) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val launch = Intent(hostContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_START_AI_VOICE, true)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startAssistantActivity(launch)
            else hostContext.startActivity(launch)
        } catch (_: Exception) {
            try { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); hostContext.startActivity(launch) }
            catch (_: Exception) { }
        }
        finish()
    }
}
