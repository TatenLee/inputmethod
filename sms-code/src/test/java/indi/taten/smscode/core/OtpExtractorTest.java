package indi.taten.smscode.core;
import org.junit.Test;
import static org.junit.Assert.*;
public final class OtpExtractorTest {
    @Test public void extractsChineseEnglishAndAlphanumericCodes() {
        assertEquals("123456",OtpExtractor.extract("【简心】验证码：123456，5分钟内有效，请勿泄露。"));
        assertEquals("1234",OtpExtractor.extract("校验码１２３４，请勿告知他人。"));
        assertEquals("A1B2C3",OtpExtractor.extract("Your verification code is A1B2C3."));
        assertEquals("654321",OtpExtractor.extract("654321 is your verification code."));
        assertEquals("572910",OtpExtractor.extract("Your code is 572910. Valid for 5 minutes."));
    }
    @Test public void refusesOrdersTelephoneNumbersAmbiguousAndOversizedTexts() {
        assertNull(OtpExtractor.extract("订单号12345678已发货，联系电话13800138000"));
        assertNull(OtpExtractor.extract("验证码已发送。客服电话：12345"));
        assertNull(OtpExtractor.extract("验证码 123456 或 654321"));
        assertNull(OtpExtractor.extract("123456"));
        assertNull(OtpExtractor.extract("验证码123456"+"x".repeat(5000)));
        assertEquals("123456",OtpExtractor.extract("验证码123456，客服电话4008001234，订单号99887766"));
    }
    @Test public void automaticDeletionRequiresExactRecentCopiedSingleMessage() {
        String body="验证码123456，请勿泄露";
        assertTrue(DeletePolicy.canAutoDelete(DeletePolicy.AOSP,body,body,2000,1000,true,1,true));
        assertFalse(DeletePolicy.canAutoDelete(DeletePolicy.AOSP,body,"验证码654321",2000,1000,true,1,true));
        assertFalse(DeletePolicy.canAutoDelete(DeletePolicy.AOSP,body,body,700000,1000,true,1,true));
        assertFalse(DeletePolicy.canAutoDelete(DeletePolicy.AOSP,body,body,2000,1000,true,2,true));
        assertFalse(DeletePolicy.canAutoDelete(DeletePolicy.AOSP,body,body,2000,1000,false,1,true));
        assertFalse(DeletePolicy.canAutoDelete(DeletePolicy.AOSP,body,body,2000,1000,true,1,false));
        assertFalse(DeletePolicy.canAutoDelete("other.sms",body,body,2000,1000,true,1,true));
    }
    @Test public void neverConfirmsConversationOrBulkDeletion() {
        assertTrue(DeletePolicy.isSingleMessageConfirmation("Delete message?"));
        assertTrue(DeletePolicy.isSingleMessageConfirmation("删除信息？"));
        assertFalse(DeletePolicy.isSingleMessageConfirmation("Delete conversation?"));
        assertFalse(DeletePolicy.isSingleMessageConfirmation("删除全部短信？"));
        assertFalse(DeletePolicy.isSingleMessageConfirmation("Delete 2 messages?"));
    }
}
