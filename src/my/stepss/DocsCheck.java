package my.stepss;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Checks that every address in {@link Docs} still resolves. This repository has
 * no unit-test framework; run this from {@code tools/docs-harness.sh}.
 *
 * <p>Deep links are a contract with stepss-docs and nothing on either side
 * enforces it. A renamed heading does not 404: the page still loads and the
 * browser lands at the top of it, so the user gets a page about the right
 * subject with no sign that the link meant to point somewhere further down. A
 * moved page does 404, but only for whoever presses that one button. Neither
 * shows up in a build, which is why this asks the site.
 *
 * <p>Two halves, and the first does not need the network. The structural
 * checks catch a constant that no ? button offers, a page listed twice in one
 * menu, and an address assembled wrongly; the fetches catch the documentation
 * moving underneath us. {@code --offline} runs the first half alone, for a
 * machine that cannot reach the site.
 */
public final class DocsCheck {

    private static int failures = 0;
    private static int checked = 0;

    private DocsCheck() {
    }

    public static void main(String[] args) throws Exception {
        boolean offline = args.length > 0 && "--offline".equals(args[0]);

        Map<String, Docs.Page> pages = pageConstants();
        Map<String, Docs.Page[]> groups = groupConstants();

        checkTheSiteIsARootUrl();
        checkEveryPageIsOffered(pages, groups);
        checkNoMenuRepeatsItself(groups);
        checkNoTwoConstantsShareAnAddress(pages);

        if (offline) {
            System.out.println("Skipping the site: --offline");
        } else {
            checkEveryAddressResolves(pages);
        }

        System.out.println(failures == 0
                ? "ALL DOCS CHECKS PASSED (" + checked + " checks, "
                        + pages.size() + " pages, " + groups.size() + " menus)"
                : failures + " DOCS CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** {@code SITE} is concatenated with a relative path, so it has to end in a slash. */
    private static void checkTheSiteIsARootUrl() {
        check("the site address ends in a slash", Docs.SITE.endsWith("/"));
        check("the site address is https", Docs.SITE.startsWith("https://"));
    }

    /**
     * A page constant no menu names is a link nobody can follow. It is the
     * shape a half-finished edit leaves behind, and it costs nothing to say so.
     */
    private static void checkEveryPageIsOffered(Map<String, Docs.Page> pages,
            Map<String, Docs.Page[]> groups) {
        Set<Docs.Page> offered = new LinkedHashSet<>();
        for (Docs.Page[] group : groups.values()) {
            check("a menu with nothing in it", group.length > 0);
            for (Docs.Page page : group) {
                offered.add(page);
            }
        }
        for (Map.Entry<String, Docs.Page> entry : pages.entrySet()) {
            check(entry.getKey() + " is offered by some ? button",
                    offered.contains(entry.getValue()));
        }
    }

    /** The same page twice in one menu is two identical items to choose between. */
    private static void checkNoMenuRepeatsItself(Map<String, Docs.Page[]> groups) {
        for (Map.Entry<String, Docs.Page[]> entry : groups.entrySet()) {
            Set<String> seen = new HashSet<>();
            for (Docs.Page page : entry.getValue()) {
                check(entry.getKey() + " lists " + page.title() + " once",
                        seen.add(page.path()));
            }
        }
    }

    /** Two constants for one address means one of them will be missed on a rename. */
    private static void checkNoTwoConstantsShareAnAddress(Map<String, Docs.Page> pages) {
        Map<String, String> byPath = new LinkedHashMap<>();
        for (Map.Entry<String, Docs.Page> entry : pages.entrySet()) {
            String path = entry.getValue().path();
            check("the path in " + entry.getKey() + " is site-relative",
                    !path.startsWith("/") && !path.startsWith("http"));
            String first = byPath.put(path, entry.getKey());
            check(entry.getKey() + " is not a second name for " + first,
                    first == null);
        }
    }

    /**
     * Asks the site for each address, once per distinct page, and reads the
     * anchor out of what comes back.
     *
     * <p>The status code alone would pass a link whose heading has been
     * renamed, which is the failure this is here to catch, so a fragment is
     * checked against the {@code id} attributes in the HTML that Starlight
     * puts on every heading.
     */
    private static void checkEveryAddressResolves(Map<String, Docs.Page> pages)
            throws InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // One request per page rather than per constant: seven of the ?
        // buttons point at sections of gui/interface, and asking for it seven
        // times would also report one outage seven times.
        Set<String> paths = new LinkedHashSet<>();
        for (Docs.Page page : pages.values()) {
            paths.add(withoutFragment(page.path()));
        }
        Map<String, String> bodies = new LinkedHashMap<>();
        for (String path : paths) {
            try {
                bodies.put(path, fetch(client, Docs.SITE + path));
            } catch (IOException problem) {
                // Told apart from a broken link on purpose: a machine with no
                // route to the site has learned nothing about the
                // documentation, and reporting that as a dead link would send
                // somebody to fix a page that is fine.
                System.out.println("UNREACHABLE " + Docs.SITE + path
                        + ": " + problem);
                failures++;
            }
        }

        for (Map.Entry<String, Docs.Page> entry : pages.entrySet()) {
            Docs.Page page = entry.getValue();
            String path = withoutFragment(page.path());
            String body = bodies.get(path);
            if (body == null) {
                continue;
            }
            check(entry.getKey() + " resolves (" + Docs.SITE + path + ")",
                    !body.isEmpty());
            int hash = page.path().indexOf('#');
            if (hash >= 0 && !body.isEmpty()) {
                String fragment = page.path().substring(hash + 1);
                check(entry.getKey() + " still finds #" + fragment,
                        body.contains("id=\"" + fragment + "\""));
            }
        }
    }

    private static String withoutFragment(String path) {
        int hash = path.indexOf('#');
        return hash < 0 ? path : path.substring(0, hash);
    }

    /** The page body, or the empty string when the site answered with an error. */
    private static String fetch(HttpClient client, String url)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "stepss-docs-check")
                .GET()
                .build();
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 ? response.body() : "";
    }

    /** Every {@code public static final Page} on {@link Docs}, by field name. */
    private static Map<String, Docs.Page> pageConstants() throws IllegalAccessException {
        Map<String, Docs.Page> found = new LinkedHashMap<>();
        for (Field field : Docs.class.getFields()) {
            if (isConstant(field) && field.getType() == Docs.Page.class) {
                found.put(field.getName(), (Docs.Page) field.get(null));
            }
        }
        return found;
    }

    /** Every {@code public static final Page[]} on {@link Docs}, by field name. */
    private static Map<String, Docs.Page[]> groupConstants() throws IllegalAccessException {
        Map<String, Docs.Page[]> found = new LinkedHashMap<>();
        for (Field field : Docs.class.getFields()) {
            if (isConstant(field) && field.getType() == Docs.Page[].class) {
                found.put(field.getName(), (Docs.Page[]) field.get(null));
            }
        }
        return found;
    }

    private static boolean isConstant(Field field) {
        int flags = field.getModifiers();
        return Modifier.isPublic(flags) && Modifier.isStatic(flags)
                && Modifier.isFinal(flags);
    }

    private static void check(String what, boolean ok) {
        checked++;
        if (!ok) {
            System.out.println("FAIL " + what);
            failures++;
        }
    }
}
