package org.jigsawlabs.fabricwrapper;

import org.jigsawlabs.fabricwrapper.notmycode.net.fabricmc.loader.launch.server.InjectingURLClassLoader;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;

public class WrapperPreLoad {
    private static final ClassLoader parentLoader = WrapperPreLoad.class.getClassLoader();

    public static void replaceLoader(URLClassLoader newLoader) {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe u = (Unsafe) theUnsafe.get(null);
            Class<MethodHandles.Lookup> lookupClass = MethodHandles.Lookup.class;
            MethodHandles.Lookup impl_lookup = (MethodHandles.Lookup) u.getObject(lookupClass, u.staticFieldOffset(lookupClass.getDeclaredField("IMPL_LOOKUP")));
            MethodHandle setter = impl_lookup.findStaticSetter(ClassLoader.class, "scl", ClassLoader.class);
            impl_lookup.findVirtual(ClassLoader.getSystemClassLoader().getClass(), "finalize", MethodType.methodType(void.class)).invoke(ClassLoader.getSystemClassLoader());
            setter.invokeWithArguments(newLoader);
            System.gc();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to replace system ClassLoader", e);
        }
    }

    public static void main(String[] args) {
        try {
            URLClassLoader newClassLoader = new InjectingURLClassLoader(new URL[] { WrapperPreLoad.class.getProtectionDomain().getCodeSource().getLocation() }, parentLoader);
            Thread.currentThread().setContextClassLoader(newClassLoader);
            replaceLoader(newClassLoader);
            launch("org.jigsawlabs.fabricwrapper.FabricWrapper", newClassLoader, args);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void launch(String mainClass, ClassLoader loader, String[] args) {
        try {
            Class<?> c = loader.loadClass(mainClass);
            c.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (Exception e) {
            throw new RuntimeException("An exception occurred when loading the wrapper!", e);
        }
    }
}
