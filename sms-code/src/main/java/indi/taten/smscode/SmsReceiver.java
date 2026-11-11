package indi.taten.smscode;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import indi.taten.smscode.core.OtpExtractor;

/** 只接收系统广播的新短信；不查询历史数据库，不发送，不中断默认短信 App 的接收。 */
public final class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())
                || context.checkSelfPermission(Manifest.permission.RECEIVE_SMS)!=PackageManager.PERMISSION_GRANTED
                || !new SmsPreferences(context).autoCopy() || context.getSystemService(KeyguardManager.class).isKeyguardLocked())return;
        try {
            SmsMessage[] messages=Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if(messages==null || messages.length==0)return;
            String sender=messages[0].getOriginatingAddress();
            if(sender==null)return;
            StringBuilder body=new StringBuilder();
            for(SmsMessage message:messages) {
                if(message==null || !sender.equals(message.getOriginatingAddress()) || message.getMessageBody()==null)return;
                body.append(message.getMessageBody());if(body.length()>4096)return;
            }
            String text=OtpExtractor.normalize(body.toString());String code=OtpExtractor.extract(text);
            if(code==null)return;
            SmsRuntime.receivedSms(sender,text);
            SmsRuntime.copy(context,code,"新短信");
        } catch(RuntimeException invalid) {
            // 不记录 PDU、号码、正文或验证码；格式异常时交给原短信 App 正常处理。
            SmsRuntime.setStatus(context,"本条新短信未能识别，可打开短信页面重试");
        }
    }
}
