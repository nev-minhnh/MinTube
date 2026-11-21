package com.hhst.youtubelite.tv.search

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hhst.youtubelite.R
import com.hhst.youtubelite.diagnostics.AppLog
import com.hhst.youtubelite.ui.showMessage

/**
 * Voice search for TV, mirroring the phone's fallback order (NativeActivity.openVoiceSearch):
 * the system recognizer activity (the remote's mic on Android TV) when one resolves, else an
 * in-process [SpeechRecognizer] behind the RECORD_AUDIO prompt. [available] decides whether the
 * Search screen shows the voice button at all. Construct during the activity's onCreate.
 */
class TvVoiceSearch(private val activity: ComponentActivity, private val onResult: (String) -> Unit) {
    private var recognizer: SpeechRecognizer? = null
    private var pending: Intent? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.trim()?.takeIf(String::isNotEmpty)?.let(onResult)
        }
    }

    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val intent = pending
        pending = null
        if (granted && intent != null && !activity.isFinishing && !activity.isDestroyed) listen(intent)
    }

    init {
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) clear()
        })
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

    private fun activityResolves(): Boolean =
        runCatching { intent().resolveActivity(activity.packageManager) != null }.getOrDefault(false)

    val available: Boolean
        get() = activityResolves() || runCatching { SpeechRecognizer.isRecognitionAvailable(activity) }.getOrDefault(false)

    fun start() {
        clear()
        val intent = intent()
        if (activityResolves()) {
            try {
                launcher.launch(intent)
                return
            } catch (_: ActivityNotFoundException) { }
        }
        if (!runCatching { SpeechRecognizer.isRecognitionAvailable(activity) }.getOrDefault(false)) return unavailable()
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pending = intent
            permission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        listen(intent)
    }

    private fun listen(intent: Intent) {
        clear()
        try {
            val current = SpeechRecognizer.createSpeechRecognizer(activity)
            recognizer = current
            current.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    if (recognizer !== current) return
                    clear()
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        ?.trim()?.takeIf(String::isNotEmpty)?.let(onResult) ?: unavailable()
                }
                override fun onError(error: Int) {
                    if (recognizer !== current) return
                    clear()
                    unavailable()
                }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            current.startListening(intent)
        } catch (failure: RuntimeException) {
            clear()
            AppLog.event(AppLog.Category.APP, "tv_voice_search_unavailable", failure = failure)
            unavailable()
        }
    }

    private fun clear() {
        val current = recognizer
        recognizer = null
        runCatching { current?.cancel() }
        runCatching { current?.destroy() }
    }

    private fun unavailable() {
        activity.showMessage(activity.getString(R.string.application_not_found))
    }
}
