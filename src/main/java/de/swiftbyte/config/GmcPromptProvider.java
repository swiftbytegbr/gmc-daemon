package de.swiftbyte.config;

import de.swiftbyte.Node;
import org.jline.utils.AttributedString;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.jline.PromptProvider;

@Configuration
public class GmcPromptProvider implements PromptProvider {

    @Override
    public AttributedString getPrompt() {

        Node node = Node.INSTANCE;

        return new AttributedString(node.getNodeName() + "@" + node.getTeamName() + ": ");
    }
}
