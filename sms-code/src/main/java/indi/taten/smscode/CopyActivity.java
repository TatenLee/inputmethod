package indi.taten.smscode;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;

/** 删除前取得正常前台焦点并读回本次写入；不绕过系统后台剪贴板限制。 */
public final class CopyActivity extends Activity {
    private SmsRuntime.CopyRequest request;
    private boolean finished;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);setFinishOnTouchOutside(false);
        request=SmsRuntime.pendingCopy;
        if(request==null || SystemClock.elapsedRealtime()>request.expires) {finish();return;}
        TextView text=new TextView(this);text.setText("正在确认验证码已复制…\n完成后返回短信页面清理此条短信。");
        text.setTextSize(18);int padding=Math.round(24*getResources().getDisplayMetrics().density);text.setPadding(padding,padding,padding,padding);setContentView(text);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(focused && !finished && request!=null) {
            finished=true;
            new Handler(Looper.getMainLooper()).postDelayed(()-> {
                boolean copied=hasWindowFocus() && !isFinishing() && request==SmsRuntime.pendingCopy
                        && SystemClock.elapsedRealtime()<=request.expires && SmsRuntime.copy(this,request.code,"复制确认窗");
                if(request==SmsRuntime.pendingCopy)SmsRuntime.pendingCopy=null;
                finish();request.completion.accept(copied);
            },120);
        }
    }
    @Override protected void onDestroy() {
        if(!finished && request!=null && request==SmsRuntime.pendingCopy) {SmsRuntime.pendingCopy=null;request.completion.accept(false);}
        super.onDestroy();
    }
}
