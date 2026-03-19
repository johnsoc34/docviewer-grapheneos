package org.apache.logging.log4j;

/**
 * No-op shim for log4j Logger interface.
 */
public interface Logger {
    default void debug(Object message) {}
    default void debug(String message, Object... params) {}
    default void debug(String message, Throwable t) {}
    default void info(Object message) {}
    default void info(String message, Object... params) {}
    default void info(String message, Throwable t) {}
    default void warn(Object message) {}
    default void warn(String message, Object... params) {}
    default void warn(String message, Throwable t) {}
    default void error(Object message) {}
    default void error(String message, Object... params) {}
    default void error(Object message, Throwable t) {}
    default void error(String message, Throwable t) {}
    default void fatal(Object message) {}
    default void fatal(String message, Object... params) {}
    default void fatal(Object message, Throwable t) {}
    default void trace(Object message) {}
    default void trace(String message, Object... params) {}
    default void trace(String message, Throwable t) {}
    default boolean isDebugEnabled() { return false; }
    default boolean isInfoEnabled() { return false; }
    default boolean isWarnEnabled() { return false; }
    default boolean isErrorEnabled() { return false; }
    default boolean isTraceEnabled() { return false; }
    default boolean isFatalEnabled() { return false; }
    default void log(Level level, String message, Object... params) {}
    default void log(Level level, Object message) {}
    default void log(Level level, Object message, Throwable t) {}
    default void log(Level level, String message, Throwable t) {}

    // Fluent API - POI calls these
    default LogBuilder atDebug() { return LogBuilder.INSTANCE; }
    default LogBuilder atInfo() { return LogBuilder.INSTANCE; }
    default LogBuilder atWarn() { return LogBuilder.INSTANCE; }
    default LogBuilder atError() { return LogBuilder.INSTANCE; }
    default LogBuilder atFatal() { return LogBuilder.INSTANCE; }
    default LogBuilder atTrace() { return LogBuilder.INSTANCE; }
}
