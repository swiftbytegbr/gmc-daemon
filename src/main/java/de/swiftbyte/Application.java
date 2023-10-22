package de.swiftbyte;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jline.builtins.telnet.Telnet;
import org.jline.reader.LineReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.shell.*;
import org.springframework.shell.boot.SpringShellProperties;
import org.springframework.shell.component.context.ComponentContext;
import org.springframework.shell.component.flow.ComponentFlow;
import org.springframework.shell.jline.InteractiveShellRunner;
import org.springframework.shell.jline.ScriptShellRunner;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.commands.Help;
import org.springframework.shell.style.TemplateExecutor;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@Slf4j
@ShellComponent
public class Application {

    private static Node node;

    @Autowired
    private ComponentFlow.Builder componentFlowBuilder;

    public static void main(String[] args) {
        node = new Node();
        node.start();
        SpringApplication.run(Application.class, args);
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onReady(ApplicationStartedEvent event) throws Exception {
        Thread.sleep(1000);
        ComponentFlow flow = componentFlowBuilder.clone().reset()
                .withStringInput("field1")
                .name("Field1")
                .defaultValue("defaultField1Value")
                .and().build();
        ComponentContext<?> context = flow.run().getContext();

        log.debug("Daemon ready...");

        if(node.getConnectionState() == ConnectionState.NOT_JOINED) node.joinTeam();
        else if(node.getConnectionState() == ConnectionState.NOT_CONNECTED) node.connect();
        else {
            log.error("Illegal ConnectionState set... Start is aborted!");
            System.exit(1);
        }
    }
}
