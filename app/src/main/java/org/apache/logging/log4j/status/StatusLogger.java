package org.apache.logging.log4j.status;

import org.apache.logging.log4j.Logger;

public class StatusLogger implements Logger {
    private static final StatusLogger INSTANCE = new StatusLogger();
    public static StatusLogger getLogger() { return INSTANCE; }
}
