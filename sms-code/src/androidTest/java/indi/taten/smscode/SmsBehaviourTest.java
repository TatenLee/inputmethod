package indi.taten.smscode;

import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class SmsBehaviourTest {
    @Test public void foregroundCopyIsVerifiedAndSensitive() {
        var inst=InstrumentationRegistry.getInstrumentation();var context=inst.getTargetContext();
        var activity=inst.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            for(int i=0;i<20 && !activity.hasWindowFocus();i++)SystemClock.sleep(50);
            assertTrue(activity.hasWindowFocus());
            inst.runOnMainSync(()-> {
                assertTrue(SmsRuntime.copy(activity,"246802","本地测试"));
                var clipboard=activity.getSystemService(ClipboardManager.class);
                var clip=clipboard.getPrimaryClip();assertNotNull(clip);
                assertEquals("246802",clip.getItemAt(0).getText().toString());
                assertTrue(clip.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE"));
                clipboard.clearPrimaryClip();
            });
        } finally {inst.runOnMainSync(activity::finish);}
    }
    @Test public void olderVisibleCodeCannotReplaceNewCopyButNewSmsCan() {
        var inst=InstrumentationRegistry.getInstrumentation();var context=inst.getTargetContext();
        var activity=inst.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            for(int i=0;i<20 && !activity.hasWindowFocus();i++)SystemClock.sleep(50);
            assertTrue(activity.hasWindowFocus());
            inst.runOnMainSync(()-> {
                assertTrue(SmsRuntime.copy(activity,"319642","删除前确认"));
                assertFalse(SmsRuntime.copyVisibleCode(activity,"477435"));
                var clipboard=activity.getSystemService(ClipboardManager.class);
                assertEquals("319642",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
                assertTrue(SmsRuntime.copy(activity,"537281","新短信"));
                assertEquals("537281",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
                clipboard.clearPrimaryClip();
            });
        } finally {inst.runOnMainSync(activity::finish);}
    }
    @Test public void changingSmsAppCancelsAutomaticDeletion() {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        var prefs=new SmsPreferences(context);
        prefs.deletionMode(SmsPreferences.AUTO_DELETE);
        prefs.smsPackage("com.android.messaging");
        assertEquals(SmsPreferences.KEEP,prefs.deletionMode());
    }
    @Test public void externalAppsCannotLaunchCopyOrBindService() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        var pm=context.getPackageManager();
        assertFalse(pm.getActivityInfo(new ComponentName(context,CopyActivity.class),0).exported);
        var service=pm.getServiceInfo(new ComponentName(context,"indi.taten.smscode.service.CodeAccessibilityService"),0);
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",service.permission);
    }
}
