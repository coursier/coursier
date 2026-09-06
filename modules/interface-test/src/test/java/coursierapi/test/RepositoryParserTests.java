package coursierapi.test;

import coursierapi.Repository;
import coursierapi.RepositoryParser;
import coursierapi.error.RepositoryParsingError;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class RepositoryParserTests {

    @Test
    public void single() {
        Repository repo = RepositoryParser.repository("central");
        assertNotNull(repo);
    }

    @Test(expected = IllegalArgumentException.class)
    public void singleInvalid() {
        RepositoryParser.repository("ivy:[unclosed");
    }

    @Test
    public void batch() throws RepositoryParsingError {
        List<Repository> repos = RepositoryParser.repositories(Arrays.asList("central", "ivy2Local"));
        assertEquals(2, repos.size());
    }

    @Test
    public void batchInvalidIsCatchable() {
        RepositoryParsingError e = assertThrows(RepositoryParsingError.class, () ->
                RepositoryParser.repositories(Arrays.asList("ivy:[unclosed", "ivy:[alsoUnclosed")));
        assertEquals(2, e.getErrors().size());
    }
}
