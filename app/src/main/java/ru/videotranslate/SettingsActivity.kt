package ru.videotranslate

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class SettingsActivity : Activity() {
    private lateinit var root: LinearLayout
    private val p by lazy { AppPrefs.prefs(this) }
    private val c get() = AppPrefs.palette(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        window.statusBarColor = c.background
        window.navigationBarColor = c.background
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(c.background)
        }
        root.addView(TextView(this).apply {
            text = "Настройки"
            textSize = 29f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(c.text)
        })
        root.addView(label("ВНЕШНИЙ ВИД"), params(18))
        root.addView(card().apply {
            addView(labelIn("Тема приложения"))
            addView(spinner(listOf("Как на телефоне", "Светлая", "Тёмная", "Midnight", "Ocean", "Forest"),
                listOf("system", "light", "dark", "midnight", "ocean", "forest"), AppPrefs.theme(this@SettingsActivity)) {
                p.edit().putString(AppPrefs.THEME, it).apply()
                render()
            })
            addView(labelIn("Системная тема автоматически переключает светлый и тёмный режим."))
        }, params(8))

        root.addView(label("ПЕРЕВОД"), params(20))
        root.addView(card().apply {
            addView(labelIn("Движок перевода"))
            addView(spinner(listOf("Google ML Kit · на устройстве", "ИИ · совместимый API"),
                listOf("google", "ai"), p.getString(AppPrefs.ENGINE, "google") ?: "google") {
                p.edit().putString(AppPrefs.ENGINE, it).apply()
                render()
            })
            val isAi = p.getString(AppPrefs.ENGINE, "google") == "ai"
            if (isAi) {
                addView(labelIn("Адрес API (OpenAI-compatible)"))
                addView(edit(p.getString(AppPrefs.AI_BASE_URL, "https://api.openai.com/v1") ?: "", "https://api.openai.com/v1").also {
                    it.tag = "base"
                })
                addView(labelIn("Модель"))
                addView(edit(p.getString(AppPrefs.AI_MODEL, "gpt-4o-mini") ?: "", "gpt-4o-mini").also { it.tag = "model" })
                addView(labelIn("API-ключ"))
                addView(edit(p.getString(AppPrefs.AI_API_KEY, "") ?: "", "Вставь свой API-ключ", true).also { it.tag = "key" })
                addView(Button(this@SettingsActivity).apply {
                    text = "Сохранить подключение ИИ"
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    background = rounded(c.accent, 14)
                    setOnClickListener {
                        val base = findEdit("base")?.text?.toString()?.trim().orEmpty()
                        val model = findEdit("model")?.text?.toString()?.trim().orEmpty()
                        val key = findEdit("key")?.text?.toString()?.trim().orEmpty()
                        if (!base.startsWith("https://") || model.isBlank() || key.isBlank()) {
                            Toast.makeText(this@SettingsActivity, "Укажи HTTPS-адрес, модель и API-ключ", Toast.LENGTH_LONG).show()
                        } else {
                            p.edit().putString(AppPrefs.AI_BASE_URL, base.trimEnd('/'))
                                .putString(AppPrefs.AI_MODEL, model).putString(AppPrefs.AI_API_KEY, key).apply()
                            Toast.makeText(this@SettingsActivity, "Настройки ИИ сохранены", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, params(10))
                addView(labelIn("ИИ требует собственного API-ключа и может отправлять распознанный текст выбранному провайдеру. Ключ хранится в настройках приложения, не в защищённом хранилище."))
            } else {
                addView(labelIn("Перевод работает локально после загрузки языковой модели."))
            }
        }, params(8))

        root.addView(label("РАСПОЗНАВАНИЕ И ОЗВУЧКА"), params(20))
        root.addView(card().apply {
            addView(labelIn("Размер аудиофрагмента"))
            addView(spinner(listOf("Быстро · 2 секунды", "Сбалансированно · 3 секунды", "Точнее по контексту · 5 секунд"),
                listOf("2", "3", "5"), (p.getInt(AppPrefs.CHUNK_SECONDS, 3)).toString()) {
                p.edit().putInt(AppPrefs.CHUNK_SECONDS, it.toInt()).apply()
            })
            addView(labelIn("Короткие фрагменты могут снизить задержку, но ухудшить распознавание фраз."))
            addView(labelIn("Скорость русского голоса"))
            val rate = SeekBar(this@SettingsActivity).apply {
                max = 15
                progress = ((p.getFloat(AppPrefs.TTS_RATE, 1.0f) - 0.7f) * 10).toInt().coerceIn(0, 15)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) p.edit().putFloat(AppPrefs.TTS_RATE, 0.7f + progress / 10f).apply()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            addView(rate)
        }, params(8))

        root.addView(label("ПЛАВАЮЩЕЕ ОКНО"), params(20))
        root.addView(card().apply {
            addView(labelIn("Кнопка поверх других приложений"))
            addView(labelIn("Позволяет быстро открыть управление переводом. Android всё равно может попросить подтвердить захват звука при запуске."))
            addView(Button(this@SettingsActivity).apply {
                text = if (Settings.canDrawOverlays(this@SettingsActivity)) "Включить / обновить плавающее окно" else "Разрешить показ поверх окон"
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = rounded(c.accent, 14)
                setOnClickListener {
                    if (!Settings.canDrawOverlays(this@SettingsActivity)) {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")))
                    } else {
                        p.edit().putBoolean(AppPrefs.FLOATING_ENABLED, true).apply()
                        startService(Intent(this@SettingsActivity, FloatingControlService::class.java))
                        Toast.makeText(this@SettingsActivity, "Плавающая кнопка включена", Toast.LENGTH_SHORT).show()
                    }
                }
            }, params(10))
            addView(Button(this@SettingsActivity).apply {
                text = "Выключить плавающее окно"
                isAllCaps = false
                setTextColor(c.text)
                background = rounded(c.background, 14)
                setOnClickListener {
                    p.edit().putBoolean(AppPrefs.FLOATING_ENABLED, false).apply()
                    stopService(Intent(this@SettingsActivity, FloatingControlService::class.java))
                }
            }, params(8))
        }, params(8))

        root.addView(label("ДАННЫЕ И МОДЕЛИ"), params(20))
        root.addView(card().apply {
            addView(labelIn("Whisper tiny.en распознаёт английскую речь локально. Первая загрузка требует интернет; точность и скорость зависят от устройства и качества аудио."))
            addView(Button(this@SettingsActivity).apply {
                text = "Открыть настройки синтеза речи Android"
                isAllCaps = false
                setTextColor(c.text)
                background = rounded(c.background, 14)
                setOnClickListener { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            }, params(8))
        }, params(8))

        root.addView(Button(this).apply {
            text = "Готово"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            background = rounded(c.accent, 16)
            setOnClickListener { finish() }
        }, params(20))

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun findEdit(tagName: String): EditText? {
        fun search(v: View): EditText? {
            if (v is EditText && v.tag == tagName) return v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) search(v.getChildAt(i))?.let { return it }
            return null
        }
        return search(root)
    }

    private fun spinner(labels: List<String>, values: List<String>, selected: String, onPick: (String) -> Unit): Spinner {
        val s = Spinner(this)
        s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        s.setSelection(values.indexOf(selected).coerceAtLeast(0))
        s.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            private var first = true
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (first) { first = false; return }
                onPick(values[position])
            }
        }
        return s
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = rounded(c.surface, 18)
    }
    private fun label(text: String) = TextView(this).apply {
        this.text = text; textSize = 11f; letterSpacing = .12f
        typeface = Typeface.DEFAULT_BOLD; setTextColor(c.muted)
    }
    private fun labelIn(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(c.text)
        setPadding(0, dp(7), 0, dp(7))
    }
    private fun edit(value: String, hint: String, password: Boolean = false) = EditText(this).apply {
        setSingleLine(true); setText(value); this.hint = hint; textSize = 14f
        setTextColor(c.text); setHintTextColor(c.muted); setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(c.background, 10)
        if (password) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun params(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
