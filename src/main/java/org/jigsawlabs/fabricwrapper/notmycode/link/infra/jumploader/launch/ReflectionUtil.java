package org.jigsawlabs.fabricwrapper.notmycode.link.infra.jumploader.launch;

import sun.misc.Unsafe;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Function;

public class ReflectionUtil {
    private static MethodHandles.Lookup lookup = MethodHandles.lookup();

    public static Unsafe getUnsafe() throws NoSuchFieldException, IllegalAccessException {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }

    @SuppressWarnings("unchecked")
    public static <T> T reflectField(Object destObj, String name) throws NoSuchFieldException, IllegalAccessException, ClassCastException {
        Field field = destObj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(destObj);
    }

    @SuppressWarnings("unchecked")
    public static <T> T reflectStaticField(Class<?> destClass, String name) throws Throwable {
        Unsafe unsafe = getUnsafe();
        //return (T) new MethodHandles.Lookup(Object.class, null, -1).findStaticGetter(destClass, name, Object.class).invokeWithArguments();
        return (T) unsafe.getObject(destClass, unsafe.staticFieldOffset(destClass.getDeclaredField(name)));

//        Field field = destClass.getDeclaredField(name);
//        field.setAccessible(true);
//        return (T) field.get(null);
    }

    @SuppressWarnings({"unchecked", "UnusedReturnValue"})
    public static <T> T transformStaticField(Class<?> destClass, String name, Function<T, T> transformer) throws NoSuchFieldException, IllegalAccessException, ClassCastException {
        Unsafe unsafe = getUnsafe();
        /*Field field = destClass.getDeclaredField(name);
        field.setAccessible(true);*/
        Object object = unsafe.getObject(destClass, unsafe.staticFieldOffset(destClass.getDeclaredField(name)));
        //T value = transformer.apply((T) field.get(null));
        T value = transformer.apply((T) object);
        unsafe.getAndSetObject(destClass, unsafe.staticFieldOffset(destClass.getDeclaredField(name)), value);
        /*field.set(null, value);*/
        return value;
    }
}
