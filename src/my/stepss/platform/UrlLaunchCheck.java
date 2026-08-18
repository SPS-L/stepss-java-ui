package my.stepss.platform;

import org.apache.commons.exec.CommandLine;

/**
 * Headless checks for the per-platform browser command. This repository has no
 * unit-test framework; run these from {@code tools/url-harness.sh}.
 *
 * <p>Nothing here launches anything, which is the point: the command that
 * broke on Windows in issue #18 is a value, and a value can be asserted on
 * Linux. What cannot be asserted here is the half that actually failed - a
 * Desktop peer that reports BROWSE as supported and then throws - so
 * {@link PlatformLauncher#openUrl} is still only proved on the machine that
 * has the fault.
 */
public final class UrlLaunchCheck {

    private static int failures = 0;

    private static final String URL = "https://github.com/SPS-L/stepss-java-ui/releases";

    private UrlLaunchCheck() {
    }

    public static void main(String[] args) {
        checkEveryPlatformPassesTheAddress();
        checkWindowsKeepsTheEmptyTitle();
        checkWindowsQuotesAQueryString();
        checkUnixPassesTheAddressUnquoted();
        checkTheDeadBrowserListIsGone();
        System.out.println(failures == 0 ? "ALL URL CHECKS PASSED"
                : failures + " URL CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void checkEveryPlatformPassesTheAddress() {
        for (Platform p : Platform.values()) {
            check(p + " passes the address", line(p, URL).contains(URL));
        }
    }

    private static void checkWindowsKeepsTheEmptyTitle() {
        // start reads its first quoted argument as a window title. Without the
        // empty one it opens a console named after the address and never
        // browses to it, which is the failure this argument exists to prevent.
        String[] args = PlatformLauncher.urlCommand(
                Platform.WINDOWS_X86_64, URL).getArguments();
        check("Windows goes through cmd start",
                args.length >= 3 && "/c".equals(args[0]) && "start".equals(args[1]));
        check("Windows keeps the empty window title", "\"\"".equals(args[2]));
        check("the address comes after the title",
                args.length == 4 && args[3].contains(URL));
    }

    private static void checkWindowsQuotesAQueryString() {
        // cmd.exe reads & as a command separator, so an unquoted query string
        // is truncated at the first parameter and its tail run as a command.
        String withQuery = URL + "?utm=a&b=c";
        String[] args = PlatformLauncher.urlCommand(
                Platform.WINDOWS_X86_64, withQuery).getArguments();
        check("Windows quotes the address",
                args[3].equals("\"" + withQuery + "\""));
    }

    private static void checkUnixPassesTheAddressUnquoted() {
        // The opposite of the Windows case: open and xdg-open receive argv
        // directly, with no shell in between, so quotes added here would be
        // part of the address rather than around it.
        for (Platform p : new Platform[] {Platform.LINUX_X86_64, Platform.MACOS_ARM64}) {
            CommandLine cmd = PlatformLauncher.urlCommand(p, URL);
            check(p + " uses the desktop's own opener",
                    cmd.getExecutable().equals(
                            p == Platform.MACOS_ARM64 ? "open" : "xdg-open"));
            check(p + " passes the address unquoted",
                    cmd.getArguments().length == 1
                            && cmd.getArguments()[0].equals(URL));
        }
    }

    private static void checkTheDeadBrowserListIsGone() {
        // BareBonesBrowserLaunch tried nine browsers by name on Linux, four of
        // which (kazehakase, conkeror, epiphany, mozilla) no longer resolve on
        // a current desktop. xdg-open asks the desktop instead, so a name here
        // would mean the list had come back.
        String line = line(Platform.LINUX_X86_64, URL);
        for (String browser : new String[] {"google-chrome", "firefox", "opera",
            "epiphany", "konqueror", "conkeror", "midori", "kazehakase", "mozilla"}) {
            check("Linux does not name " + browser, !line.contains(browser));
        }
    }

    private static String line(Platform p, String url) {
        CommandLine cmd = PlatformLauncher.urlCommand(p, url);
        return cmd.getExecutable() + " " + String.join(" ", cmd.getArguments());
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            System.out.println("FAIL " + what);
            failures++;
        }
    }
}
