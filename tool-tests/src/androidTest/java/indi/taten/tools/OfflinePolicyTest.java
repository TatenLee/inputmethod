package indi.taten.tools;

import android.content.pm.PackageManager;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.FileDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 使用 APK 自己的 UID 检查；不借用 shell 身份，不向任何主机发包。 */
@RunWith(AndroidJUnit4.class)
public final class OfflinePolicyTest {
    @Test public void onlyDeclaredLocalFeaturesAndNoSharedUid() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        var info=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_PERMISSIONS);
        if(context.getPackageName().equals("indi.taten.smscode"))
            assertArrayEquals(new String[]{"android.permission.RECEIVE_SMS"},info.requestedPermissions);
        else assertTrue(info.requestedPermissions==null || info.requestedPermissions.length==0);
        assertNull(info.sharedUserId);
        assertEquals(context.getApplicationInfo().uid,Process.myUid());
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission("android.permission.INTERNET"));
        assertFalse(android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted());
    }
    @Test public void kernelRejectsIpv4Ipv6TcpUdpSockets() throws Exception {
        for(int domain:new int[]{OsConstants.AF_INET,OsConstants.AF_INET6})for(int type:new int[]{OsConstants.SOCK_STREAM,OsConstants.SOCK_DGRAM}) {
            FileDescriptor fd=null;
            try {fd=Os.socket(domain,type,0);fail("不应允许创建网络套接字");}
            catch(ErrnoException denied){assertTrue(denied.toString(),denied.errno==OsConstants.EACCES || denied.errno==OsConstants.EPERM);}
            finally {if(fd!=null)Os.close(fd);}
        }
    }
}
