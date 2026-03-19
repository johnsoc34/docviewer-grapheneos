package org.apache.logging.log4j;
import org.apache.logging.log4j.util.Supplier;
public interface LogBuilder {
    static final LogBuilder INSTANCE = new LogBuilder() {};
    default LogBuilder withLocation() { return this; }
    default LogBuilder withMarker(Marker marker) { return this; }
    default LogBuilder withThrowable(Throwable t) { return this; }
    default void log() {}
    default void log(String message) {}
    default void log(Object message) {}
    default void log(String message, Object p0) {}
    default void log(String message, Object p0, Object p1) {}
    default void log(String message, Object p0, Object p1, Object p2) {}
    default void log(String message, Object p0, Object p1, Object p2, Object p3) {}
    default void log(String message, Object... params) {}
    default void log(String message, Throwable t) {}
    default void log(Object message, Throwable t) {}
    default void log(Supplier<?> msgSupplier) {}
    default void log(String message, Supplier<?>... paramSuppliers) {}
    default void log(Supplier<?> msgSupplier, Throwable t) {}
}
