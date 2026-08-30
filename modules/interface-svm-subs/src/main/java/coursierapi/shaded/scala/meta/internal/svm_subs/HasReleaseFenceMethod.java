// from https://github.com/scalameta/svm-subs/tree/792c0f6cfc0726779c3d45f54031094f54519c7d
package coursierapi.shaded.scala.meta.internal.svm_subs;

import java.util.function.Predicate;

final class HasReleaseFenceMethod implements Predicate<String> {
    @Override
    public boolean test(String className) {
        try {
            final Class<?> classForName = Class.forName(className);
            classForName.getMethod("releaseFence");
            return true;
        } catch (Exception cnfe) {
            return false;
        }
    }
}
