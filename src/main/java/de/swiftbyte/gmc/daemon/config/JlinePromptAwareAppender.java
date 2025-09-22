package de.swiftbyte.gmc.daemon.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import lombok.Setter;
import org.jline.reader.LineReader;

import java.nio.charset.StandardCharsets;

@Setter
public class JlinePromptAwareAppender extends AppenderBase<ILoggingEvent> {

    private final Object lineReaderLock = new Object();
    private LineReader lineReader;
    private Encoder<ILoggingEvent> encoder;

    @Override
    public void start() {
        if (this.encoder == null) {
            addError("No encoder set for JlinePromptAwareAppender");
            return;
        }
        if (!this.encoder.isStarted()) {
            this.encoder.start();
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        try {
            byte[] bytes = this.encoder.encode(eventObject);
            String msg = new String(bytes, StandardCharsets.UTF_8);

            if (lineReader != null) {
                synchronized (lineReaderLock) {
                    lineReader.printAbove(msg);
                }
            } else {
                System.out.print(msg);
                System.out.flush();
            }
        } catch (Exception e) {
            addError("Failed to append log via JlinePromptAwareAppender", e);
        }
    }
}

