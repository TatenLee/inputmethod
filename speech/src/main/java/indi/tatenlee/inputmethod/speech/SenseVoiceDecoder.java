package indi.tatenlee.inputmethod.speech;

import android.content.Context;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;

/** 只负责离线解码。必须在后台串行调用，与麦克风、输入框及界面无关。 */
public final class SenseVoiceDecoder implements AutoCloseable {
    private final Context context;
    private OfflineRecognizer recognizer;
    public SenseVoiceDecoder(Context context) { this.context = context.getApplicationContext(); }

    public void initialize() {
        if (recognizer != null) return;
        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.getModelConfig().getSenseVoice().setModel("speech/model.int8.onnx");
        config.getModelConfig().getSenseVoice().setLanguage("auto");
        config.getModelConfig().getSenseVoice().setUseInverseTextNormalization(true);
        config.getModelConfig().setTokens("speech/tokens.txt");
        config.getModelConfig().setNumThreads(2);
        config.getModelConfig().setProvider("cpu");
        config.getModelConfig().setDebug(false);
        recognizer = new OfflineRecognizer(context.getAssets(), config);
    }

    public String decode(float[] samples) {
        initialize();
        OfflineStream stream = recognizer.createStream();
        try {
            stream.acceptWaveform(samples, 16000);
            recognizer.decode(stream);
            return recognizer.getResult(stream).getText().trim();
        } finally { stream.release(); }
    }

    @Override public void close() { if (recognizer != null) { recognizer.release(); recognizer = null; } }
}
