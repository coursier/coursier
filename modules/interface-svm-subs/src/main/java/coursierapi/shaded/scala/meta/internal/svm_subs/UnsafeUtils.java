// from https://github.com/scalameta/svm-subs/tree/792c0f6cfc0726779c3d45f54031094f54519c7d
package coursierapi.shaded.scala.meta.internal.svm_subs;

import java.lang.reflect.Field;

class UnsafeUtils {
    static final sun.misc.Unsafe UNSAFE;
    static {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) field.get(null);
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
