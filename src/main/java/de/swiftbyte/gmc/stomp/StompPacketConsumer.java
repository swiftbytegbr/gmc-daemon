package de.swiftbyte.gmc.stomp;

public interface StompPacketConsumer<T> {

    void onReceive(T packet);
}
