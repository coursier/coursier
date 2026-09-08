package coursierapi.error;

import java.util.HashMap;
import java.util.Map;

public class DownloadingArtifactsError extends FetchError {

    private final Map<String, String> errors;

    private DownloadingArtifactsError(Map<String, String> errors, String message) {
        super(message);
        this.errors = new HashMap<>(errors);
    }

    public static DownloadingArtifactsError of(Map<String, String> errors) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> entry : errors.entrySet()) {
            b.append(entry.getKey());
            b.append(": ");
            b.append(entry.getValue());
            b.append('\n');
        }
        return new DownloadingArtifactsError(errors, b.toString());
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
