/*
 * Copyright (c) 2017-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.app;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.salesforce.androidsdk.KeyImpl;
import com.salesforce.androidsdk.MainActivity;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager;
import com.salesforce.androidsdk.analytics.manager.AnalyticsManager;
import com.salesforce.androidsdk.analytics.model.DeviceAppAttributes;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManagerTest;
import com.salesforce.androidsdk.ui.LoginActivity;

/**
 * A class that contains tests for functionality exposed in SalesforceSDKManager.
 *
 * @author bhariharan
 */
public class SalesforceSDKManagerTest extends InstrumentationTestCase {

    private static final String TAG = "SFSDKManagerTest";
    private static final String TEST_APP_NAME = "OverridenAppName";

    /**
     * Test for setting the analytics app name before 'SalesforceSDKManager.init()'
     * has been called.
     */
    public void testOverrideAiltnAppNameBeforeSDKManagerInit() {
        SalesforceSDKTestManager.setAiltnAppName(TEST_APP_NAME);
        SalesforceSDKTestManager.init(getInstrumentation().getTargetContext(),
                new KeyImpl(), MainActivity.class);
        SalesforceSDKTestManager.getInstance().setIsTestRun(true);
        compareAiltnAppNames(TEST_APP_NAME);
    }

    /**
     * Test for setting the analytics app name after 'SalesforceSDKManager.init()'
     * has been called.
     */
    public void testOverrideAiltnAppNameAfterSDKManagerInit() {
        SalesforceSDKTestManager.init(getInstrumentation().getTargetContext(),
                new KeyImpl(), MainActivity.class);
        SalesforceSDKTestManager.setAiltnAppName(TEST_APP_NAME);
        SalesforceSDKTestManager.getInstance().setIsTestRun(true);
        compareAiltnAppNames(TEST_APP_NAME);
    }

    /**
     * Test for default analytics app name.
     */
    public void testDefaultAiltnAppName() {
        SalesforceSDKTestManager.init(getInstrumentation().getTargetContext(),
                new KeyImpl(), MainActivity.class);
        SalesforceSDKTestManager.getInstance().setIsTestRun(true);
        compareAiltnAppNames(getDefaultAppName());
    }

    /**
     * Test for setting an invalid analytics app name.
     */
    public void testOverrideInvalidAiltnAppName() {
        SalesforceSDKTestManager.init(getInstrumentation().getTargetContext(),
                new KeyImpl(), MainActivity.class);
        SalesforceSDKTestManager.setAiltnAppName(null);
        SalesforceSDKTestManager.getInstance().setIsTestRun(true);
        compareAiltnAppNames(getDefaultAppName());
    }

    private void compareAiltnAppNames(String expectedAppName) {
        final UserAccountManager userAccMgr = SalesforceSDKTestManager.getInstance().getUserAccountManager();
        final Context targetContext = getInstrumentation().getTargetContext();
        final ClientManager clientManager = new ClientManager(targetContext,
                ClientManagerTest.TEST_ACCOUNT_TYPE, null, true);
        clientManager.createNewAccount(ClientManagerTest.TEST_ACCOUNT_NAME, ClientManagerTest.TEST_USERNAME,
                ClientManagerTest.TEST_REFRESH_TOKEN, ClientManagerTest.TEST_AUTH_TOKEN,
                ClientManagerTest.TEST_INSTANCE_URL, ClientManagerTest.TEST_LOGIN_URL,
                ClientManagerTest.TEST_IDENTITY_URL, ClientManagerTest.TEST_CLIENT_ID,
                ClientManagerTest.TEST_ORG_ID, ClientManagerTest.TEST_USER_ID, null);
        final AccountManager accMgr = AccountManager.get(targetContext);
        final UserAccount curUser = userAccMgr.getCurrentUser();
        assertNotNull("Current user should NOT be null", curUser);
        SalesforceAnalyticsManager.reset(curUser);
        final SalesforceAnalyticsManager analyticsManager = SalesforceAnalyticsManager.getInstance(userAccMgr.getCurrentUser());
        assertNotNull("SalesforceAnalyticsManager instance should NOT be null", analyticsManager);
        final AnalyticsManager manager = analyticsManager.getAnalyticsManager();
        assertNotNull("AnalyticsManager instance should NOT be null", manager);
        final DeviceAppAttributes deviceAppAttributes = analyticsManager.getAnalyticsManager().getDeviceAppAttributes();
        assertNotNull("Device attributes should NOT be null", deviceAppAttributes);
        final String ailtnAppName = deviceAppAttributes.getAppName();
        assertEquals("DeviceAppAttributes - App names do NOT match", expectedAppName, ailtnAppName);
        assertEquals("SalesforceSDKManager - App names do NOT match", expectedAppName, SalesforceSDKTestManager.getAiltnAppName());
        SalesforceAnalyticsManager.reset(curUser);
        clientManager.removeAccounts(accMgr.getAccountsByType(ClientManagerTest.TEST_ACCOUNT_TYPE));
        SalesforceSDKTestManager.resetAiltnAppName();
        SalesforceSDKTestManager.resetInstance();
    }

    private String getDefaultAppName() {
        String ailtnAppName = null;
        try {
            final Context targetContext = getInstrumentation().getTargetContext();
            final PackageInfo packageInfo = targetContext.getPackageManager().getPackageInfo(targetContext.getPackageName(), 0);
            ailtnAppName = targetContext.getString(packageInfo.applicationInfo.labelRes);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Package not found", e);
        }
        return ailtnAppName;
    }

    /**
     * Mock version of SalesforceSDKManager.
     */
    private static class SalesforceSDKTestManager extends SalesforceSDKManager {

        /**
         * Protected constructor.
         *
         * @param context Application context.
         * @param keyImpl Implementation of KeyInterface.
         * @param mainActivity Activity to be launched after the login flow.
         * @param loginActivity Login activity.
         */
        protected SalesforceSDKTestManager(Context context, KeyInterface keyImpl,
                                       Class<? extends Activity> mainActivity, Class<? extends Activity> loginActivity) {
            super(context, keyImpl, mainActivity, loginActivity);
        }

        /**
         * Initializes this component.
         *
         * @param context Application context.
         * @param keyImpl Implementation of KeyInterface.
         * @param mainActivity Activity to be launched after the login flow.
         */
        public static void init(Context context, KeyInterface keyImpl,
                                 Class<? extends Activity> mainActivity) {
            if (INSTANCE == null) {
                INSTANCE = new SalesforceSDKTestManager(context, keyImpl, mainActivity, LoginActivity.class);
            }
            initInternal(context);
        }

        /**
         * Resets the app name to be used by the analytics framework.
         * This is meant to be used ONLY by tests.
         */
        public static void resetAiltnAppName() {
            AILTN_APP_NAME = null;
        }

        /**
         * Resets the current instance being used.
         * This is meant to be used ONLY by tests.
         */
        public static void resetInstance() {
            INSTANCE = null;
        }
    }
}
