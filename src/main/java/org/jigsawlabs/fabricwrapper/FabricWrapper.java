package org.jigsawlabs.fabricwrapper;

import org.jigsawlabs.fabricwrapper.fabricloader.server.InjectingURLClassLoader;
import org.jigsawlabs.fabricwrapper.fabricloader.util.Arguments;
import org.jigsawlabs.fabricwrapper.fabricloader.util.UrlConversionException;
import org.jigsawlabs.fabricwrapper.fabricloader.util.UrlUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

public class FabricWrapper {

    private static final ClassLoader parentLoader = FabricWrapper.class.getClassLoader();

    public static void main(String[] args) {
        Thread thread = new Thread(new ShutdownHook(), "Shutdown hook");
        Runtime.getRuntime().addShutdownHook(thread);
        try {
            setup(args);
        } catch (IOException | UrlConversionException exception) {
            System.err.println("Failed to setup. Unable to continue.");
            exception.printStackTrace();
            System.exit(-1);
        }
    }

    protected static void setup(String[] args) throws IOException, UrlConversionException {
        //Use fabric loader "fabric-server-launcher.properties"
        File propertiesFile = new File("fabric-server-launcher.properties");
        Properties properties = new Properties();

        if (propertiesFile.exists()) {
            try (FileInputStream stream = new FileInputStream(propertiesFile)) {
                properties.load(stream);
            }
        }

        if (!properties.containsKey("serverJar")) {
            properties.put("serverJar", "server.jar");
            try (FileOutputStream stream = new FileOutputStream(propertiesFile)) {
                properties.store(stream, null);
            }
        }

        File serverJar = new File((String) properties.get("serverJar"));

        if (!serverJar.exists()) {
            System.err.println("Could not find Minecraft server .JAR (" + properties.get("serverJar") + ")!");
            System.err.println();
            System.err.println("Fabric's server-side launcher expects the server .JAR to be provided.");
            System.err.println("You can edit its location in fabric-server-launcher.properties.");
            System.err.println();
            System.err.println("Without the official Minecraft server .JAR, Fabric Loader cannot launch.");
            throw new RuntimeException("Searched for '" + serverJar.getName() + "' but could not find it.");
        }

        System.setProperty("fabricWrapper.gameJarPath", serverJar.getAbsolutePath());

        try {
            URLClassLoader newClassLoader = new InjectingURLClassLoader(new URL[] { FabricWrapper.class.getProtectionDomain().getCodeSource().getLocation(), UrlUtil.asUrl(serverJar) }, parentLoader);
            Thread.currentThread().setContextClassLoader(newClassLoader);
            Class<?> c = newClassLoader.loadClass("org.jigsawlabs.fabricwrapper.FabricLoaderWrapper");
            c.getMethod("dummy", String[].class).invoke(null, (Object) args);
        } catch (Exception e) {
            throw new RuntimeException("An exception occurred when launching running the wrapper!", e);
        }
    }
}
