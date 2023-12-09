package eu.europa.ec.fhir.state;

/**
 * Record for the state of an expected manual check by an administrator.
 *
 * @param testSessionId The relevant test session ID.
 * @param callId The relevant 'receive' step's ID.
 * @param callbackAddress The Test Bed's callback address.
 */
public record ExpectedManualCheck(String testSessionId, String callId, String callbackAddress) {
}
