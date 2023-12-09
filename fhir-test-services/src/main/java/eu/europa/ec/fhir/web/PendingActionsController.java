package eu.europa.ec.fhir.web;

import eu.europa.ec.fhir.state.StateManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Web controller to manage the simple screen presenting pending administrator requests.
 */
@Controller
public class PendingActionsController {

    @Autowired
    private StateManager stateManager;

    /**
     * Display the pending administrator verifications.
     *
     * @param model The UI model
     * @return The view name.
     */
    @RequestMapping(method = { RequestMethod.POST, RequestMethod.GET }, value = "/web/pending")
    public String pending(Model model) {
        model.addAttribute("pendingChecks", stateManager.getExpectedManualChecks());
        return "pending";
    }

    /**
     * Resolve a specific pending verification.
     *
     * @param session The relevant test session ID.
     * @param result The result.
     * @param comments The (optional) comments.
     * @return The view name.
     */
    @PostMapping(value = "/web/resolve")
    public String resolve(@RequestParam(name="session") String session, @RequestParam(name="result") String result,  @RequestParam(name="comments", defaultValue = "") String comments) {
        stateManager.completeExpectedManualCheck(session, result, comments);
        return "redirect:/web/pending";
    }

}
