package coursierapi.error;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MultipleResolutionError extends ResolutionError {

    private final List<SimpleResolutionError> errors;

    private MultipleResolutionError(List<SimpleResolutionError> errors, String message) {
        super(message);
        this.errors = Collections.unmodifiableList(errors);
    }

    public static MultipleResolutionError of(SimpleResolutionError error, SimpleResolutionError... errors) {
        List<SimpleResolutionError> errors0 = new ArrayList<>();
        errors0.add(error);
        errors0.addAll(Arrays.asList(errors));

        StringBuilder b = new StringBuilder();
        for (SimpleResolutionError error0 : errors0) {
            b.append(error0.getMessage());
            b.append('\n');
        }

        return new MultipleResolutionError(errors0, b.toString());
    }

    public List<SimpleResolutionError> getErrors() {
        return errors;
    }
}
