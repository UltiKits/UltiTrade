package com.ultikits.plugins.trade;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure version policy; SNAPSHOT builds count as their numeric version, not as a prior release. */
final class RuntimeCompatibility {
    private static final Pattern SERVER = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-R\\d+\\.\\d+-SNAPSHOT)?");
    private static final Pattern FRAMEWORK = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:-SNAPSHOT)?");

    enum Result { COMPATIBLE, REFUSE, UNKNOWN }

    private RuntimeCompatibility() { }

    static Result check(String serverVersion, String frameworkVersion) {
        int[] server = parse(SERVER, serverVersion);
        int[] framework = parse(FRAMEWORK, frameworkVersion);
        if (server == null || framework == null) {
            return Result.UNKNOWN;
        }
        boolean oldServer = server[0] < 1 || (server[0] == 1 && server[1] < 21);
        boolean oldFramework = framework[0] < 6 || (framework[0] == 6 && framework[1] < 4);
        return oldServer && oldFramework ? Result.REFUSE : Result.COMPATIBLE;
    }

    private static int[] parse(Pattern pattern, String version) {
        if (version == null) {
            return null;
        }
        Matcher match = pattern.matcher(version);
        if (!match.matches()) {
            return null;
        }
        try {
            return new int[] {Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                    match.group(3) == null ? 0 : Integer.parseInt(match.group(3))};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
