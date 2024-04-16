package eu.europa.ec.fhir.state;

import com.gitb.core.LogLevel;
import com.gitb.tr.TAR;
import com.gitb.tr.TestResultType;
import eu.europa.ec.fhir.gitb.TestBedNotifier;
import eu.europa.ec.fhir.handlers.RequestResult;
import eu.europa.ec.fhir.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Component used to manage the ongoing state of test sessions.
 */
@Component
public class StateManager {

    private static final Logger LOG = LoggerFactory.getLogger(StateManager.class);
    private static final String MANUAL_CHECK = "manualCheck";
    private static final String POST_TO_VALIDATE = "postToValidate";

    @Autowired
    private TestBedNotifier testBedNotifier;
    @Autowired
    private Utils utils;

    /**
     * Map that represents the ongoing test sessions' state. This is not defined as synchronised given that we use
     * synchronised guards in all public methods.
     */
    private final Map<String, Map<String, Object>> testSessions = new HashMap<>();
    private final Object lock = new Object();

    /**
     * Record a new test session identifier.
     *
     * @param testSessionIdentifier The test session identifier.
     */
    public void recordSession(String testSessionIdentifier) {
        synchronized (lock) {
            testSessions.put(testSessionIdentifier, new HashMap<>());
        }
    }

    /**
     * Destroy all state relevant to a given test session.
     *
     * @param testSessionIdentifier The test session identifier.
     */
    public void destroySession(String testSessionIdentifier) {
        synchronized (lock) {
            testSessions.remove(testSessionIdentifier);
        }
    }

    /**
     * Record an expected POST by a FHIR client.
     * <p/>
     * We record these as an ArrayList in case we may have a test session expecting multiple POSTs in parallel.
     *
     * @param expected The expected POST's information.
     */
    public void recordExpectedPost(ExpectedPost expected) {
        synchronized (lock) {
            var sessionState = testSessions.get(expected.testSessionId());
            if (sessionState != null) {
                var expectedPosts = (ArrayList<ExpectedPost>) sessionState.computeIfAbsent(POST_TO_VALIDATE, (key) -> new ArrayList<ExpectedPost>());
                expectedPosts.add(expected);
                // Add also a message to the test session's log.
                addToSessionLog(expected.testSessionId(), expected.callbackAddress(), LogLevel.INFO, "Expecting to receive post for patient [%s].".formatted(expected.patient()));
            }
        }
    }

    /**
     * Check to see whether the currently active test sessions are expecting any POSTs.
     *
     * @return The check result.
     */
    public boolean isExpectingPosts() {
        synchronized (lock) {
            return testSessions.values().stream().anyMatch(
                    sessionData -> sessionData.containsKey(POST_TO_VALIDATE) && !((Collection<ExpectedPost>)sessionData.get(POST_TO_VALIDATE)).isEmpty()
            );
        }
    }

    /**
     * Retrieve (and consume) an expected POST matching the provided patient reference.
     * <p/>
     * Note that we need some reference information to determine whether an incoming POST is relevant for a given test session.
     * For this purpose in the current implementation we use the patient reference, with the assumption (even if not
     * implemented in test cases and test data currently), that each test session would expect the creation of a new
     * patient and then use her reference to distinguish between concurrent test sessions.
     *
     * @param patient The patient reference to look for.
     * @return The expected POST for the patient (if any).
     */
    public Optional<ExpectedPost> retrieveExpectedPost(String patient) {
        Optional<ExpectedPost> result = Optional.empty();
        synchronized (lock) {
            for (var sessionEntry: testSessions.entrySet()) {
                Collection<ExpectedPost> expectedPosts = (Collection<ExpectedPost>) sessionEntry.getValue().get(POST_TO_VALIDATE);
                if (expectedPosts != null) {
                    int index = 0;
                    for (var expectedPost: expectedPosts) {
                        if (expectedPost.patient().equals(patient)) {
                            result = Optional.of(expectedPost);
                            break;
                        }
                        index += 1;
                    }
                    if (result.isPresent()) {
                        expectedPosts.remove(index);
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Complete an expected POST for a patient by creating the relevant report and reporting back to the Test Bed.
     *
     * @param expectedPost The expected POST's information.
     * @param payload The received payload.
     * @param result The result of the call once forwarded to our internal FHIR server.
     */
    public void completeExpectedPost(ExpectedPost expectedPost, String payload, RequestResult result) {
        TAR report = utils.createReport(TestResultType.SUCCESS);
        utils.addCommonReportData(report, null, payload, result);
        testBedNotifier.notifyTestBed(expectedPost.testSessionId(), expectedPost.callId(), expectedPost.callbackAddress(), report);
    }

    /**
     * Add an entry to the test session log.
     *
     * @param sessionId The test session to log this for.
     * @param callbackAddress The callback address on which log entries are to be signalled (this matches the messaging
     *                        callback address).
     * @param level The log level.
     * @param message The message.
     */
    private void addToSessionLog(String sessionId, String callbackAddress, LogLevel level, String message) {
        // Log first in our own log.
        switch (level) {
            case INFO -> LOG.info(message);
            case WARNING -> LOG.warn(message);
            case ERROR -> LOG.error(message);
            default -> LOG.debug(message);
        }
        // Log also in the test session log.
        testBedNotifier.sendLogMessage(sessionId, callbackAddress, message, level);
    }

}
