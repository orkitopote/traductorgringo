# Subtítulos en vivo (inglés → español) para Android

Escucha audio en inglés con el micrófono, lo transcribe en tiempo real y muestra la traducción al español mientras se habla.

## Cómo funciona
- **Reconocimiento de voz:** el `SpeechRecognizer` de Android (motor de Google), en inglés (en-US), con resultados parciales para que el texto aparezca mientras se habla. La app lo reinicia sola al terminar cada frase para que escuche sin parar.
- **Traducción:** Google ML Kit Translation, que traduce **en el propio teléfono**. La primera vez descarga un modelo de unos 30 MB; después la traducción funciona sin internet.
- **Pantalla:** abajo se ve el subtítulo en vivo (inglés en gris y español grande). Arriba queda el historial de frases terminadas.

## Compilar e instalar

### Opción A: Android Studio (recomendada)
1. Instala [Android Studio](https://developer.android.com/studio).
2. *File → Open…* y elige la carpeta `SubtitulosEnVivo`. Espera a que termine el "Gradle sync".
3. Conecta tu teléfono con la depuración USB activada y pulsa ▶ **Run**.
   Para obtener el archivo: *Build → Build App Bundle(s)/APK(s) → Build APK(s)*.

### Opción B: sin instalar nada (GitHub)
1. Sube esta carpeta a un repositorio de GitHub.
2. En la pestaña **Actions** se ejecuta "Compilar APK" automáticamente.
3. Al terminar, descarga el artefacto `SubtitulosEnVivo-apk`, descomprímelo e instala el `.apk` en el teléfono. Tendrás que permitir "instalar apps de origen desconocido".

Requisitos: Android 8.0 o superior y la app de Google instalada (es la que da el servicio de voz).

## Uso
- **Escuchar / Detener:** empieza o para la escucha. La primera vez pedirá permiso de micrófono.
- **A− / A+:** cambia el tamaño de los subtítulos.
- **Borrar:** limpia el historial. Si lo **mantienes pulsado**, copia toda la transcripción al portapapeles.
- **Preferir reconocimiento sin internet:** usa el modelo de voz offline si tienes descargado el paquete de inglés (*Ajustes → Google → Voz/Reconocimiento sin conexión*). Es un poco menos preciso, pero no gasta datos.

## Limitaciones
- **Escucha por el micrófono.** Para subtitular un video que suena en el mismo teléfono, sube el volumen del altavoz: el micrófono lo capta. Captar el audio interno directamente (sin micrófono) requeriría otro motor de reconocimiento, como Vosk o Whisper, porque el de Android solo acepta el micrófono.
- En línea, el reconocedor de Google necesita internet. Si quieres que no gaste datos, activa el modo sin internet.
- Hay una pausa muy breve entre frases mientras el reconocedor se reinicia, así que puede perderse alguna palabra justo en ese momento.
- La app silencia los sonidos de notificación y del sistema mientras escucha para evitar el "bip" de cada reinicio, y los restaura al detenerse. En algunos teléfonos ese bip sale por otro canal y seguirá sonando.
- Android no permite usar el micrófono con la app en segundo plano, así que la escucha se detiene al salir de la app.

## Estructura
```
app/src/main/java/com/ejemplo/subtitulos/
├── MainActivity.kt      # Pantalla, botones y unión de voz + traducción
├── SpeechEngine.kt      # Reconocimiento continuo en inglés
├── TranslatorEngine.kt  # Traducción EN→ES con ML Kit
└── SubtitleAdapter.kt   # Lista del historial
```
