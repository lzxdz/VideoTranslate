package ru.videotranslate

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*

class SettingsActivity : Activity() {
    private lateinit var root: LinearLayout
    private val p by lazy { AppPrefs.prefs(this) }
    private val c: AppPrefs.Palette get() = AppPrefs.palette(this)

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }

    private fun render() {
        window.statusBarColor = c.background
        window.navigationBarColor = c.background
        window.decorView.systemUiVisibility = if (c.dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(c.background)
        }
        root.addView(TextView(this).apply {
            text = "Настройки"; textSize = 30f
            typeface = Typeface.create("sans-serif", Typeface.BOLD); setTextColor(c.text)
        })
        root.addView(TextView(this).apply {
            text = "Настрой перевод под себя"; textSize = 14f; setTextColor(c.muted)
            setPadding(0, dp(3), 0, dp(10))
        })

        section("ВНЕШНИЙ ВИД")
        panel().apply {
            addView(label("Тема приложения"))
            addView(spinner(listOf("Как на телефоне", "Светлая", "Тёмная", "Midnight", "Ocean", "Forest"),
                listOf("system", "light", "dark", "midnight", "ocean", "forest"), AppPrefs.theme(this@SettingsActivity)) {
                p.edit().putString(AppPrefs.THEME, it).apply()
                recreate()
            })
            addView(hint("Системная тема автоматически следует настройке Android."))
        }.also { root.addView(it, params(8)) }

        section("РАСПОЗНАВАНИЕ РЕЧИ")
        panel().apply {
            addView(label("Движок распознавания"))
            addView(spinner(listOf("Whisper tiny.en · локально", "Google / системное распознавание · микрофон"),
                listOf("whisper", "google"), p.getString(AppPrefs.RECOGNITION_ENGINE, "whisper") ?: "whisper") {
                p.edit().putString(AppPrefs.RECOGNITION_ENGINE, it).apply()
                recreate()
            })
            if (p.getString(AppPrefs.RECOGNITION_ENGINE, "whisper") == "google") {
                addView(hint("Системное распознавание использует микрофон и сервис распознавания речи, установленный на телефоне. Для захвата внутреннего звука другого приложения выбирай Whisper. Интернет и наличие Google Speech Services могут требоваться."))
            } else {
                addView(hint("Whisper распознаёт английскую речь локально. На первом запуске скачивается модель; скорость зависит от процессора и памяти телефона."))
            }
        }.also { root.addView(it, params(8)) }

        section("ПЕРЕВОД ТЕКСТА")
        panel().apply {
            addView(label("Движок перевода"))
            addView(spinner(listOf("Google ML Kit · локально", "API ИИ · отдельное подключение"),
                listOf("google", "ai"), p.getString(AppPrefs.ENGINE, "google") ?: "google") {
                p.edit().putString(AppPrefs.ENGINE, it).apply()
                recreate()
            })
            addView(hint(if (p.getString(AppPrefs.ENGINE, "google") == "ai")
                "Перевод отправляется выбранному API-провайдеру. Скорость зависит от сети и модели."
                else "После загрузки языковой модели перевод выполняется на устройстве без отправки текста в облако."))
        }.also { root.addView(it, params(8)) }

        section("API ИИ")
        panel().apply {
            addView(label("Адрес API (OpenAI-compatible)"))
            addView(edit(p.getString(AppPrefs.AI_BASE_URL, "https://api.openai.com/v1") ?: "", "https://api.openai.com/v1").also { it.tag = "base" })
            addView(label("Модель"))
            addView(edit(p.getString(AppPrefs.AI_MODEL, "gpt-4o-mini") ?: "", "Например, gpt-4o-mini").also { it.tag = "model" })
            addView(label("API-ключ"))
            addView(edit(p.getString(AppPrefs.AI_API_KEY, "") ?: "", "Вставь свой ключ", true).also { it.tag = "key" })
            addView(actionButton("Сохранить настройки API", true) {
                val base = findEdit("base")?.text?.toString()?.trim().orEmpty()
                val model = findEdit("model")?.text?.toString()?.trim().orEmpty()
                val key = findEdit("key")?.text?.toString()?.trim().orEmpty()
                if (!base.startsWith("https://") || model.isBlank() || key.isBlank()) {
                    Toast.makeText(this@SettingsActivity, "Нужны HTTPS-адрес, модель и API-ключ", Toast.LENGTH_LONG).show()
                } else {
                    p.edit().putString(AppPrefs.AI_BASE_URL, base.trimEnd('/'))
                        .putString(AppPrefs.AI_MODEL, model).putString(AppPrefs.AI_API_KEY, key).apply()
                    Toast.makeText(this@SettingsActivity, "Настройки API сохранены", Toast.LENGTH_SHORT).show()
                }
            }, 12)
            addView(hint("Ключ хранится в настройках приложения и не зашифрован. Не публикуй его и не отправляй в репозиторий."))
        }.also { root.addView(it, params(8)) }

        section("РУССКАЯ ОЗВУЧКА")
        panel().apply {
            addView(label("Громкость голоса VideoTranslate"))
            addView(hint("Увеличивает или уменьшает только переведённую речь. Громкость исходного видео регулируется системой или самим плеером."))
            val volumeText = TextView(this@SettingsActivity).apply {
                text = "${(p.getFloat(AppPrefs.TTS_VOLUME, 1f) * 100).toInt()}%"
                textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(c.accent)
            }
            addView(volumeText)
            addView(SeekBar(this@SettingsActivity).apply {
                max = 100
                progress = (p.getFloat(AppPrefs.TTS_VOLUME, 1f) * 100).toInt().coerceIn(0, 100)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        volumeText.text = "$progress%"
                        if (fromUser) p.edit().putFloat(AppPrefs.TTS_VOLUME, progress / 100f).apply()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            })
            addView(label("Скорость речи"))
            val rateText = TextView(this@SettingsActivity).apply {
                text = String.format(java.util.Locale.US, "%.1fx", p.getFloat(AppPrefs.TTS_RATE, 1f))
                textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(c.accent)
            }
            addView(rateText)
            addView(SeekBar(this@SettingsActivity).apply {
                max = 15
                progress = ((p.getFloat(AppPrefs.TTS_RATE, 1f) - .7f) * 10).toInt().coerceIn(0, 15)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val value = .7f + progress / 10f
                        rateText.text = String.format(java.util.Locale.US, "%.1fx", value)
                        if (fromUser) p.edit().putFloat(AppPrefs.TTS_RATE, value).apply()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            })
            addView(label("Длина аудиофрагмента Whisper"))
            addView(spinner(listOf("2 секунды · быстрее", "3 секунды · баланс", "5 секунд · больше контекста"),
                listOf("2", "3", "5"), p.getInt(AppPrefs.CHUNK_SECONDS, 3).toString()) {
                p.edit().putInt(AppPrefs.CHUNK_SECONDS, it.toInt()).apply()
            })
            addView(actionButton("Настройки голоса Android", false) {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            }, 8)
        }.also { root.addView(it, params(8)) }

        section("ПЛАВАЮЩАЯ ПАНЕЛЬ")
        panel().apply {
            addView(label("Управление поверх видео"))
            addView(hint("Панель содержит запуск/остановку и регулятор громкости русского голоса. Разреши показ поверх окон. Для запуска Android может попросить подтвердить захват звука."))
            addView(actionButton(if (Settings.canDrawOverlays(this@SettingsActivity)) "Включить плавающую панель" else "Разрешить показ поверх окон", true) {
                if (!Settings.canDrawOverlays(this@SettingsActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    p.edit().putBoolean(AppPrefs.FLOATING_ENABLED, true).apply()
                    if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(Intent(this@SettingsActivity, FloatingControlService::class.java))
                    else startService(Intent(this@SettingsActivity, FloatingControlService::class.java))
                    Toast.makeText(this@SettingsActivity, "Панель включена", Toast.LENGTH_SHORT).show()
                }
            }, 10)
            addView(actionButton("Выключить плавающую панель", false) {
                p.edit().putBoolean(AppPrefs.FLOATING_ENABLED, false).apply()
                stopService(Intent(this@SettingsActivity, FloatingControlService::class.java))
            }, 8)
        }.also { root.addView(it, params(8)) }

        root.addView(actionButton("Готово", true) { finish() }, params(20))
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun section(text: String) { root.addView(TextView(this).apply {
        this.text = text; textSize = 11f; letterSpacing = .14f
        typeface = Typeface.DEFAULT_BOLD; setTextColor(c.muted)
    }, params(20)) }
    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(16))
        background = rounded(c.surface, 20)
    }
    private fun label(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(c.text); setPadding(0, dp(8), 0, dp(3))
    }
    private fun hint(text: String) = TextView(this).apply {
        this.text = text; textSize = 12f; setTextColor(c.muted)
        setLineSpacing(dp(3).toFloat(), 1f); setPadding(0, dp(6), 0, dp(8))
    }
    private fun edit(value: String, hintText: String, password: Boolean = false) = EditText(this).apply {
        setSingleLine(true); setText(value); hint = hintText; textSize = 14f
        setTextColor(c.text); setHintTextColor(c.muted); setPadding(dp(12), dp(11), dp(12), dp(11))
        background = rounded(c.background, 12)
        if (password) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    private fun actionButton(textValue: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = textValue; isAllCaps = false; textSize = 14f
        setTextColor(if (primary) Color.WHITE else c.text)
        background = rounded(if (primary) c.accent else c.background, 14)
        setOnClickListener { action() }
    }
    private fun findEdit(tagName: String): EditText? {
        fun search(v: View): EditText? {
            if (v is EditText && v.tag == tagName) return v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) {
                val found = search(v.getChildAt(i)); if (found != null) return found
            }
            return null
        }
        return search(root)
    }
    private fun spinner(labels: List<String>, values: List<String>, selected: String, onPick: (String) -> Unit): Spinner {
        val s = Spinner(this)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return super.getView(position, convertView, parent).also { row ->
                    (row as? TextView)?.setTextColor(c.text)
                    row.setPadding(dp(12), dp(8), dp(12), dp(8))
                }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return super.getDropDownView(position, convertView, parent).also { row ->
                    (row as? TextView)?.setTextColor(Color.BLACK)
                    row.setPadding(dp(14), dp(12), dp(14), dp(12))
                }
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        s.adapter = adapter
        val initialIndex = values.indexOf(selected).coerceAtLeast(0)
        s.setSelection(initialIndex, false)
        s.setPadding(dp(8), dp(5), dp(8), dp(5))
        s.background = rounded(c.background, 12)
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var lastPosition = initialIndex
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != lastPosition) {
                    lastPosition = position
                    values.getOrNull(position)?.let(onPick)
                }
            }
        }
        return s
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun params(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
