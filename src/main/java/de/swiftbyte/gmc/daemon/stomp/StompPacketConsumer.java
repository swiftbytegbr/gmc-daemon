package de.swiftbyte.gmc.daemon.stomp;

import de.swiftbyte.gmc.daemon.Node;
import org.jspecify.annotations.NonNull;

public interface StompPacketConsumer<T> {

    default Node getNode() {
        Node node = Node.INSTANCE;
        if (node == null) {
            throw new IllegalStateException("Node is not initialized yet.");
        }
        return node;
    }

    void onReceive(@NonNull T packet);
}
