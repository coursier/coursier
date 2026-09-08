package coursierapi.error;

public abstract class CoursierError extends Exception {

    CoursierError(String message) {
        super(message);
    }

    public static CoursierError of(String message) {
        return new SimpleCoursierError(message);
    }
}
