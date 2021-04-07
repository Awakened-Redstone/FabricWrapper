package org.jigsawlabs.fabricwrapper.fabricloader.minecraft;

import org.jetbrains.annotations.Nullable;
import org.jigsawlabs.fabricwrapper.fabricloader.gson.JsonReader;
import org.jigsawlabs.fabricwrapper.fabricloader.gson.JsonToken;
import org.jigsawlabs.fabricwrapper.fabricloader.metadata.ParseMetadataException;
import org.jigsawlabs.fabricwrapper.fabricloader.util.FileSystemUtil;
import org.jigsawlabs.fabricwrapper.fabricloader.util.version.SemanticVersionImpl;
import org.jigsawlabs.fabricwrapper.fabricloader.util.version.SemanticVersionPredicateParser;
import org.jigsawlabs.fabricwrapper.fabricloader.util.VersionParsingException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class McVersionLookup {

    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "0\\.\\d+(\\.\\d+)?a?(_\\d+)?|" // match classic versions first: 0.1.2a_34
                    + "\\d+\\.\\d+(\\.\\d+)?(-pre\\d+| Pre-[Rr]elease \\d+)?|" // modern non-snapshot: 1.2, 1.2.3, optional -preN or " Pre-Release N" suffix
                    + "\\d+\\.\\d+(\\.\\d+)?(-rc\\d+| Rr]elease Candidate \\d+)?|" // 1.16+ Release Candidate
                    + "\\d+w\\d+[a-z]|" // modern snapshot: 12w34a
                    + "[a-c]\\d\\.\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?|" // alpha/beta a1.2.3_45
                    + "(Alpha|Beta) v?\\d+\\.\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?|" // long alpha/beta names: Alpha v1.2.3_45
                    + "Inf?dev (0\\.31 )?\\d+(-\\d+)?|" // long indev/infdev names: Infdev 12345678-9
                    + "(rd|inf)-\\d+|" // early rd-123, inf-123
                    + "1\\.RV-Pre1|3D Shareware v1\\.34" // odd exceptions
    );
    private static final Pattern RELEASE_PATTERN = Pattern.compile("\\d+\\.\\d+(\\.\\d+)?");
    private static final Pattern PRE_RELEASE_PATTERN = Pattern.compile(".+(?:-pre| Pre-[Rr]elease )(\\d+)");
    private static final Pattern RELEASE_CANDIDATE_PATTERN = Pattern.compile(".+(?:-rc| [Rr]elease Candidate )(\\d+)");
    private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("(?:Snapshot )?(\\d+)w0?(0|[1-9]\\d*)([a-z])");
    private static final Pattern BETA_PATTERN = Pattern.compile("(?:b|Beta v?)1\\.(\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?)");
    private static final Pattern ALPHA_PATTERN = Pattern.compile("(?:a|Alpha v?)1\\.(\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?)");
    private static final Pattern INDEV_PATTERN = Pattern.compile("(?:inf-|Inf?dev )(?:0\\.31 )?(\\d+(-\\d+)?)");
    private static final String STRING_DESC = "Ljava/lang/String;";

    private static String normalizeVersion(String name, String release) {
        if (release == null || name.equals(release)) {
            return normalizeVersion(name);
        }

        Matcher matcher;

        if (name.startsWith(release)) {
            matcher = RELEASE_CANDIDATE_PATTERN.matcher(name);
            if (matcher.matches()) {
                String rcBuild = matcher.group(1);

                // This is a hack to fake 1.16's new release candidates to follow on from the 8 pre releases.
                if (release.equals("1.16")) {
                    int build = Integer.parseInt(rcBuild);
                    rcBuild = Integer.toString(8 + build);
                }

                name = String.format("rc.%s", rcBuild);
            } else {
                matcher = PRE_RELEASE_PATTERN.matcher(name);

                if (matcher.matches()) {
                    boolean legacyVersion;

                    try {
                        legacyVersion = SemanticVersionPredicateParser.create("<=1.16").test(new SemanticVersionImpl(release, false));
                    } catch (VersionParsingException e) {
                        throw new RuntimeException("Failed to parse version: " + release);
                    }

                    // Mark pre-releases as 'beta' versions, except for version 1.16 and before, where they are 'rc'
                    if (legacyVersion) {
                        name = String.format("rc.%s", matcher.group(1));
                    } else {
                        name = String.format("beta.%s", matcher.group(1));
                    }
                }
            }
        } else if ((matcher = SNAPSHOT_PATTERN.matcher(name)).matches()) {
            name = String.format("alpha.%s.%s.%s", matcher.group(1), matcher.group(2), matcher.group(3));
        } else {
            name = normalizeVersion(name);
        }

        return String.format("%s-%s", release, name);
    }

    private static String normalizeVersion(String version) {
        // old version normalization scheme
        // do this before the main part of normalization as we can get crazy strings like "Indev 0.31 12345678-9"
        Matcher matcher;

        if ((matcher = BETA_PATTERN.matcher(version)).matches()) { // beta 1.2.3: 1.0.0-beta.2.3
            version = "1.0.0-beta." + matcher.group(1);
        } else if ((matcher = ALPHA_PATTERN.matcher(version)).matches()) { // alpha 1.2.3: 1.0.0-alpha.2.3
            version = "1.0.0-alpha." + matcher.group(1);
        } else if ((matcher = INDEV_PATTERN.matcher(version)).matches()) { // indev/infdev 12345678: 0.31.12345678
            version = "0.31." + matcher.group(1);
        } else if (version.startsWith("c0.")) { // classic: unchanged, except remove prefix
            version = version.substring(1);
        } else if (version.startsWith("rd-")) { // pre-classic
            version = version.substring("rd-".length());
            if ("20090515".equals(version)) version = "150000"; // account for a weird exception to the pre-classic versioning scheme
            version = "0.0.0-rd." + version;
        }

        StringBuilder ret = new StringBuilder(version.length() + 5);
        boolean lastIsDigit = false;
        boolean lastIsLeadingZero = false;
        boolean lastIsSeparator = false;

        for (int i = 0, max = version.length(); i < max; i++) {
            char c = version.charAt(i);

            if (c >= '0' && c <= '9') {
                if (i > 0 && !lastIsDigit && !lastIsSeparator) { // no separator between non-number and number, add one
                    ret.append('.');
                } else if (lastIsDigit && lastIsLeadingZero) { // leading zero in output -> strip
                    ret.setLength(ret.length() - 1);
                }

                lastIsLeadingZero = c == '0' && (!lastIsDigit || lastIsLeadingZero); // leading or continued leading zero(es)
                lastIsSeparator = false;
                lastIsDigit = true;
            } else if (c == '.' || c == '-') { // keep . and - separators
                if (lastIsSeparator) continue;

                lastIsSeparator = true;
                lastIsDigit = false;
            } else if ((c < 'A' || c > 'Z') && (c < 'a' || c > 'z')) { // replace remaining non-alphanumeric with .
                if (lastIsSeparator) continue;

                c = '.';
                lastIsSeparator = true;
                lastIsDigit = false;
            } else { // keep other characters (alpha)
                if (lastIsDigit) ret.append('.'); // no separator between number and non-number, add one

                lastIsSeparator = false;
                lastIsDigit = false;
            }

            ret.append(c);
        }

        // strip leading and trailing .

        int start = 0;
        while (start < ret.length() && ret.charAt(start) == '.') start++;

        int end = ret.length();
        while (end > start && ret.charAt(end - 1) == '.') end--;

        return ret.substring(start, end);
    }

    @Nullable
    private static String fromVersionJson(InputStream is) {
        try(JsonReader reader = new JsonReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String id = null;
            String name = null;
            String release = null;

            reader.beginObject();

            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "id":
                        if (reader.peek() != JsonToken.STRING) {
                            // FIXME: Needs its own type?
                            throw new ParseMetadataException("\"id\" in version json must be a string");
                        }

                        id = reader.nextString();
                        break;
                    case "name":
                        if (reader.peek() != JsonToken.STRING) {
                            // FIXME: Needs its own type?
                            throw new ParseMetadataException("\"name\" in version json must be a string");
                        }

                        name = reader.nextString();
                        break;
                    case "release_target":
                        if (reader.peek() != JsonToken.STRING) {
                            // FIXME: Needs its own type?
                            throw new ParseMetadataException("\"release_target\" in version json must be a string");
                        }

                        release = reader.nextString();
                        break;
                    default:
                        // There is typically other stuff in the file, just ignore anything we don't know
                        reader.skipValue();
                }
            }

            reader.endObject();

            if (name == null) {
                name = id;
            } else if (id != null) {
                if (id.length() < name.length()) name = id;
            }

            if (name != null && release != null) return normalizeVersion(name, release);
        } catch (IOException | ParseMetadataException e) {
            e.printStackTrace();
        }

        return null;
    }


    @Nullable
    public static String getVersion(Path gameJar) {
        String ret;

        // check various known files for version information

        try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(gameJar, false)) {
            FileSystem fs = jarFs.get();
            Path file;

            // version.json - contains version and target release for 18w47b+
            if (Files.isRegularFile(file = fs.getPath("version.json"))
                    && (ret = fromVersionJson(Files.newInputStream(file))) != null) {
                return ret;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
