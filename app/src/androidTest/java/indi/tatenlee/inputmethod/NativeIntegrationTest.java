package indi.tatenlee.inputmethod;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import indi.tatenlee.inputmethod.core.EngineState;
import indi.tatenlee.inputmethod.engine.RimeEngine;
import indi.tatenlee.inputmethod.speech.SenseVoiceDecoder;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** 在真实 Android ARM64 进程中验证打包后的 JNI、词库和语音模型。 */
@RunWith(AndroidJUnit4.class)
public final class NativeIntegrationTest {
    @Test public void rimePinyinAndNineKeyCommitChinese() throws Exception {
        LinkedBlockingQueue<Object> results = new LinkedBlockingQueue<>();
        RimeEngine engine = new RimeEngine(InstrumentationRegistry.getInstrumentation().getTargetContext(), new RimeEngine.Listener() {
            public void onState(long token, EngineState state) { results.add(state); }
            public void onError(long token, String message) { results.add(new AssertionError(message)); }
            public void onUnhandled(long token, int key) { results.add(new AssertionError("Unhandled " + key)); }
        });
        try {
            for (boolean nine : new boolean[]{false, true}) {
                engine.configure(1, nine, false); take(results);
                EngineState state = null;
                for (char c : (nine ? "64426" : "nihao").toCharArray()) {
                    engine.command(1, RimeEngine.KEY, c); state = take(results);
                }
                assertNotNull(state);
                int index = state.candidates.indexOf("你好");
                assertTrue("Expected 你好: " + state.candidates, index >= 0);
                engine.command(1, RimeEngine.SELECT, index);
                assertEquals("你好", take(results).commit);
                engine.text(1, "😀");
                assertEquals("😀", take(results).commit);
            }
        } finally { engine.close(); }
    }

    @Test public void bundledOfflineSpeechRecognizesPublicChineseFixture() throws Exception {
        byte[] wav;
        try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("sensevoice-zh.wav");
             var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            wav = output.toByteArray();
        }
        ByteBuffer bytes = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int offset = 12, start = -1, size = 0;
        while (offset + 8 <= wav.length) {
            int length = bytes.getInt(offset + 4);
            if (wav[offset] == 'd' && wav[offset + 1] == 'a' && wav[offset + 2] == 't' && wav[offset + 3] == 'a') {
                start = offset + 8; size = length; break;
            }
            offset += 8 + length + (length % 2);
        }
        assertTrue("WAV data chunk", start >= 0 && start + size <= wav.length);
        float[] samples = new float[size / 2];
        for (int i = 0; i < samples.length; i++) samples[i] = bytes.getShort(start + i * 2) / 32768f;
        try (SenseVoiceDecoder decoder = new SenseVoiceDecoder(InstrumentationRegistry.getInstrumentation().getTargetContext())) {
            String result = decoder.decode(samples);
            android.util.Log.i("JianxinTest", "Public speech fixture: " + result);
            assertTrue("Chinese speech result: " + result, result.contains("早上") && result.contains("下午"));
        }
    }

    private static EngineState take(LinkedBlockingQueue<Object> queue) throws Exception {
        Object result = queue.poll(120, TimeUnit.SECONDS);
        assertNotNull("Native engine timed out", result);
        if (result instanceof AssertionError) throw (AssertionError) result;
        return (EngineState) result;
    }
}
