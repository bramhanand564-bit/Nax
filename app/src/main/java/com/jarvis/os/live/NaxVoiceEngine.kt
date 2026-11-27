package com.jarvis.os.live

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

data class IndicVoiceLang(val code: String, val label: String)

object NaxVoiceEngine {

    val supportedIndicLanguages = listOf(
        IndicVoiceLang("hi-IN", "Hindi / Hinglish (हिंदी)"),
        IndicVoiceLang("en-IN", "English (India)"),
        IndicVoiceLang("mr-IN", "Marathi (मराठी)"),
        IndicVoiceLang("ta-IN", "Tamil (தமிழ்)"),
        IndicVoiceLang("te-IN", "Telugu (తెలుగు)"),
        IndicVoiceLang("bn-IN", "Bengali (বাংলা)"),
        IndicVoiceLang("gu-IN", "Gujarati (ગુજરાતી)"),
        IndicVoiceLang("kn-IN", "Kannada (ಕನ್ನಡ)"),
        IndicVoiceLang("ml-IN", "Malayalam (മലയാളം)"),
        IndicVoiceLang("pa-IN", "Punjabi (ਪੰਜਾਬੀ)"),
        IndicVoiceLang("or-IN", "Odia (ଓଡ଼ିଆ)"),
        IndicVoiceLang("as-IN", "Assamese (অসমীয়া)")
    )

    private var tts: TextToSpeech? = null
    private var isReady: Boolean = false
    private var currentLangTag: String = "hi-IN"

    fun init(context: Context) {
        if (tts != null) return
        val appCtx = context.applicationContext
        currentLangTag = getSavedLang(appCtx)
        tts = TextToSpeech(appCtx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                applyLocale(currentLangTag)
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(1.05f)
            }
        }
    }

    fun isVoiceEnabled(context: Context): Boolean {
        return context.getSharedPreferences("nax_voice_prefs", Context.MODE_PRIVATE)
            .getBoolean("voice_enabled", true)
    }

    fun setVoiceEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("nax_voice_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("voice_enabled", enabled)
            .apply()
        if (!enabled) stop()
    }

    fun getSavedLang(context: Context): String {
        return context.getSharedPreferences("nax_voice_prefs", Context.MODE_PRIVATE)
            .getString("voice_lang", "hi-IN") ?: "hi-IN"
    }

    fun setLanguage(context: Context, langTag: String) {
        currentLangTag = langTag
        context.getSharedPreferences("nax_voice_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("voice_lang", langTag)
            .apply()
        applyLocale(langTag)
    }

    private fun applyLocale(langTag: String) {
        val engine = tts ?: return
        val locale = Locale.forLanguageTag(langTag)
        val res = engine.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.forLanguageTag("en-IN"))
        }
    }

    fun speak(context: Context, rawText: String) {
        if (!isVoiceEnabled(context)) return
        if (tts == null) init(context)
        if (!isReady) return

        val clean = rawText
            .replace(Regex("""https?://\S+"""), "")
            .replace("**", "")
            .replace("*", "")
            .replace("#", "")
            .replace("•", ",")
            .replace(Regex("""\[[^\]]*]"""), "")
            .trim()
            .take(280)

        if (clean.isNotBlank()) {
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "nax_utterance_${System.currentTimeMillis()}")
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }
}
