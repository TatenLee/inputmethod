package indi.tatenlee.inputmethod.speech;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 按一下录音，再按一下结束；录音仅在内存中，识别全程在设备上。 */
public final class OfflineSpeech implements AutoCloseable {
    public interface Listener {
        void onStatus(String message, boolean busy);
        void onResult(long editorToken, String text);
    }
    private static final int RATE = 16000;
    private static final int MAX_SECONDS = 30;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final SenseVoiceDecoder decoder;
    private volatile Recording current;
    private volatile boolean closed;
    private final Runnable releaseIdle;

    private static final class Recording {
        final long editorToken;
        volatile boolean stop;
        volatile boolean cancelled;
        Recording(long token) { editorToken = token; }
    }

    public OfflineSpeech(Context context, Listener listener) {
        decoder = new SenseVoiceDecoder(context);
        this.listener = listener;
        releaseIdle = () -> worker.execute(() -> { if (current == null) decoder.close(); });
    }

    public boolean busy() { return current != null; }

    /** 调用方先检查 RECORD_AUDIO 运行时权限。 */
    public void start(long token) {
        if (closed || current != null) return;
        main.removeCallbacks(releaseIdle);
        Recording recording = new Recording(token);
        current = recording;
        listener.onStatus("正在准备离线语音…", true);
        worker.execute(() -> capture(recording));
    }

    public void finish() { Recording r = current; if (r != null) r.stop = true; }

    public void cancel() {
        Recording r = current;
        if (r != null) { r.cancelled = true; r.stop = true; }
        current = null;
        if (!closed) { main.removeCallbacks(releaseIdle); main.postDelayed(releaseIdle, 30000); }
    }

    private boolean valid(Recording r) { return !closed && !r.cancelled && current == r; }
    private void status(Recording r, String text) {
        main.post(() -> { if (valid(r)) listener.onStatus(text, true); });
    }

    @SuppressWarnings("MissingPermission")
    private void capture(Recording r) {
        AudioRecord recorder = null;
        String result = "";
        String error = null;
        try {
            decoder.initialize();
            if (!valid(r) || r.stop) return;
            int minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IllegalStateException("Unsupported recorder");
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, RATE));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Recorder unavailable");
            float[] samples = new float[RATE * MAX_SECONDS];
            short[] buffer = new short[1600];
            int length = 0;
            int voicedSamples = 0;
            recorder.startRecording();
            status(r, "正在聆听 · 再点语音结束");
            while (valid(r) && !r.stop && length < samples.length) {
                int count = recorder.read(buffer, 0, Math.min(buffer.length, samples.length - length));
                if (count < 0) throw new IllegalStateException("AudioRecord error " + count);
                for (int i = 0; i < count; i++) {
                    float value = buffer[i] / 32768f;
                    samples[length++] = value;
                    if (Math.abs(value) > .012f) voicedSamples++;
                }
            }
            recorder.stop();
            recorder.release();
            recorder = null;
            if (!valid(r)) return;
            if (length > RATE / 4 && voicedSamples > RATE / 50) {
                status(r, "正在识别，请稍候…");
                result = decoder.decode(Arrays.copyOf(samples, length));
            }
            Arrays.fill(samples, 0);
        } catch (Exception | LinkageError failure) {
            android.util.Log.e("JianxinSpeech", "Offline recognition failed", failure);
            error = "语音未能启动，请检查麦克风权限和离线模型";
        } finally {
            if (recorder != null) {
                try { recorder.stop(); } catch (IllegalStateException ignored) { }
                recorder.release();
            }
            String finalResult = result;
            String finalError = error;
            main.post(() -> {
                if (!valid(r)) return;
                current = null;
                main.postDelayed(releaseIdle, 30000);
                listener.onStatus(finalError != null ? finalError : finalResult.isEmpty() ? "没有听清，请重试" : "", false);
                if (finalError == null && !finalResult.isEmpty()) listener.onResult(r.editorToken, finalResult);
            });
        }
    }

    @Override public void close() {
        closed = true;
        main.removeCallbacks(releaseIdle);
        cancel();
        worker.execute(decoder::close);
        worker.shutdown();
    }
}
