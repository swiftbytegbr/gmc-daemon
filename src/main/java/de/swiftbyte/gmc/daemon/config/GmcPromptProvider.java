package de.swiftbyte.gmc.daemon.config;

import de.swiftbyte.gmc.daemon.Node;
import org.jline.utils.AttributedString;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.jline.PromptProvider;

@Configuration
public class GmcPromptProvider implements PromptProvider {

    @Override
    public AttributedString getPrompt() {

        Node node = Node.INSTANCE;
        if (node == null) {
            return new AttributedString("$ ");
        }

        return new AttributedString(node.getNodeName().toLowerCase().replace(" ", "-") + "@" + node.getTeamName().toLowerCase().replace(" ", "-") + ": ");
    }
}
