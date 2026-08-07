package my.ramses.platform;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ToolchainDump {

    public static void main(String[] args) throws Exception {
        Platform platform = Platform.current();
        File dir = File.createTempFile("stepss-dump", "");
        if (!dir.delete() || !dir.mkdir()) {
            throw new IllegalStateException("Could not create " + dir);
        }
        Toolchain chain = new Toolchain(platform, dir);
        chain.extractAll();

        List<String> lines = new ArrayList<String>();
        collect(dir, dir, lines);
        Collections.sort(lines);
        System.out.println("platform=" + platform);
        for (String line : lines) {
            System.out.println(line);
        }
        ToolExtractor.deleteRecursively(dir);
    }

    private static void collect(File root, File node, List<String> out) throws Exception {
        File[] kids = node.listFiles();
        if (kids == null) {
            return;
        }
        for (File kid : kids) {
            if (kid.isDirectory()) {
                collect(root, kid, out);
            } else {
                String rel = root.toURI().relativize(kid.toURI()).getPath();
                if (rel.startsWith(".stepss-payload-")) {
                    continue;
                }
                out.add(rel + "  " + sha256(kid) + "  exec=" + kid.canExecute());
            }
        }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        } finally {
            in.close();
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
