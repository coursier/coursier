package coursierapi.error;

public abstract class SimpleRepositoryParsingError extends CoursierError {

    SimpleRepositoryParsingError(String message) {
        super(message);
    }

    public static SimpleRepositoryParsingError of(String message) {
        return new SimpleRepositoryParsingError(message) {
        };
    }
}
