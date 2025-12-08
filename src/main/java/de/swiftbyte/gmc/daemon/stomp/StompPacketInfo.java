package de.swiftbyte.gmc.daemon.stomp;

import org.jspecify.annotations.NonNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface StompPacketInfo {

    @NonNull String[] path();

    @NonNull Class<?> packetClass();

}
