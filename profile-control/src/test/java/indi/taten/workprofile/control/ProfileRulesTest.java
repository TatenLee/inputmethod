package indi.taten.workprofile.control;
import org.junit.Test;
import static org.junit.Assert.*;
public final class ProfileRulesTest {
    @Test public void anyAssociatedManagedProfileIdWorks() {
        ProfileRules.validateProfile(0,10,14,14,true,true);
        ProfileRules.validateProfile(10,27,35,35,true,true);
    }
    @Test public void rejectsPersonalPrivateUnrelatedAndRecreatedProfiles() {
        for (int example=0;example<4;example++) {
            final int n=example;
            assertThrows(SecurityException.class,()->ProfileRules.validateProfile(0,n==0?0:12, n==3?43:42,42,n!=2,n!=1));
        }
    }
    @Test public void neverUsesNavigationModeOrUnavailableOverlay() {
        ProfileRules.validateOverlay(ProfileRules.OVERLAY,"android",ProfileRules.CATEGORY,true,2);
        assertThrows(IllegalStateException.class,()->ProfileRules.validateOverlay("com.android.internal.systemui.navbar.gestural","android",ProfileRules.CATEGORY,true,2));
        assertThrows(IllegalStateException.class,()->ProfileRules.validateOverlay(ProfileRules.OVERLAY,"android",ProfileRules.CATEGORY,false,2));
        assertThrows(IllegalStateException.class,()->ProfileRules.validateOverlay(ProfileRules.OVERLAY,"android",ProfileRules.CATEGORY,true,1));
    }
}
