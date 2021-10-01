/*
 * Copyright (c) 2021  comp500
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.jigsawlabs.fabricwrapper.jumploader.launch;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
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
