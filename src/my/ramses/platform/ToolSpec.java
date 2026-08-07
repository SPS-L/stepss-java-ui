package my.ramses.platform;

import java.util.HashMap;
import java.util.Map;

public final class ToolSpec {

    public enum Kind { ZIP, TGZ, RAW, ZIP_TREE }

    public static final class Payload {
        public final String resource;
        public final Kind kind;
        public final String member;
        public final String extractedName;
        public final boolean executable;
        /**
         * For {@link Kind#ZIP_TREE} only: the archive-relative path prefixes
         * to unpack. A prefix ending in '/' matches a subtree; anything else
         * must match an entry exactly. Null for every other kind.
         */
        public final java.util.List<String> retain;

        public Payload(String resource, Kind kind, String member,
                       String extractedName, boolean executable) {
            this.resource = resource;
            this.kind = kind;
            this.member = member;
            this.extractedName = extractedName;
            this.executable = executable;
            this.retain = null;
        }

        /** ZIP_TREE payload: unpacks the retained prefixes into {@code extractedName}/. */
        public Payload(String resource, String extractedName, java.util.List<String> retain) {
            this.resource = resource;
            this.kind = Kind.ZIP_TREE;
            this.member = null;
            this.extractedName = extractedName;
            this.executable = false;
            this.retain = java.util.Collections.unmodifiableList(
                    new java.util.ArrayList<String>(retain));
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
