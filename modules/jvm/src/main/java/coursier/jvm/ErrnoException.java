package coursier.jvm;

import com.oracle.svm.core.posix.headers.Errno;
import org.graalvm.nativeimage.c.type.CTypeConversion;

public class ErrnoException extends Exception {

    private final int errno0;

    public ErrnoException(int errno) {
        this(errno, null);
    }

    public ErrnoException(int errno, Throwable cause) {
        super("Errno " + CTypeConversion.toJavaString(Errno.strerror(errno)) + " (" + errno + ")", cause);
        errno0 = errno;
    }

    public int value() {
        return errno0;
    }

}
