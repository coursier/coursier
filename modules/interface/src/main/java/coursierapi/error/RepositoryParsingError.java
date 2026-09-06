package coursierapi.error;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RepositoryParsingError extends CoursierError {

    private final List<SimpleRepositoryParsingError> errors;

    private RepositoryParsingError(List<SimpleRepositoryParsingError> errors, String message) {
        super(message);
        this.errors = Collections.unmodifiableList(errors);
    }

    public static RepositoryParsingError of(SimpleRepositoryParsingError error, SimpleRepositoryParsingError... errors) {
        List<SimpleRepositoryParsingError> errorsList = new ArrayList<>();
        errorsList.add(error);
        errorsList.addAll(Arrays.asList(errors));

        StringBuilder b = new StringBuilder();
        for (SimpleRepositoryParsingError errorElement : errorsList) {
            b.append(errorElement.getMessage());
            b.append('\n');
        }

        return new RepositoryParsingError(errorsList, b.toString());
    }

    public List<SimpleRepositoryParsingError> getErrors() {
        return errors;
    }
}
