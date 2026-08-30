package coursierapi;

import coursier.internal.api.ApiHelper;
import coursierapi.error.RepositoryParsingError;

import java.util.List;

public final class RepositoryParser {

    private RepositoryParser() {}

    public static Repository repository(String input) {
        return ApiHelper.parseRepository(input);
    }

    public static List<Repository> repositories(List<String> inputs) throws RepositoryParsingError {
        return ApiHelper.parseRepositories(inputs);
    }
}
