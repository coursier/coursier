package coursier.jvm;

@Deprecated
public class ErrnoException extends Exception {

    private final int errno0;

    public ErrnoException(int errno) {
        this(errno, null);
    }

    public ErrnoException(int errno, Throwable cause) {
        super(new coursier.exec.ErrnoException(errno).getMessage(), cause);
        errno0 = errno;
    }

    public int value() {
        return errno0;
    }

}
