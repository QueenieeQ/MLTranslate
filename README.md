<h3>General</h3>

MLTranslator uses [Meta's No Language Left Behind](https://ai.meta.com/research/no-language-left-behind/) for translations and [OpenAI's Whisper](https://openai.com/index/whisper/) for speech recognition. Both are cutting-edge, open-source AIs with top-tier quality, and they run directly on your phone. This means everything stays private, and you can even use MLTranslator offline without losing quality.

### Performance

I’ve worked hard to optimize the AI models to use less RAM and run faster. That said, to avoid any crashes, your phone should have at least **6GB of RAM**, and a fast CPU helps with smoother performance. The first time you open MLTranslator, it will automatically download the models (about 1.2GB), and then you're good to start translating.
I converted both models to ONNX format and quantized them to int8 (except for some weights to avoid noticeable quality loss). I also split some parts of the models to lower RAM usage since without this, some weights would duplicate during runtime, using more RAM than needed.

### Supported Languages

MLTranslator supports these languages:

Arabic, Bulgarian, Catalgit an, Chinese, Czech, Danish, German, Greek, English, Spanish, Finnish, French, Croatian, Italian, Japanese, Korean, Dutch, Polish, Portuguese, Romanian, Russian, Slovak, Swedish, Tamil, Thai, Turkish, Ukrainian, Urdu, Vietnamese.

### Text To Speech

For speaking, MLTranslator uses your phone’s system TTS (Text-to-Speech). The quality and supported languages will depend on your phone’s system TTS. All the languages listed above work with [Google TTS](https://play.google.com/store/apps/details?id=com.google.android.tts&pcampaignid=web_share), which is what we recommend, but feel free to use any TTS you prefer.

To switch your system TTS (and the one used by MLTranslator), just download the TTS engine you want from the Play Store or elsewhere. Then open MLTranslator, go to settings (top right), and in the "Output" section, click on "Text to Speech." This will take you to the system settings where you can select your preferred TTS engine. Once you’ve made your choice, restart MLTranslator to apply the changes.

### Libraries and Models

MLTranslator is fully open-source, though some external libraries it uses come with different licenses. Here are the external libraries:

- [OnnxRuntime](https://github.com/microsoft/onnxruntime) (open-source): Accelerates AI models.
- [SentencePiece](https://github.com/google/sentencepiece) (open-source): Tokenizes input text for NLLB.
- [ML Kit](https://developers.google.com/ml-kit/language/identification) (closed-source): Identifies languages in voice mode.
- [NLLB](https://github.com/facebookresearch/fairseq/tree/nllb): Uses the NLLB-Distilled-600M model with KV cache.
- [Whisper](https://github.com/openai/whisper): Uses the Whisper-Small-244M model with KV cache.



