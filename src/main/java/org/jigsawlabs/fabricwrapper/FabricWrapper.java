package org.jigsawlabs.fabricwrapper;

import com.google.gson.JsonObject;
import com.mojang.util.QueueLogAppender;
import com.mojang.util.UUIDTypeAdapter;
import net.minecrell.terminalconsole.util.LoggerNamePatternSelector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.jigsawlabs.fabricwrapper.notmycode.link.infra.jumploader.launch.PreLaunchDispatcher;
import org.jigsawlabs.fabricwrapper.notmycode.link.infra.jumploader.launch.classpath.ClasspathReplacer;
import org.jigsawlabs.fabricwrapper.notmycode.link.infra.jumploader.util.RequestUtils;
import org.jigsawlabs.fabricwrapper.notmycode.net.fabricmc.installer.server.ServerInstaller;
import org.jigsawlabs.fabricwrapper.notmycode.net.fabricmc.installer.util.*;
import org.jigsawlabs.fabricwrapper.notmycode.net.fabricmc.loader.launch.server.InjectingURLClassLoader;
import org.jigsawlabs.fabricwrapper.notmycode.net.fabricmc.loader.util.Arguments;
import sun.misc.Unsafe;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FabricWrapper {

    private static final Logger LOGGER = LogManager.getLogger("FabricWrapper");

    private static Properties properties = new Properties();

    public static MetaHandler GAME_VERSION_META;
    public static MetaHandler LOADER_META;

    public static String gameVersion;
    public static String loaderVersion;

    public static final List<URL> loadUrls = new ArrayList<>();

    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "0\\.\\d+(\\.\\d+)?a?(_\\d+)?|" // match classic versions first: 0.1.2a_34
                    + "\\d+\\.\\d+(\\.\\d+)?(-pre\\d+)?|" // modern non-snapshot: 1.2, 1.2.3, optional -preN or " Pre-Release N" suffix
                    + "\\d+\\.\\d+(\\.\\d+)?(-rc\\d+)?|" // 1.16+ Release Candidate
                    + "\\d+w\\d+[a-z]+|" // modern snapshot: 12w34a
                    + "[a-c]\\d\\.\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?|" // alpha/beta a1.2.3_45
                    + "(Alpha|Beta) v?\\d+\\.\\d+(\\.\\d+)?[a-z]?(_\\d+)?[a-z]?|" // long alpha/beta names: Alpha v1.2.3_45
                    + "Inf?dev (0\\.31 )?\\d+(-\\d+)?|" // long indev/infdev names: Infdev 12345678-9
                    + "(rd|inf)-\\d+|" // early rd-123, inf-123
                    + "1\\.RV-Pre1|3D Shareware v1\\.34" // odd exceptions
    );

    //Dummy code to make shadowJar minimize keep needed classes
    @SuppressWarnings("unused")
    private void dummy() {
        Class<?> dummy0 = LoggerNamePatternSelector.class;
        Class<?> dummy1 = QueueLogAppender.class;
        Class<?> dummy2 = UUIDTypeAdapter.class;
        Class<?> dummy3 = PropertiesUtil.class;
    }

    public static void disableWarning() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe u = (Unsafe) theUnsafe.get(null);

            //noinspection rawtypes
            Class cls = Class.forName("jdk.internal.module.IllegalAccessLogger");
            Field logger = cls.getDeclaredField("logger");
            u.putObjectVolatile(cls, u.staticFieldOffset(logger), null);
        } catch (Exception ignored) {
        }
    }

    public static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(out);
        os.writeObject(obj);
        return out.toByteArray();
    }

    public static void replaceLoader(URLClassLoader newLoader) {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe u = (Unsafe) theUnsafe.get(null);

            /*//noinspection rawtypes
            Class cls = Class.forName("java.lang.ClassLoader");
            Field scl = cls.getDeclaredField("scl");
            u.putObjectVolatile(cls, u.staticFieldOffset(scl), newLoader);
            Class<?> cls = Class.forName("java.lang.ClassLoader");
            Method getDeclaredFields0 = cls.getClass().getDeclaredMethod("getDeclaredFields0", boolean.class);*/
            Class<MethodHandles.Lookup> lookupClass = MethodHandles.Lookup.class;
            MethodHandles.Lookup impl_lookup = (MethodHandles.Lookup) u.getObject(lookupClass, u.staticFieldOffset(lookupClass.getDeclaredField("IMPL_LOOKUP")));
            MethodHandle setter = impl_lookup.findStaticSetter(ClassLoader.class, "scl", ClassLoader.class);
            //byte[] bytes = serialize(Gson.class);
            impl_lookup.findVirtual(ClassLoader.getSystemClassLoader().getClass(), "finalize", MethodType.methodType(void.class)).invoke(ClassLoader.getSystemClassLoader());
            setter.invokeWithArguments(newLoader);
            System.gc();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to replace system ClassLoader", e);
        }
    }

    public static void main(String[] args) throws Throwable {
        //disableWarning();
        LOGGER.debug("Loading data.");
        GAME_VERSION_META = new MetaHandler(Reference.getMetaServerEndpoint("v2/versions/game"));
        LOADER_META = new MetaHandler(Reference.getMetaServerEndpoint("v2/versions/loader"));
        Arguments arguments = new Arguments();
        arguments.parse(args);

        loadProperties();

        gameVersion = arguments.get(Arguments.GAME_VERSION);

        if (gameVersion == null) {
            gameVersion = System.getProperty("fabric.gameVersion");
        }

        if (gameVersion == null) {
            gameVersion = parseVersion(properties.getProperty("gameVersion"));
        }

        InstallerProgress.CONSOLE.updateProgress(Utils.BUNDLE.getString("progress.start.loader"));
        try {
            LOADER_META.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Fabric Loader meta.", e);
        }
        loaderVersion = LOADER_META.getLatestVersion(false).getVersion();
        File file = new File(".");
        if (!file.exists()) {
            throw new FileNotFoundException("Server directory not found at " + file.getAbsolutePath());
        }

        try {
            ServerInstaller.install(file.getAbsoluteFile(), loaderVersion, gameVersion, InstallerProgress.CONSOLE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to install Fabric Loader", e);
        }
        try {
            File serverJar = new File(file, "server.jar");
            File serverJarTmp = new File(file, "server.jar.tmp");
            Files.deleteIfExists(serverJar.toPath());
            InstallerProgress.CONSOLE.updateProgress(Utils.BUNDLE.getString("progress.download.minecraft"));
            Utils.downloadFile(new URL(LauncherMeta.getLauncherMeta().getVersion(gameVersion).getVersionMeta().downloads.get("server").url), serverJarTmp.toPath());
            Files.move(serverJarTmp.toPath(), serverJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            InstallerProgress.CONSOLE.updateProgress(Utils.BUNDLE.getString("progress.done.server"));
            InstallerProgress.CONSOLE.updateProgress("Preparing for server start");
            loadUrls.add(serverJar.toURI().toURL());
        } catch (IOException e) {
            throw new RuntimeException("Failed to install Minecraft Server", e);
        }

        if (Boolean.parseBoolean(properties.getProperty("replaceVanillaLogger"))) {
            LOGGER.debug("Adding custom log4j2 to classpath.");
            try (InputStream in = FabricWrapper.class.getClassLoader().getResourceAsStream("log4j2.xml")) {
                File libsDir = new File(".", ".cache" + File.separator + "fabric-wrapper" + File.separator + "libraries");
                File file0 = new File(libsDir, "log4j2.xml");
                //noinspection ConstantConditions
                Files.copy(in, file0.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.setProperty("log4j.configurationFile", file0.getAbsolutePath());
                Configurator.initialize("configurationFile", file0.getAbsolutePath());
                loadUrls.add(file0.toURI().toURL());
            } catch (IOException exception) {
                LOGGER.error("Failed to replace vanilla logger, loading will continue without replacing the logger", exception);
            }
        }

        loadUrls.add(FabricWrapper.class.getProtectionDomain().getCodeSource().getLocation());

        try {
            ClasspathReplacer.replaceClasspath(loadUrls);
        } catch (URISyntaxException e) {
            LOGGER.error("Failed to parse URL in replacement classpath: " + e.getClass().getTypeName() + ": " + e.getLocalizedMessage());
            throw new RuntimeException("Failed to parse URL in replacement classpath", e);
        }

        URLClassLoader newLoader = new InjectingURLClassLoader(loadUrls.toArray(new URL[]{}), FabricWrapper.class.getClassLoader());
        replaceLoader(newLoader);
        Thread.currentThread().setContextClassLoader(newLoader);

        PreLaunchDispatcher.dispatch(newLoader);

        String loaderJsonPath = String.format("v2/versions/loader/%s/%s", gameVersion, loaderVersion);
        String loaderJsonUrl = Reference.getMetaServerEndpoint(loaderJsonPath);
        JsonObject loaderJson = RequestUtils.getJson(loaderJsonUrl).getAsJsonObject();
        String mainClassPath = loaderJson.getAsJsonObject("launcherMeta").getAsJsonObject("mainClass").get("server").getAsString();

        int preLaunchRunningThreads = Thread.currentThread().getThreadGroup().activeCount();

        LOGGER.info("Starting Fabric server.");
        try {
            Class<?> mainClass = newLoader.loadClass(mainClassPath);
            Method main = mainClass.getMethod("main", String[].class);
            main.invoke(null, (Object) arguments.toArray());
        } catch (NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException(String.format("Failed to load server. Got an exception when trying to invoke {Class: \"%s\", Method: \"main(String[] args)\", Arguments: \"%s\"}", mainClassPath, arguments.toList()), e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getTargetException());
        }

        if (Thread.currentThread().getThreadGroup().activeCount() - preLaunchRunningThreads <= 0) {
            LOGGER.warn("Minecraft shouldn't return from invoke() without spawning threads, loading is likely to have failed!");
            System.exit(0);
        }
    }

    private static void loadProperties() {
        Map<String, String> defaultProperties = new HashMap<>();
        defaultProperties.put("gameVersion", "latest/stable");
        defaultProperties.put("replaceVanillaLogger", "false");

        try {
            File propertiesFile = new File("fabric-wrapper.properties");
            if (propertiesFile.exists()) {
                try (FileInputStream stream = new FileInputStream(propertiesFile)) {
                    properties.load(stream);
                }
            }

            for (Map.Entry<String, String> entry : defaultProperties.entrySet()) {
                if (!properties.containsKey(entry.getKey())) {
                    properties.put(entry.getKey(), entry.getValue());
                    try (FileOutputStream stream = new FileOutputStream(propertiesFile)) {
                        properties.store(stream, null);
                    }
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to load \"fabric-wrapper.properties\"", e);
        }
    }

    protected static String parseVersion(String genericVersion) {
        String[] genericIdentifiers = genericVersion.split("/");
        if (genericIdentifiers.length == 2) {

            if (!genericIdentifiers[0].toLowerCase(Locale.ROOT).equals("latest")) {
                throw new RuntimeException("gameVersion does not follow the pattern ([$version] | latest/([stable|release]|[unstable|snapshot]))");
            }

            Matcher matcher = Pattern.compile("(snapshot|unstable)|(release|stable)").matcher(genericIdentifiers[1].toLowerCase(Locale.ROOT));
            if (matcher.matches()) {
                try {
                    GAME_VERSION_META.load();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to load latest versions", e);
                }
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
                return genericIdentifiers[0];
            } else {
                throw new RuntimeException("`gameVersion` does not follow the pattern ([$version] | latest/([stable|release]|[unstable|snapshot]))");
            }
        } else {
            throw new RuntimeException("`gameVersion` does not follow the pattern ([$version] | latest/([stable|release]|[unstable|snapshot]))");
        }
    }
}
