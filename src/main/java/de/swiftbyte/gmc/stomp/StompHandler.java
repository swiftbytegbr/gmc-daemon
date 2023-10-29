package de.swiftbyte.gmc.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.ExecutionException;

@Slf4j
public class StompHandler {

    public static void initialiseStomp() {

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Node-Id", "65320b0563b6a95179a650ff");
        headers.add("Node-Secret", "test_secret");


        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String url = "ws://localhost:8080/websocket-nodes";
        StompSessionHandler sessionHandler = new StompSessionHandler();
        try {
            StompSession session = stompClient.connectAsync(url, headers, sessionHandler).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

    }

    private static class StompSessionHandler extends StompSessionHandlerAdapter {

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            log.debug("Connected to session: " + session.getSessionId());
            super.afterConnected(session, connectedHeaders);

            //TODO handle login package

        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
            super.handleException(session, command, headers, payload, exception);
            log.error("An unknown error occurred.", exception);
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            log.info("Received message: {}", payload);
            headers.keySet().forEach(key -> log.info("{}: {}", key, headers.get(key)));
            super.handleFrame(headers, payload);
        }
    }
}
