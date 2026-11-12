package com.yoonbin.tts

import android.app.Activity
import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var langGroup: RadioGroup
    private var rate = 1.0f
    private var ready = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        input = EditText(this).apply {
            hint = "읽을 텍스트 / Text to read"
            minLines = 8; gravity = android.view.Gravity.TOP
        }
        langGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("자동", "한국어", "English").forEachIndexed { i, s ->
            langGroup.addView(RadioButton(this).apply { text = s; id = i + 1 })
        }
        langGroup.check(1)
        val speedLabel = TextView(this).apply { text = "속도 1.0x" }
        val seek = SeekBar(this).apply {
            max = 15; progress = 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    rate = 0.5f + p * 0.1f
                    speedLabel.text = "속도 " + String.format(Locale.US, "%.1f", rate) + "x"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        val play = Button(this).apply { text = "재생"; setOnClickListener { speak() } }
        val stop = Button(this).apply { text = "정지"; setOnClickListener { tts.stop() } }
        val save = Button(this).apply { text = "음성 파일로 저장 (Music/TTS)"; setOnClickListener { saveFile() } }
        status = TextView(this)
        listOf(input, langGroup, speedLabel, seek, play, stop, save, status).forEach {
            root.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        tts = TextToSpeech(this, this)
    }

    override fun onInit(code: Int) {
        ready = code == TextToSpeech.SUCCESS
        if (!ready) { status.text = "TTS 엔진 초기화 실패. 폰에 TTS 엔진이 설치되어 있는지 확인 필요."; return }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == "save") runOnUiThread { export() }
            }
            @Deprecated("deprecated") override fun onError(id: String?) {
                runOnUiThread { status.text = "오류 발생" }
            }
        })
        status.text = "준비 완료"
    }

    private fun locale(text: String): Locale = when (langGroup.checkedRadioButtonId) {
        2 -> Locale.KOREAN
        3 -> Locale.ENGLISH
        else -> {
            val ko = text.count { it in '\uAC00'..'\uD7A3' || it in '\u3131'..'\u318E' }
            val en = text.count { it in 'A'..'Z' || it in 'a'..'z' }
            if (ko >= en) Locale.KOREAN else Locale.ENGLISH
        }
    }

    private fun prepare(text: String): Boolean {
        if (!ready) { status.text = "엔진 준비 안 됨"; return false }
        if (text.isBlank()) { status.text = "텍스트가 비어 있음"; return false }
        val r = tts.setLanguage(locale(text))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            status.text = "해당 언어 음성 데이터 없음. 설정 > TTS에서 언어 데이터 설치 필요."
            return false
        }
        tts.setSpeechRate(rate)
        return true
    }

    private fun speak() {
        val text = input.text.toString()
        if (!prepare(text)) return
        val max = TextToSpeech.getMaxSpeechInputLength() - 100
        tts.stop()
        text.chunked(max).forEachIndexed { i, c ->
            tts.speak(c, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "p$i")
        }
        status.text = "재생 중 (" + locale(text).displayLanguage + ")"
    }

    private fun saveFile() {
        val text = input.text.toString()
        if (!prepare(text)) return
        if (text.length >= TextToSpeech.getMaxSpeechInputLength()) {
            status.text = "파일 저장은 ${TextToSpeech.getMaxSpeechInputLength()}자 미만만 가능"; return
        }
        status.text = "변환 중..."
        tts.synthesizeToFile(text, Bundle(), File(cacheDir, "tts.wav"), "save")
    }

    private fun export() {
        try {
            val name = "tts_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".wav"
            val v = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/TTS")
            }
            val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v)!!
            contentResolver.openOutputStream(uri)!!.use { o ->
                File(cacheDir, "tts.wav").inputStream().use { it.copyTo(o) }
            }
            status.text = "저장 완료: Music/TTS/$name"
        } catch (e: Exception) {
            status.text = "저장 실패: " + e.message
        }
    }

    override fun onDestroy() { tts.shutdown(); super.onDestroy() }
}
