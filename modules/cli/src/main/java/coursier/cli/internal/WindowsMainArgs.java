package coursier.cli.internal;

import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import coursier.jniutils.WindowsCodePages;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

/**
 * Reads the arguments of a Windows native image back from the C {@code char**} they came in as,
 * with the code page of the machine running it.
 *
 * <p>An image is handed its arguments as bytes that Windows encoded in the process' ANSI code
 * page, and turns them into strings with {@code Charset.defaultCharset()} (see
 * {@code SubstrateUtil.convertCToJavaArgs}). GraalVM freezes that charset into the image at build
 * time, taking it from the machine that built the image - so as soon as the two disagree,
 * anything but ASCII comes out wrong: UTF-8 baked in against a Windows-1252 machine turns every
 * accented character into U+FFFD, and an image built on a Windows-1252 machine mangles arguments
 * on a UTF-8-enabled one. Asking Windows for the code page at run time and decoding the same
 * bytes again with it gets those characters back.
 *
 * <p>Characters that the ANSI code page cannot represent at all are already gone by then -
 * Windows writes a {@code ?} in their place when it builds the {@code char**}, and only the
 * UTF-16 command line still has them.
 */
@Platforms(Platform.WINDOWS.class)
@CContext(WindowsMainArgs.Directives.class)
final class WindowsMainArgs {

    static final class Directives implements CContext.Directives {
        @Override
        public boolean isInConfiguration() {
            return Platform.includedIn(Platform.WINDOWS.class);
        }

        @Override
        public List<String> getHeaderFiles() {
            return Collections.singletonList("<windows.h>");
        }
    }

    /** The ANSI code page of this process - the one Windows encoded its arguments with. */
    @CFunction("GetACP")
    static native int GetACP();

    static String[] get(String[] args) {
        try {
            return decoded(args);
        }
        catch (Throwable t) {
            if (Boolean.getBoolean("coursier.windows-args.throw-exception"))
                throw t;
            if (Boolean.getBoolean("coursier.windows-args.verbose"))
                System.err.println("Error reading the command line arguments back: " + t);
            return args;
        }
    }

    private static String[] decoded(String[] args) {
        if (args.length == 0)
            return args;

        Charset charset = WindowsCodePages.charsetFor(GetACP());
        if (charset == null || charset.equals(Charset.defaultCharset()))
            // either this image has no charset for that code page, or it already read the
            // arguments with it
            return args;

        CEntryPointCreateIsolateParameters parameters = JavaMainWrapper.MAIN_ISOLATE_PARAMETERS.get();
        if (parameters.isNull())
            return args;
        CCharPointerPointer argv = parameters.getArgv();
        int argc = parameters.getArgc();
        // argv[0] is the executable itself, the rest is what main was handed - if that doesn't
        // line up, these are not the bytes these arguments came from, so leave them alone
        if (argv.isNull() || argc - 1 != args.length)
            return args;

        String[] decoded = new String[args.length];
        for (int i = 0; i < decoded.length; i++) {
            CCharPointer arg = argv.read(i + 1);
            decoded[i] = CTypeConversion.toJavaString(arg, SubstrateUtil.strlen(arg), charset);
        }
        return decoded;
    }

}
