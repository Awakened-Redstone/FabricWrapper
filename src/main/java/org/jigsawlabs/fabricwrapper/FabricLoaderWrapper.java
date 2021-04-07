package org.jigsawlabs.fabricwrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jigsawlabs.fabricwrapper.fabricinstaller.util.Utils;
import org.jigsawlabs.fabricwrapper.fabricloader.game.GameProviderHelper;
import org.jigsawlabs.fabricwrapper.fabricloader.minecraft.McVersionLookup;
import org.jigsawlabs.fabricwrapper.fabricloader.util.Arguments;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class FabricLoaderWrapper {

    private static final ClassLoader parentLoader = FabricLoader.class.getClassLoader();
    private static final Logger LOGGER = LogManager.getFormatterLogger("FabricLoaderWrapper");

    private Properties properties;
    private File propertiesFile;
    private Arguments arguments;
    private Path gameJar;

    protected static int retries = 0;

    protected static boolean badMappings = false;
    protected static boolean triedReinstall = false;
    protected static boolean triedReinstallWithNewInstaller = false;

    public static void dummy(String[] args) throws IOException {
        new FabricLoaderWrapper().setup(args);
    }

    private void prep() {
        try {
            this.propertiesFile = new File("fabric-loader-wrapper.properties");
            Properties properties = new Properties();

            if (propertiesFile.exists()) {
                try (FileInputStream stream = new FileInputStream(propertiesFile)) {
                    properties.load(stream);
                }
            }

            this.properties = properties;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load properties file!", e);
        }
    }

    private static void launch(String mainClass, ClassLoader loader, Arguments args, File loaderFile) throws Exception {
            Class<?> c = loader.loadClass(mainClass);
            c.getMethod("dummy", Arguments.class, File.class).invoke(null, args, loaderFile);
    }

    private void setup(String[] args) throws IOException {
        prep();
        this.arguments = new Arguments();
        arguments.parse(args);
        analyzeMappings();
        runLoader();
    }

    protected void runLoader() throws IOException {
        int preLaunchRunningThreads = Thread.currentThread().getThreadGroup().activeCount();

        try {
            new FabricLoader().runLoader(arguments, getLoader());
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (Thread.currentThread().getThreadGroup().activeCount() - preLaunchRunningThreads <= 0) {
            LOGGER.warn("Minecraft shouldn't return from invoke() without spawning threads, loading is likely to have failed! Retrying.");
            retry();
        } else {
            System.exit(0);
        }
    }

    protected void retry() throws IOException {
        if (retries > 5) throw new RuntimeException("Failed to load after 5 retries.");
        retries += 1;
        analyzeMappings();
        runLoader();
    }

    protected void analyzeMappings() {
        try {
            String gameVersion = getGameVersion(this.getClass().getClassLoader());

            if (!Files.exists(gameJar)) {
                throw new RuntimeException("Could not locate Minecraft: " + gameJar + " not found");
            }

            Path gameDir = getLaunchDirectory();

            Path deobfJarDir = gameDir.resolve(".fabric").resolve("remappedJars");

            if (!Files.exists(deobfJarDir)) {
                return;
            }

            String versionedId = gameVersion.isEmpty() ? "minecraft" : String.format("%s-%s", "minecraft", gameVersion);
            deobfJarDir = deobfJarDir.resolve(versionedId);

            String deobfJarFilename = "intermediary" + "-" + gameJar.getFileName();
            Path deobfJarFile = deobfJarDir.resolve(deobfJarFilename);
            Path deobfJarFileTmp = deobfJarDir.resolve(deobfJarFilename + ".tmp");

            if (Files.exists(deobfJarFileTmp)) {
                if (!badMappings) {
                    LOGGER.warn("Incomplete remapped file found!");
                    badMappings = true;
                    Files.deleteIfExists(deobfJarFile);
                    Files.deleteIfExists(deobfJarFileTmp);
                } else if (!triedReinstall) {
                    LOGGER.warn("Incomplete remapped file found!");
                    Files.deleteIfExists(deobfJarFile);
                    Files.deleteIfExists(deobfJarFileTmp);
                    triedReinstall = true;
                    File installer = getInstaller();

                    if (!installer.exists()) {
                        LOGGER.warn("Could not find installer .JAR (" + properties.get("installerJar") + ")!");
                        LOGGER.info("Trying to download new installer.");
                        triedReinstallWithNewInstaller = true;

                        File installerTmp = new File(installer.getAbsolutePath() + ".tmp");
                        Utils.downloadFile(new URL(getInstallerDownloadUrl()), installerTmp.toPath());
                        Files.move(installerTmp.toPath(), installer.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }

                    try {
                        Class<?> c = FabricLoaderWrapper.class.getClassLoader().loadClass("net.fabricmc.installer.Main");
                        Method m = c.getMethod("main", String[].class);
                        m.invoke(null, new Object[]{new String[]{"server", "-snapshot", "-downloadMinecraft"}});
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else if (!triedReinstallWithNewInstaller) {
                    LOGGER.warn("Incomplete remapped file found!");
                    Files.deleteIfExists(deobfJarFile);
                    Files.deleteIfExists(deobfJarFileTmp);
                    File installer = getInstaller();
                    triedReinstallWithNewInstaller = true;
                    File installerTmp = new File(installer.getAbsolutePath() + ".tmp");
                    Utils.downloadFile(new URL(getInstallerDownloadUrl()), installerTmp.toPath());
                    Files.move(installerTmp.toPath(), installer.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    try {
                        Class<?> c = FabricLoaderWrapper.class.getClassLoader().loadClass("net.fabricmc.installer.Main");
                        Method m = c.getMethod("main", String[].class);
                        m.invoke(null, new Object[]{new String[]{"server", "-snapshot", "-downloadMinecraft"}});
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    LOGGER.error("Incomplete remapped file found! Failed to fix Fabric Loader.");
                    throw new RuntimeException("Unable to start Fabric Loader.");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getInstallerDownloadUrl() throws IOException {
        URL url = new URL("https://meta.fabricmc.net/v2/versions/installer");
        String str = Utils.readTextFile(url);
        JsonParser parser = new JsonParser();
        JsonElement json = parser.parse(str);
        return json.getAsJsonArray().get(0).getAsJsonObject().get("url").getAsString();
    }

    private File getInstaller() throws IOException {
        if (!properties.containsKey("installerJar")) {
            properties.put("installerJar", "fabric-installer.jar");
            try (FileOutputStream stream = new FileOutputStream(propertiesFile)) {
                properties.store(stream, null);
            }
        }

        return new File((String) properties.get("installerJar"));
    }

    private File getLoader() throws IOException {
        if (!properties.containsKey("loaderJar")) {
            properties.put("loaderJar", "fabric-server-launch.jar");
            try (FileOutputStream stream = new FileOutputStream(propertiesFile)) {
                properties.store(stream, null);
            }
        }

        return new File((String) properties.get("loaderJar"));
    }

    public Path getLaunchDirectory() {
        if (arguments == null) {
            return new File(".").toPath();
        }

        return getLaunchDirectory(arguments).toPath();
    }

    public static File getLaunchDirectory(Arguments argMap) {
        return new File(argMap.getOrDefault("gameDir", "."));
    }

    protected String getGameVersion(ClassLoader loader) {

        List<String> entrypointClasses;
        entrypointClasses = Arrays.asList("net.minecraft.server.Main", "net.minecraft.server.MinecraftServer", "com.mojang.minecraft.server.MinecraftServer");

        Optional<GameProviderHelper.EntrypointResult> entrypointResult = GameProviderHelper.findFirstClass(loader, entrypointClasses);

        if (!entrypointResult.isPresent()) {
            return null;
        }

        gameJar = entrypointResult.get().entrypointPath;

        return McVersionLookup.getVersion(gameJar);
    }
}
