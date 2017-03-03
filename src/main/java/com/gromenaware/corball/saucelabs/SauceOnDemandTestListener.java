package com.gromenaware.corball.saucelabs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * Test Listener that providers helper logic for TestNG tests.  Upon startup, the class
 * will store any SELENIUM_* environment variables (typically set by a Sauce OnDemand CI
 * plugin) as system parameters, so that they can be retrieved by tests as parameters.
 * <p>
 * TODO how to specify whether to download log/video?
 *
 * @author Ross Rowe
 */
public class SauceOnDemandTestListener extends TestListenerAdapter {

    private static final String SELENIUM_BROWSER = "SELENIUM_BROWSER";
    private static final String SELENIUM_PLATFORM = "SELENIUM_PLATFORM";
    private static final String SELENIUM_VERSION = "SELENIUM_VERSION";
    private static final String SELENIUM_IS_LOCAL = "SELENIUM_IS_LOCAL";
    private static final Logger logger = LogManager.getLogger(SauceOnDemandTestListener.class);
    public InheritableThreadLocal<String> sessionIdContainer = new InheritableThreadLocal<String>();
    /**
     * The underlying {@link SauceOnDemandSessionIdProvider} instance which contains the Selenium session id.  This is typically
     * the unit test being executed.
     */
    private SauceOnDemandSessionIdProvider sessionIdProvider;

    /**
     * The instance of the Sauce OnDemand Java REST API client.
     */
    private SauceREST sauceREST;

    /**
     * Treat this test as a local test or run in SauceLabs.
     */
    private boolean isLocal = false;

    /**
     * Boolean indicating whether to print the log messages to the stdout.
     */
    public static final boolean verboseMode = true;

    /**
     * Check to see if environment variables that define the Selenium browser to be used have been set (typically by
     * a Sauce OnDemand CI plugin).  If so, then populate the appropriate system parameter, so that tests can use
     * these values.
     *
     * @param testContext
     */
    @Override
    public void onStart(ITestContext testContext) {
        super.onStart(testContext);
        String local = SauceUtils.readPropertyOrEnv(SELENIUM_IS_LOCAL, "");
        if (local != null && !local.equals("")) {
            isLocal = true;
        }
        String browser = System.getenv(SELENIUM_BROWSER);
        if (browser != null && !browser.equals("")) {
            System.setProperty("browser", browser);
        }
        String platform = System.getenv(SELENIUM_PLATFORM);
        if (platform != null && !platform.equals("")) {
            System.setProperty("os", platform);
        }
        String version = System.getenv(SELENIUM_VERSION);
        if (version != null && !version.equals("")) {
            System.setProperty("version", version);
        }
    }

    /**
     * @param result
     */
    @Override
    public void onTestStart(ITestResult result) {
        super.onTestStart(result);

        if (isLocal) {
            return;
        }

        if (verboseMode && result.getInstance() instanceof SauceOnDemandSessionIdProvider) {
            this.sessionIdProvider = (SauceOnDemandSessionIdProvider) result.getInstance();
            //log the session id to the system out
            if (sessionIdProvider.getSessionId() != null) {
                logger.info(String.format("SauceOnDemandSessionID=%1$s job-name=%2$s",
                        sessionIdProvider.getSessionId(), result.getMethod().getMethodName()));
                sessionIdContainer.set(sessionIdProvider.getSessionId());
            }
        }
        SauceOnDemandAuthentication sauceOnDemandAuthentication;
        if (result.getInstance() instanceof SauceOnDemandAuthenticationProvider) {
            //use the authentication information provided by the test class
            SauceOnDemandAuthenticationProvider provider =
                    (SauceOnDemandAuthenticationProvider) result.getInstance();
            sauceOnDemandAuthentication = provider.getAuthentication();
        } else {
            //otherwise use the default authentication
            sauceOnDemandAuthentication = new SauceOnDemandAuthentication();
        }
        this.sauceREST = new SauceREST(sauceOnDemandAuthentication.getUsername(),
                sauceOnDemandAuthentication.getAccessKey());
    }

    /**
     * @param tr
     */
    @Override
    public void onTestFailure(ITestResult tr) {
        super.onTestFailure(tr);
        if (isLocal) {
            return;
        }
        markJobAsFailed();
        printPublicJobLink();
    }

    private void markJobAsFailed() {
        if (this.sauceREST != null && sessionIdProvider != null) {
            //String sessionId = sessionIdProvider.getSessionId();
            String sessionId = sessionIdContainer.get();
            if (sessionId != null) {
                Map<String, Object> updates = new HashMap<String, Object>();
                updates.put("passed", false);
                SauceUtils.addBuildNumberToUpdate(updates);
                sauceREST.updateJobInfo(sessionId, updates);
            }
        }
    }

    private void printPublicJobLink() {
        if (verboseMode && this.sauceREST != null && sessionIdProvider != null) {
            String sessionId = sessionIdContainer.get();
            logger.info("sessionId: " + sessionId);
            String authLink = this.sauceREST.getPublicJobLink(sessionId);
            // String authLink = "test";
            logger.info("Job link: " + authLink);
        }
    }

    /**
     * @param tr
     */
    @Override
    public void onTestSuccess(ITestResult tr) {
        super.onTestSuccess(tr);
        if (isLocal) {
            return;
        }
        printPublicJobLink();
        markJobAsPassed();
    }

    private void markJobAsPassed() {
        if (this.sauceREST != null && sessionIdProvider != null) {
            //String sessionId = sessionIdProvider.getSessionId();
            String sessionId = sessionIdContainer.get();
            if (sessionId != null) {
                Map<String, Object> updates = new HashMap<String, Object>();
                updates.put("passed", true);
                SauceUtils.addBuildNumberToUpdate(updates);
                sauceREST.updateJobInfo(sessionId, updates);
            }
        }
    }
}