package coursier.cli.internal;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Reads the arguments of a Windows native image back from the command line Windows kept for the
 * process.
 *
 * <p>An image is handed its arguments as bytes that Windows encoded in the process' ANSI code
 * page, and turns them into strings with the charset frozen into it at build time (see
 * {@code SubstrateUtil.convertCToJavaArgs}, which goes through {@code new String(byte[])}). As
 * soon as those two disagree, anything but ASCII comes out wrong: UTF-8 baked in against a
 * Windows-1252 machine turns every accented character into U+FFFD, and an image built on a
 * Windows-1252 machine turns them into mojibake on a UTF-8-enabled one. Characters that the ANSI
 * code page cannot represent at all never make it into those bytes either - Windows writes a
 * {@code ?} in their place.
 *
 * <p>The command line Windows holds on to is UTF-16 and has all of them, whichever code pages are
 * involved, so that is what gets split again here - with the very function the C runtime splits it
 * with, so that the arguments come out the way the image would have gotten them.
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
            return Arrays.asList("<windows.h>", "<shellapi.h>");
        }
    }

    /** The UTF-16 command line of this process, as it was passed to it. */
    @CFunction("GetCommandLineW")
    static native CCharPointer GetCommandLineW();

    /**
     * Splits a UTF-16 command line into an argument vector, the way the C runtime does.
     *
     * <p>From shell32, which images already link for jni-utils - see {@code CsJniUtilsFeature}.
     */
    @CFunction("CommandLineToArgvW")
    static native CCharPointerPointer CommandLineToArgvW(CCharPointer commandLine, CIntPointer argcOut);

    @CFunction("LocalFree")
    static native PointerBase LocalFree(PointerBase mem);

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

        CCharPointer commandLine = GetCommandLineW();
        if (commandLine.isNull())
            return args;

        CIntPointer argcOut = StackValue.get(CIntPointer.class);
        argcOut.write(0);
        CCharPointerPointer argv = CommandLineToArgvW(commandLine, argcOut);
        if (argv.isNull())
            return args;

        try {
            // argv[0] is the executable itself, the rest is what main was handed - if that doesn't
            // line up, the command line did not get split the way this image's arguments did (the
            // C runtime expanded a wildcard, say), so leave them alone
            int argc = argcOut.read();
            if (argc - 1 != args.length)
                return args;

            String[] decoded = new String[args.length];
            for (int i = 0; i < decoded.length; i++) {
                String fromCommandLine = wideToJavaString(argv.read(i + 1));
                // An all-ASCII argument came through the code pages intact, so a difference there
                // is the C runtime's doing (again, an expanded wildcard) rather than ours to undo
                boolean intact = isAscii(args[i]) && isAscii(fromCommandLine);
                decoded[i] = intact ? args[i] : fromCommandLine;
            }
            return decoded;
        }
        finally {
            LocalFree(argv);
        }
    }

    /** Reads a NUL-terminated UTF-16 string off native memory. */
    private static String wideToJavaString(CCharPointer wide) {
        Pointer pointer = (Pointer) wide;
        int length = 0;
        while (pointer.readShort(length) != 0)
            length += 2;
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++)
            bytes[i] = pointer.readByte(i);
        return new String(bytes, StandardCharsets.UTF_16LE);
    }

    private static boolean isAscii(String str) {
        for (int i = 0; i < str.length(); i++)
            if (str.charAt(i) > 0x7f)
                return false;
        return true;
    }

}
