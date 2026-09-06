package coursierapi.error;

public abstract class SimpleResolutionError extends ResolutionError {

    SimpleResolutionError(String message) {
        super(message);
    }

    public static SimpleResolutionError of(String message) {
        // TODO Replace with implementations of SimpleResolutionError
        return new SimpleResolutionError(message) {
        };
    }
}
