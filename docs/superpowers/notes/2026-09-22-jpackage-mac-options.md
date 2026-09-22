# jpackage macOS signing options, read from the runner (2026-09-22)

Task 12 of the macOS code-signing plan. This is a recorded fact for Task 13 to build on, not a code change. It was produced by a temporary workflow, `jpackage-probe.yml`, run on a throwaway branch (`jpackage-probe-2026-09-22`) on `stepss-java-ui`, never merged and deleted after use. `release.yml` was never touched or dispatched.

## Runner and JDK

Runner: `macos-latest` (GitHub-hosted, arm64). JDK: Temurin 21.0.12+7 (`setup-java@v5`, `distribution: temurin`, `java-version: "21"`). `jpackage --version` reported `21.0.12.1`. `JAVA_HOME` resolved to `/Users/runner/hostedtoolcache/Java_Temurin-Hotspot_jdk/21.0.12-101.0/arm64/Contents/Home`.

Runs: https://github.com/SPS-L/stepss-java-ui/actions/runs/35774123216 (first pass, brief's exact commands), https://github.com/SPS-L/stepss-java-ui/actions/runs/35774462833 (added the decompiled identity-matching check), https://github.com/SPS-L/stepss-java-ui/actions/runs/35774616804 (full bytecode dump of the concatenation logic).

## A correction to the brief's own extraction command

Step 1 of the brief's own probe script was `jpackage --help | sed -n '/Platform dependent option/,$p'`. On this JDK that heading exists (`Platform dependent options for creating the application package:`), but it introduces only `--mac-dmg-content`. The other nine `--mac-*` options (`--mac-package-identifier`, `--mac-package-name`, `--mac-package-signing-prefix`, `--mac-sign`, `--mac-signing-keychain`, `--mac-signing-key-user-name`, `--mac-app-store`, `--mac-entitlements`, `--mac-app-category`) are listed earlier in the output, directly after `--module`, under `Options for creating the application launcher(s):`, with no "Platform dependent" heading of their own. A reader who trusts the brief's sed line alone would see only one of ten options. The full option list below was read from the unfiltered `jpackage --help` output.

## Every `--mac-*` option this jpackage accepts, exact spelling

```
--mac-package-identifier <ID string>
        An identifier that uniquely identifies the application for macOS
        Defaults to the main class name.
        May only use alphanumeric (A-Z,a-z,0-9), hyphen (-),
        and period (.) characters.
--mac-package-name <name string>
        Name of the application as it appears in the Menu Bar
        This can be different from the application name.
        This name must be less than 16 characters long and be suitable for
        displaying in the menu bar and the application Info window.
        Defaults to the application name.
--mac-package-signing-prefix <prefix string>
        When signing the application package, this value is prefixed
        to all components that need to be signed that don't have
        an existing package identifier.
--mac-sign
        Request that the package or the predefined application image be
        signed.
--mac-signing-keychain <keychain name>
        Name of the keychain to search for the signing identity
        If not specified, the standard keychains are used.
--mac-signing-key-user-name <team name>
        Team or user name portion of Apple signing identities.
--mac-app-store
        Indicates that the jpackage output is intended for the
        Mac App Store.
--mac-entitlements <file path>
        Path to file containing entitlements to use when signing
        executables and libraries in the bundle.
--mac-app-category <category string>
        String used to construct LSApplicationCategoryType in
        application plist.  The default value is "utilities".
--mac-dmg-content <additional content path>[,<additional content path>...]
        Include all the referenced content in the dmg.
        This option can be used multiple times.
```

There is also `--mac-sign [<additional signing options>...]` as a bare mode flag noted in the samples section (used with `--app-image` to sign a predefined image), same option as `--mac-sign` above.

## Question 1: does `--mac-signing-key-user-name` take the identity with or without the `Developer ID Application: ` prefix

Without the prefix. Pass the team or user name portion alone, for our identity that is `Cyprus University of Technology (SWZD63F3C7)`, not `Developer ID Application: Cyprus University of Technology (SWZD63F3C7)`.

This is stated by the `--help` text itself ("Team or user name portion of Apple signing identities") and confirmed by decompiling the actual matching logic. `jdk.jpackage.internal.MacCertificate.findCertificateKey(String keyPrefix, String teamName, String keychainName)` is called by `MacAppBundler` with `keyPrefix = "Developer ID Application:"` (verified from the class's own constant pool, entry `#181`/`#182`, no trailing space after the colon). Inside `findCertificateKey`, the bytecode (`MacCertificate.class`, method body captured in full) does, in source terms:

```
boolean useAsIs = keyPrefix == null
    || teamName.startsWith(keyPrefix)
    || teamName.startsWith("Developer ID")
    || teamName.startsWith("3rd Party Mac");
String key = useAsIs ? teamName : (keyPrefix + teamName);
```

So: if the value you pass already looks like it carries a recognized prefix (starts with `Developer ID Application:`, or more loosely with `Developer ID` or `3rd Party Mac`), jpackage uses it exactly as given. Otherwise it builds the search key itself by concatenating `keyPrefix` and `teamName`. That concatenation is a plain `StringConcatFactory` call with template `\u0001\u0001` (verified in the class's `BootstrapMethods` table, the only entry in the class), i.e. the two arguments are joined with nothing in between, not even a space. Reading only the `keyPrefix` constant (`"Developer ID Application:"`, no trailing space) and the concatenation template together, passing a bare team name builds `"Developer ID Application:" + teamName`, with no space after the colon.

Note on that last point: this is what the bytecode shows the JDK does; it was not exercised end to end against a real keychain identity here (no signing certificate is available to this probe, and doing so was out of scope), so whether the missing space between the colon and the name ends up mattering to whatever downstream match `getFindCertificateOutput` performs against `security`'s output is not something this probe verified. Flagging it as a loose end rather than asserting either way.

## Question 2: do the default entitlements already contain `com.apple.security.cs.disable-library-validation`

Yes. The full default entitlements plist, extracted from `jdk.jpackage.jmod` (`classes/jdk/jpackage/internal/resources/entitlements.plist`, the file applied when `--mac-entitlements` is not given and the target is not `--mac-app-store`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
    <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
    <true/>
    <key>com.apple.security.cs.disable-library-validation</key>
    <true/>
    <key>com.apple.security.cs.allow-dyld-environment-variables</key>
    <true/>
    <key>com.apple.security.cs.debugger</key>
    <true/>
    <key>com.apple.security.device.audio-input</key>
    <true/>
</dict>
</plist>
```

`com.apple.security.cs.disable-library-validation` is present and set to `true`. Passing `--mac-entitlements` replaces this file wholesale, so Task 13's plan to assert that entitlement on the built app (rather than trust the default silently surviving a custom `--mac-entitlements`) is the right call: any custom entitlements file used for Homebrew gfortran loading must also carry `allow-jit`, `allow-unsigned-executable-memory`, `allow-dyld-environment-variables`, `debugger` and `device.audio-input` if those JVM-side behaviors still matter, since none of them survive a custom entitlements file by default.

There is a second, separate plist at the same location for App Store builds: `classes/jdk/jpackage/internal/resources/sandbox.plist`, used instead of `entitlements.plist` when `--mac-app-store` is set. Its full content was not read (out of scope; STEPSS does not target the Mac App Store).

## How the plist was found

The brief's own discovery command, `find "$jdk" -name '*.plist' -path '*jpackage*'`, was tried first and found nothing, printing only `JAVA_HOME=...`. A broader `find "$jdk" -iname '*.plist'` (dropping the `jpackage` path filter) also found nothing. Neither could, because `jpackage`'s default resource plists are not loose files anywhere under `$JAVA_HOME`; they are compiled resources packed inside `$JAVA_HOME/jmods/jdk.jpackage.jmod`, which is itself a zip archive, not something a filesystem `find` walks into. That jmod was located and `unzip -l`/`unzip -p` was used to list and extract `entitlements.plist` and its siblings directly from the archive. A `jimage list`/`jimage extract` pass against `$JAVA_HOME/lib/modules` (the linked runtime image) was also tried as a second fallback and did not surface the plist either, since `jlink`'s runtime image only ships classes needed to run, not jpackage's packaging-time resources. The working method was reading the jmod directly.

## Concerns for whoever reads this before Task 13

- `gh workflow run jpackage-probe.yml --ref <branch>` (the brief's own Step 2) does not work as written: GitHub refuses to dispatch a `workflow_dispatch` workflow that is not registered on the repository's default branch, confirmed with both `gh workflow run` and the raw `POST .../actions/workflows/jpackage-probe.yml/dispatches` API, both 404. `stepss-java-ui`'s `main` was not touched to work around this (per this task's explicit instruction). Instead the probe workflow's trigger was changed to `push` on that one branch name, which runs automatically on every push to it without ever needing to exist on `main`. If a future probe of this kind is needed again, plan for this: either register the workflow on `main` first (not done here, deliberately) or use a `push` trigger scoped to a throwaway branch the way this one did.
- The `useAsIs`/prefix-concatenation behavior above was read from JDK 21.0.12 (Temurin) bytecode on `macos-latest` on 2026-09-22. The brief itself warns the option set differs between JDK versions; this note is only as current as that JDK build.
