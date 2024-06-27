package eu.europa.ec.fhir.gitb;

import com.gitb.ps.Void;
import com.gitb.ps.*;
import com.gitb.tr.TestResultType;
import eu.europa.ec.fhir.state.StateManager;
import eu.europa.ec.fhir.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implementation of the GITB messaging API to handle messaging calls.
 */
@Component
public class ProcessingServiceImpl implements ProcessingService {

    @Autowired
    private StateManager stateManager;
    @Autowired
    private Utils utils;

    @Override
    public GetModuleDefinitionResponse getModuleDefinition(Void aVoid) {
        // Empty implementation.
        return new GetModuleDefinitionResponse();
    }

    /**
     * Called when a "process" step is executed.
     * <p/>
     * This is used currently to pass configuration values to the service when the test session starts.
     *
     * @param processRequest The request.
     * @return The step report.
     */
    @Override
    public ProcessResponse process(ProcessRequest processRequest) {
        String operation = processRequest.getOperation();
        if ("init".equals(operation)) {
            stateManager.recordConfiguration(utils.getRequiredString(processRequest.getInput(), "endpoint"));
        }
        var response = new ProcessResponse();
        response.setReport(utils.createReport(TestResultType.SUCCESS));
        return response;
    }

    @Override
    public BeginTransactionResponse beginTransaction(BeginTransactionRequest beginTransactionRequest) {
        // Empty implementation.
        return new BeginTransactionResponse();
    }

    @Override
    public Void endTransaction(BasicRequest basicRequest) {
        // Empty implementation.
        return new Void();
    }
}
