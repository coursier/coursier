package coursierapi.error;

public abstract class FetchError extends CoursierError {
    FetchError(String message) {
        super(message);
    }

    public static FetchError of(String message) {
        return new FetchError(message) {
        };
    }
}
