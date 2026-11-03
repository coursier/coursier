package coursier.exec;

/**
 * Thrown when a system call fails, such as {@link Execve#execve(String, String[], String[])}
 * or {@link Chdir#chdir(String)}.
 *
 * <p>The message describes the error, the way {@code strerror(3)} does, followed by the errno
 * value, such as {@code "chdir /foo: No such file or directory (errno 2)"}. When the error
 * means a path passed to the system call doesn't exist ({@code ENOENT} or {@code ENOTDIR}),
 * the cause of the exception is a {@link java.io.FileNotFoundException} with that path as
 * message.
 */
public class ErrnoException extends Exception {

    /** The errno value, see {@link #value()} */
    private final int errno0;

    /**
     * Creates an exception for an errno value
     *
     * @param errno the value of {@code errno} after the failed system call
     */
    public ErrnoException(int errno) {
        this(errno, null);
    }

    /**
     * Creates an exception for an errno value
     *
     * @param errno the value of {@code errno} after the failed system call
     * @param cause the cause of the exception, can be null
     */
    public ErrnoException(int errno, Throwable cause) {
        this(null, errno, cause);
    }

    /**
     * Creates an exception for an errno value, with a description of what failed
     *
     * @param operation what failed, such as {@code "execve /foo/bar"}, prepended to the message
     *                  if it's not null
     * @param errno     the value of {@code errno} after the failed system call
     * @param cause     the cause of the exception, can be null
     */
    public ErrnoException(String operation, int errno, Throwable cause) {
        super(message(operation, errno), cause);
        errno0 = errno;
    }

    /**
     * The errno value of this exception
     *
     * <p>Errno values depend on the OS. Compare them to the constants of the OS (from
     * {@code errno.h}) rather than to hard-coded values when possible.
     *
     * @return the value of {@code errno} after the failed system call
     */
    public int value() {
        return errno0;
    }

    private static String message(String operation, int errno) {
        String description = Libc.strerror(errno);
        String error = description == null
            ? "errno " + errno
            : description + " (errno " + errno + ")";
        return operation == null ? error : operation + ": " + error;
    }

}
