package de.swiftbyte.utils;

import de.swiftbyte.Application;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.component.context.ComponentContext;
import org.springframework.shell.component.flow.ComponentFlow;

@Slf4j
public class NodeUtils {

    public static Integer getValidatedToken(String token) {

        log.debug("Validating token '" + token + "'...");

        String normalizedToken = token.replace("-", "");

        log.debug("Token was normalized to '" + normalizedToken + "'. Checking length...");

        if (normalizedToken.length() != 6) {
            log.debug("Token was not expected size.");
            return null;
        }
        ;

        log.debug("Token was expected size. Checking if token is a valid integer...");

        try {

            return Integer.parseInt(normalizedToken);

        } catch (NumberFormatException ignore) {

            log.debug("Convert token to integer failed.");
            return null;

        }
    }

    public static ComponentContext<?> promptForInviteToken() {
        ComponentFlow flow = Application.getComponentFlowBuilder().clone().reset()
                .withStringInput("inviteToken")
                .name("Please enter the Invite Token. You can find the Invite Token in the create node window in the web panel:")
                .and().build();
        return flow.run().getContext();
    }
}
