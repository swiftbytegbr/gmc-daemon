package de.swiftbyte.gmc.daemon.stomp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.management.OperatingSystemMXBean;
import de.swiftbyte.gmc.common.entity.NodeData;
import de.swiftbyte.gmc.common.packet.from.daemon.node.NodeLoginPacket;
import de.swiftbyte.gmc.daemon.Application;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.utils.CommonUtils;
import de.swiftbyte.gmc.daemon.utils.ConnectionState;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;

@Slf4j
public class StompHandler {

    // 2MB
    private static final int MAX_MESSAGE_BUFFER_SIZE_BYTES = 1024 * 1024 * 2;

    private static StompSession session;

    public static boolean initialiseStomp() {

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(MAX_MESSAGE_BUFFER_SIZE_BYTES);
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient(container));
        stompClient.setInboundMessageSizeLimit(MAX_MESSAGE_BUFFER_SIZE_BYTES);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Node-Id", Node.INSTANCE.getNodeId());
        headers.add("Node-Secret", Node.INSTANCE.getSecret());


        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).registerModule(new JavaTimeModule()));

        stompClient.setMessageConverter(converter);
        try {
            log.debug("Connecting WebSocket to {}", Application.getWebsocketUrl());
            session = stompClient.connectAsync(Application.getWebsocketUrl(), headers, new StompSessionHandler()).get();
            scanForPacketListeners();
        } catch (InterruptedException | ExecutionException e) {

            if (e.getMessage() != null && e.getMessage().contains("Failed to handle HTTP response code [401]")) {
                log.error("Backend rejected connection. When you just deleted the node, please execute the 'delete' command in the daemon console as well.");
                return false;
            }

            log.error("Failed to establish connection to backend. Is the backend running?");
            log.debug("Error: ", e);
            return false;
        }
        return true;
    }

    public synchronized static void send(String destination, Object payload) {
        if (session == null) {
            if (Node.INSTANCE.getConnectionState() != ConnectionState.RECONNECTING) {
                log.error("Failed to send packet to {} because the session is null.", destination);
            }
            return;
        }

        if (!session.isConnected()) {
            log.error("Failed to send packet to {} because the session is not connected. Is the backend running?", destination);
            Node.INSTANCE.setConnectionState(ConnectionState.RECONNECTING);
            return;
        }

        session.send(destination, payload);
    }

    public static void disconnect() {
        session.disconnect();
        session = null;
    }

    private static void scanForPacketListeners() {

        Reflections reflections = new Reflections(Application.class.getPackageName().split("\\.")[0]);

        reflections.getTypesAnnotatedWith(StompPacketInfo.class).forEach(clazz -> {

            if (StompPacketConsumer.class.isAssignableFrom(clazz)) {

                StompPacketInfo annotation = clazz.getAnnotation(StompPacketInfo.class);
                StompPacketConsumer<Object> packetConsumer;

                try {
                    Constructor<?> constructor = clazz.getConstructor();
                    packetConsumer = (StompPacketConsumer<Object>) constructor.newInstance();
                } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                         InvocationTargetException e) {
                    log.error("Failed to find default constructor for class {}.", clazz.getName(), e);
                    return;
                }

                for (String path : annotation.path()) {
                    session.subscribe(path, new StompFrameHandler() {
                        @Override
                        public Type getPayloadType(StompHeaders headers) {
                            return annotation.packetClass();
                        }

                        @Override
                        public void handleFrame(StompHeaders headers, Object payload) {
                            new Thread(() -> packetConsumer.onReceive(payload)).start();
                        }
                    });
                }

            } else {
                log.error("Found class annotated with @StompPacketInfo that does not implement StompPacketConsumer: {}", clazz.getName());
            }
        });
    }

    private static class StompSessionHandler extends StompSessionHandlerAdapter {

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            log.debug("Connected to session: {}", session.getSessionId());
            super.afterConnected(session, connectedHeaders);

            NodeLoginPacket loginPacket = new NodeLoginPacket();

            loginPacket.setDaemonVersion(Application.getVersion());

            NodeData nodeData = new NodeData();
            nodeData.setOs(System.getProperty("os.name"));
            try {
                nodeData.setHostname(InetAddress.getLocalHost().getHostName());
            } catch (UnknownHostException e) {
                nodeData.setHostname("UNKNOWN");
                log.warn("Failed to fetch hostname.");
            }
            nodeData.setRam((int) (((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize() / (1024 * 1024)));
            nodeData.setIpAddresses(CommonUtils.getSystemIpAddresses());
            nodeData.setStorage(CommonUtils.getSystemStorages());
            nodeData.setCpu(CommonUtils.getSystemCpu());

            loginPacket.setNodeData(nodeData);

            log.debug("Sending login packet: {} to /node/login", loginPacket);

            session.send("/app/node/login", loginPacket);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable e) {
            if (!session.isConnected()) {
                log.error("Failed to send packet to backend because the session is not connected. Is the backend running?");
                Node.INSTANCE.setConnectionState(ConnectionState.RECONNECTING);
            } else {
                log.error("An error occurred while communicating with the backend.", e);
            }
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
