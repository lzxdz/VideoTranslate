# VideoTranslate Android

Прототип Android-приложения для распознавания английской речи через микрофон, перевода через OpenAI-compatible Chat Completions API и озвучивания русского текста системным TTS.

**Важно:** это прототип. Он не захватывает внутренний звук YouTube напрямую: звук должен быть слышен через динамик и попадать в микрофон. Нужен HTTPS endpoint и API key совместимого API. Не вводите ключи в публичный репозиторий.

## Сборка

GitHub Actions собирает debug APK. Откройте вкладку Actions, дождитесь завершения workflow, затем скачайте artifact `VideoTranslate-debug-apk`.
