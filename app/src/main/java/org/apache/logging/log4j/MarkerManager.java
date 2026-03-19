package org.apache.logging.log4j;

public class MarkerManager {
    public static Marker getMarker(String name) {
        return new Marker() {
            public String getName() { return name; }
        };
    }
}
