package my.ramses.platform;

import java.util.HashMap;
import java.util.Map;

public final class ToolSpec {

    public enum Kind { ZIP, TGZ, RAW }

    public static final class Payload {
        public final String resource;
        public final Kind kind;
        public final String member;
        public final String extractedName;
        public final boolean executable;

        public Payload(String resource, Kind kind, String member,
                       String extractedName, boolean executable) {
            this.resource = resource;
            this.kind = kind;
            this.member = member;
            this.extractedName = extractedName;
            this.executable = executable;
        }
    }

    private final String id;
    private final Map<Platform, Payload> payloads = new HashMap<Platform, Payload>();

    public ToolSpec(String id) {
        this.id = id;
    }

    public ToolSpec on(Platform p, Payload payload) {
        payloads.put(p, payload);
        return this;
    }

    public String id() {
        return id;
    }

    public Payload payloadFor(Platform p) {
        return payloads.get(p);
    }

    public boolean availableOn(Platform p) {
        return payloads.containsKey(p);
    }
}
