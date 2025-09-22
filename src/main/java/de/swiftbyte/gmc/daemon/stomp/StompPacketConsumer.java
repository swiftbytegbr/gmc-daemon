package de.swiftbyte.gmc.daemon.stomp;

public interface StompPacketConsumer<T> {

    void onReceive(T packet);
}
