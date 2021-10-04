package coursier.cli.internal;

// https://github.com/VirtusLab/scala-cli/blob/4ae17a428933c0edc118a065838c236c255f719c/modules/cli/src/main/java/scala/cli/internal/CsJniUtilsFeature.java

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@AutomaticFeature
@Platforms({Platform.WINDOWS.class})
public class CsJniUtilsFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("csjniutils");
        PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("coursier_bootstrap_launcher_jniutils_");
        PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("coursier_jniutils_");
        PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("coursierapi_internal_jniutils_");
        PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("lmcoursier_internal_jniutils_");
        NativeLibraries nativeLibraries = ((FeatureImpl.BeforeAnalysisAccessImpl) access).getNativeLibraries();
        nativeLibraries.addStaticJniLibrary("csjniutils");
        nativeLibraries.addDynamicNonJniLibrary("ole32");
        nativeLibraries.addDynamicNonJniLibrary("shell32");
        nativeLibraries.addDynamicNonJniLibrary("advapi32");
    }
}
