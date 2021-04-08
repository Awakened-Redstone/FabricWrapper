package org.jigsawlabs.fabricwrapper;

import org.jigsawlabs.fabricwrapper.notmycode.link.infra.jumploader.launch.PreLaunchDispatcher;
import org.jigsawlabs.fabricwrapper.notmycode.link.infra.jumploader.launch.classpath.ClasspathReplacer;
import org.jigsawlabs.fabricwrapper.notmycode.net.fabricmc.installer.server.ServerInstaller;
import org.jigsawlabs.fabricwrapper.notmycode.net.fabricmc.installer.util.MetaHandler;
import org.jigsawlabs.fabricwrapper.notmycode.net.fabricmc.installer.util.Reference;
import org.jigsawlabs.fabricwrapper.notmycode.net.fabricmc.loader.util.Arguments;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FabricWrapper {

    public static MetaHandler GAME_VERSION_META;
    public static MetaHandler LOADER_META;

    public static String gameVersion;
    public static String loaderVersion;

    public static final List<URL> loadUrls = new ArrayList<>();

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

    public static void main(String[] args) {
        GAME_VERSION_META = new MetaHandler(Reference.getMetaServerEndpoint("v2/versions/game"));
        LOADER_META = new MetaHandler(Reference.getMetaServerEndpoint("v2/versions/loader"));
        Arguments arguments = new Arguments();
        arguments.parse(args);
        gameVersion = arguments.get(Arguments.GAME_VERSION);
        if (gameVersion == null) {
            System.getProperty("fabric.gameVersion");
        }
        if (gameVersion == null) {
            try {
                File propertiesFile = new File("fabric-wrapper.properties");
                Properties properties = new Properties();

                if (propertiesFile.exists()) {
                    try (FileInputStream stream = new FileInputStream(propertiesFile)) {
                        properties.load(stream);
                    }
                }

                if (!properties.containsKey("gameVersion")) {
                    properties.put("gameVersion", "latest/stable");
                    try (FileOutputStream stream = new FileOutputStream(propertiesFile)) {
                        properties.store(stream, null);
                    }
                }
                gameVersion = parseVersion((String) properties.get("gameVersion"));
                try {
                    LOADER_META.load();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to load latest versions", e);
                }

                loaderVersion = LOADER_META.getLatestVersion(false).getVersion();
                ServerInstaller.install(loaderVersion, gameVersion);

                try {
                    ClasspathReplacer.replaceClasspath(loadUrls);
                } catch (URISyntaxException e) {
                    //ErrorMessages.showFatalMessage("Jumploader failed to load", "Failed to parse URL in replacement classpath: " + e.getClass().getTypeName() + ": " + e.getLocalizedMessage(), LOGGER);
                    throw new RuntimeException("Failed to parse URL in replacement classpath", e);
                }

                URLClassLoader newLoader = new URLClassLoader(loadUrls.toArray(new URL[0]), ClassLoader.getSystemClassLoader().getParent());
                Thread.currentThread().setContextClassLoader(newLoader);

                PreLaunchDispatcher.dispatch(newLoader);

            } catch (IOException e) {
                throw new RuntimeException("Failed to load \"fabric-wrapper.properties\"", e);
            }


        }
    }

    protected static String parseVersion(String genericVersion) {
        try {
            GAME_VERSION_META.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load latest versions", e);
        }

        String[] genericIdentifiers = genericVersion.split("/");
        if (genericIdentifiers.length == 2) {

            if (!genericIdentifiers[0].toLowerCase(Locale.ROOT).equals("latest")) {
                throw new RuntimeException("gameVersion does not follow the pattern ([$version] | latest/([stable|release]|[unstable|snapshot]))");
            }

            Matcher matcher = Pattern.compile("(snapshot|unstable)|(release|stable)").matcher(genericIdentifiers[1].toLowerCase(Locale.ROOT));
            if (matcher.matches()) {
                if (matcher.group(1) != null && !matcher.group(1).isEmpty()) {
                    return GAME_VERSION_META.getLatestVersion(true).getVersion();
                } else if (matcher.group(2) != null && !matcher.group(2).isEmpty()) {
                    return GAME_VERSION_META.getLatestVersion(false).getVersion();
                } else {
                    throw new RuntimeException("`gameVersion` does not follow the pattern ([$version] | latest/([stable|release]|[unstable|snapshot]))");
                }
            } else {
                throw new RuntimeException("`gameVersion` does not follow the pattern ([$version] | latest/([stable|release]|[unstable|snapshot]))");
            }
        } else if (genericIdentifiers.length == 1) {
            if (VERSION_PATTERN.matcher(genericIdentifiers[0]).matches()) {
                return null;
            } else {
                throw new RuntimeException("`gameVersion` does not follow the pattern ([$version] | latest/([stable|release]|[unstable|snapshot]))");
            }
        } else {
            throw new RuntimeException("`gameVersion` does not follow the pattern ([$version] | latest/([stable|release]|[unstable|snapshot]))");
        }
    }
}
