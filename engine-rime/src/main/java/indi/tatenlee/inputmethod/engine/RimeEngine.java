package indi.tatenlee.inputmethod.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import indi.tatenlee.inputmethod.core.EngineState;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 词库部署、查询和学习在同一个后台线程串行执行，不阻塞按键绘制。 */
public final class RimeEngine implements AutoCloseable {
    public interface Listener {
        void onState(long token, EngineState state);
        void onError(long token, String message);
        void onUnhandled(long token, int key);
    }
    public static final int QUERY = 0, KEY = 1, SELECT = 2, CLEAR = 3, CONFIGURE = 4;
    // 服务重建时旧实例的 finalize 和新实例的 initialize 也必须排在同一条队列。
    private static final ExecutorService NATIVE_QUEUE = Executors.newSingleThreadExecutor();
    private final ExecutorService worker = NATIVE_QUEUE;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final Context context;
    private volatile boolean closed;
    private volatile boolean initialized;
    private boolean nineKey;
    private boolean privateMode;

    public RimeEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }
    public boolean ready() { return initialized; }

    public void configure(long token, boolean nine, boolean privacy) {
        submit(token, CONFIGURE, 0, nine, privacy);
        nineKey = nine;
        privateMode = privacy;
    }
    public void command(long token, int type, int value) { submit(token, type, value, nineKey, privateMode); }

    /** 先提交尚未完成的拼音，再提交符号/英文/表情，顺序与用户的点击顺序一致。 */
    public void text(long token, String text) { submit(token, 5, 0, nineKey, privateMode, text); }

    private void submit(long token, int type, int value, boolean nine, boolean privacy) {
        submit(token, type, value, nine, privacy, "");
    }
    private void submit(long token, int type, int value, boolean nine, boolean privacy, String suffix) {
        if (closed) return;
        worker.execute(() -> {
            if (closed) return;
            try {
                if (!initialized) {
                    var directories = RimeData.install(context);
                    RimeNative.initialize(directories[0], directories[1]);
                    initialized = true;
                }
                JSONObject json = new JSONObject(RimeNative.command(type, value, nine, privacy));
                JSONArray list = json.getJSONArray("candidates");
                ArrayList<String> candidates = new ArrayList<>();
                for (int i = 0; i < list.length(); i++) candidates.add(list.getString(i));
                EngineState state = new EngineState(json.getString("preedit"), json.getString("commit") + suffix,
                        candidates, json.getBoolean("lastPage"), json.getInt("page"));
                boolean handled = json.getBoolean("handled");
                main.post(() -> {
                    if (closed) return;
                    listener.onState(token, state);
                    if (type == KEY && !handled) listener.onUnhandled(token, value);
                });
            } catch (Exception | LinkageError failure) {
                main.post(() -> { if (!closed) listener.onError(token, "词库初始化失败，请在设置中查看构建说明"); });
                android.util.Log.e("JianxinEngine", "Engine operation failed", failure);
            }
        });
    }

    @Override public void close() {
        closed = true;
        worker.execute(() -> { if (initialized) RimeNative.shutdown(); });
    }
}
