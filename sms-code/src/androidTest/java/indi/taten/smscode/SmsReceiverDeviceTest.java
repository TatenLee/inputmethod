package indi.taten.smscode;

import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/** 宿主先通过模拟器注入合成短信，再以应用自己的前台身份核验后台复制结果。 */
@RunWith(AndroidJUnit4.class)
public final class SmsReceiverDeviceTest {
    @Test public void previouslyReceivedSmsIsInClipboard() {
        String expected=InstrumentationRegistry.getArguments().getString("expected_sms_code");
        assumeNotNull(expected);
        var inst=InstrumentationRegistry.getInstrumentation();var context=inst.getTargetContext();
        assertEquals(PackageManager.PERMISSION_GRANTED,context.checkSelfPermission("android.permission.RECEIVE_SMS"));
        var activity=inst.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            for(int i=0;i<20 && !activity.hasWindowFocus();i++)SystemClock.sleep(50);
            assertTrue(activity.hasWindowFocus());
            inst.runOnMainSync(()-> {
                var clip=activity.getSystemService(ClipboardManager.class).getPrimaryClip();
                assertNotNull("后台接收后应有剪贴板内容",clip);
                assertEquals(expected,clip.getItemAt(0).getText().toString());
                assertTrue(clip.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE"));
            });
        } finally {inst.runOnMainSync(activity::finish);}
    }
}
