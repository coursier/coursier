package coursierapi.error;

public abstract class ResolutionError extends CoursierError {

    ResolutionError(String message) {
        super(message);
    }

    public static ResolutionError of(String message) {
        return new ResolutionError(message) {
        };
    }
}
