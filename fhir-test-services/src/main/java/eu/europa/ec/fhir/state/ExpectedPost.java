package eu.europa.ec.fhir.state;

/**
 * Record for the state of an expected post by a remote FHIR server.
 *
 * @param testSessionId The relevant test session ID.
 * @param callId The relevant 'receive' step's ID.
 * @param callbackAddress The Test Bed's callback address.
 * @param patient The patient for which we expect to receive a message.
 * @param endpoint The internal FHIR server endpoint to which we will forward the POST.
 */
public record ExpectedPost(String testSessionId, String callId, String callbackAddress, String patient, String endpoint) {
}
