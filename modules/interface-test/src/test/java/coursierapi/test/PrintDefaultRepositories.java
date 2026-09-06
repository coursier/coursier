package coursierapi.test;

import coursierapi.MavenRepository;
import coursierapi.Repository;

/** Prints the default repositories, one per line - used by {@link SystemPropertyTests} */
public final class PrintDefaultRepositories {

    public static void main(String[] args) {
        for (Repository repository : Repository.defaults()) {
            if (repository instanceof MavenRepository)
                System.out.println(((MavenRepository) repository).getBase());
            else
                System.out.println(repository);
        }
    }

}
