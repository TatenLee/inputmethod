package indi.tatenlee.inputmethod.settings;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class LicensesActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this); text.setTextSize(13); text.setTextIsSelectable(true);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density); text.setPadding(padding, padding, padding, padding);
        StringBuilder contents = new StringBuilder("简心输入法 · 开源组件\n\n");
        try {
            String[] files = getAssets().list("licenses");
            if (files != null) for (String file : files) {
                contents.append("\n\n—— ").append(file).append(" ——\n\n");
                try (var input = getAssets().open("licenses/" + file); var output = new ByteArrayOutputStream()) {
                    byte[] bytes = new byte[8192]; int n;
                    while ((n = input.read(bytes)) != -1) output.write(bytes, 0, n);
                    contents.append(output.toString(StandardCharsets.UTF_8.name()));
                }
            }
        } catch (Exception failure) { contents.append("许可文件无法读取，请查看随源码提供的 LICENSE 与 THIRD_PARTY_NOTICES.md。"); }
        text.setText(contents); ScrollView scroll = new ScrollView(this); scroll.addView(text);
        WindowPadding.setContent(this, scroll);
    }
}
