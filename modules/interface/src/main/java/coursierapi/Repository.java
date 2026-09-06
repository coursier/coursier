package coursierapi;

import coursier.internal.api.ApiHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public interface Repository {

    static List<Repository> defaults() {
        return Collections.unmodifiableList(Arrays.asList(ApiHelper.defaultRepositories()));
    }

    static MavenRepository central() {
        return ApiHelper.central();
    }

    static IvyRepository ivy2Local() {
        return ApiHelper.ivy2Local();
    }
}
