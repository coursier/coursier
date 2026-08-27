// from https://github.com/scalameta/svm-subs/tree/792c0f6cfc0726779c3d45f54031094f54519c7d
package coursierapi.shaded.scala.meta.internal.svm_subs;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "coursierapi.shaded.scala.collection.immutable.VM", onlyWith = HasReleaseFenceMethod.class)
public final class Target_scala_collection_immutable_VM {

    @Substitute
    public static void releaseFence() {
        UnsafeUtils.UNSAFE.storeFence();
    }
}
