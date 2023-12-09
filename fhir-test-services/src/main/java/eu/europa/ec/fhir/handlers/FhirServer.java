package eu.europa.ec.fhir.handlers;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import eu.europa.ec.fhir.state.ExpectedPost;
import eu.europa.ec.fhir.state.StateManager;
import eu.europa.ec.fhir.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Component used to receive REST calls from FHIR clients.
 * <p/>
 * This is not a full implementation of a FHIR server but rather it is used to proxy an internal FHIR server instance
 * while exposing the necessary API to the client. In regard to this proxying, it will only occur if we have active
 * test sessions waiting for such calls.
 */
@RestController
public class FhirServer {

    private static final Logger LOG = LoggerFactory.getLogger(FhirServer.class);

    private final JsonPointer patientPointer = JsonPointer.compile("/patient/reference");
    @Autowired
    private StateManager stateManager;
    @Autowired
    private FhirClient fhirClient;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private Utils utils;

    /**
     * Receive an allergy intolerance post.
     *
     * @param pathExtension The path part after the base URI.
     * @param body The request's body.
     * @return The response.
     */
    @PostMapping(value="/server/api/{pathExtension}")
    public ResponseEntity<String> receivePost(@PathVariable String pathExtension, @RequestBody String body) {
        if (stateManager.isExpectingPosts()) {
            // We are expecting such POSTs.
            Optional<String> patient = extractPatient(body);
            if (patient.isPresent()) {
                // We have a patient reference in the POST.
                Optional<ExpectedPost> expectedPost = stateManager.retrieveExpectedPost(patient.get());
                if (expectedPost.isPresent()) {
                    // We have a test session that was expecting a POST for the patient in question.
                    LOG.info("Received expected POST for patient reference [%s]".formatted(expectedPost.get().patient()));
                    // We pretty-print here to ensure good presentation and to make possible validation report locations more meaningful.
                    body = utils.prettyPrintJson(body);
                    // Call our internal FHIR server with the exact same payload. We do this so that we can register the allergy and produce
                    // a fully valid response for the client.
                    RequestResult result = fhirClient.callServer(HttpMethod.POST, constructUri(expectedPost.get().endpoint(), pathExtension), body);
                    var builder = ResponseEntity.status(result.status());
                    var contentTypeHeader = result.contentType();
                    if (contentTypeHeader.isPresent()) {
                        builder = builder.header(HttpHeaders.CONTENT_TYPE, contentTypeHeader.get());
                    }
                    // Signal also to the Test Bed that the expected POST is completed so that the relevant test session continues.
                    stateManager.completeExpectedPost(expectedPost.get(), body, result);
                    return builder.body(result.body());
                } else {
                    LOG.warn("Received POST for patient reference [%s] that was not expected. Returning a 'bad request' response.".formatted(patient.get()));
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
                }
            } else {
                LOG.warn("Received POST without a patient reference. Returning a 'bad request' response.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        } else {
            LOG.warn("Received unexpected POST. Returning a 'bad request' response.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Construct the final URI to use for calling our internal FHIR server.
     *
     * @param base The base URI.
     * @param pathExtension The extension.
     * @return The full URI to use.
     */
    private String constructUri(String base, String pathExtension) {
        return StringUtils.appendIfMissing(base, "/") + StringUtils.removeStart(pathExtension, "/");
    }

    /**
     * Extract the patient reference from the provided payload.
     *
     * @param body The body to extract from.
     * @return The located patient reference.
     */
    private Optional<String> extractPatient(String body) {
        String patient = null;
        try {
            JsonNode payload = objectMapper.readTree(body);
            JsonNode patientNode = payload.at(patientPointer);
            if (patientNode instanceof TextNode) {
                patient = patientNode.asText();
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse body as JSON", e);
        }
        return Optional.ofNullable(patient);
    }

}
