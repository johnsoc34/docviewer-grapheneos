package org.apache.logging.log4j;

/**
 * No-op shim for log4j LogManager.
 */
public class LogManager {
    private static final Logger NOOP = new Logger() {};

    public static Logger getLogger(Class<?> clazz) { return NOOP; }
    public static Logger getLogger(String name) { return NOOP; }
    public static Logger getLogger() { return NOOP; }
    public static Logger getRootLogger() { return NOOP; }
    public static Logger getFormatterLogger(Class<?> clazz) { return NOOP; }
    public static Logger getFormatterLogger(String name) { return NOOP; }
}
