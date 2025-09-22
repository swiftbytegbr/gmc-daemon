package de.swiftbyte.gmc.daemon.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import org.jline.reader.LineReader;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.Iterator;

@Configuration
public class JlineLoggingConfiguration {

    private final LineReader lineReader;

    public JlineLoggingConfiguration(LineReader lineReader) {
        this.lineReader = lineReader;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void configureLogback() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);

        ConsoleAppender<ILoggingEvent> console = null;
        for (Iterator<Appender<ILoggingEvent>> it = root.iteratorForAppenders(); it.hasNext(); ) {
            Appender<ILoggingEvent> app = it.next();
            if (app instanceof ConsoleAppender) {
                console = (ConsoleAppender<ILoggingEvent>) app;
                break;
            }
        }

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);

        if (console != null && console.getEncoder() instanceof PatternLayoutEncoder existing) {
            encoder.setPattern(existing.getPattern());
        } else {
            encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} %5p %c{1} - %m%n");
        }

        encoder.start();

        JlinePromptAwareAppender jlineAppender = new JlinePromptAwareAppender();
        jlineAppender.setContext(context);
        jlineAppender.setEncoder(encoder);
        jlineAppender.setLineReader(lineReader);
        jlineAppender.setName("JLINE_PROMPT_AWARE");
        jlineAppender.start();

        if (console != null) {
            root.detachAppender(console);
        }
        root.addAppender(jlineAppender);
    }
}

