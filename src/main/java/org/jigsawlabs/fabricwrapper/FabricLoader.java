package org.jigsawlabs.fabricwrapper;

import org.jigsawlabs.fabricwrapper.fabricloader.server.InjectingURLClassLoader;
import org.jigsawlabs.fabricwrapper.fabricloader.util.Arguments;
import org.jigsawlabs.fabricwrapper.fabricloader.util.UrlConversionException;
import org.jigsawlabs.fabricwrapper.fabricloader.util.UrlUtil;
import org.jigsawlabs.fabricwrapper.jumploader.launch.PreLaunchDispatcher;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class FabricLoader {

    private static final ClassLoader parentLoader = FabricLoader.class.getClassLoader();

    public static void dummy(Arguments args, File loaderFile) {
        new FabricLoader().runLoader(args, loaderFile);
    }

    public void runLoader(Arguments args, File loaderFile) {
        try {
            URLClassLoader newClassLoader = new InjectingURLClassLoader(new URL[]{FabricLoader.class.getProtectionDomain().getCodeSource().getLocation(), UrlUtil.asUrl(loaderFile)}, parentLoader);
            Thread.currentThread().setContextClassLoader(newClassLoader);

            PreLaunchDispatcher.dispatch(newClassLoader);

            try {
                Class<?> c = newClassLoader.loadClass("net.fabricmc.loader.launch.server.FabricServerLauncher");
                Method m = c.getMethod("main", String[].class);
                m.invoke(null, (Object) args.toArray());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (UrlConversionException e) {
            throw new RuntimeException(e);
        }
    }
}
