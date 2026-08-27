package coursierapi.test;

import coursierapi.*;
import coursierapi.error.CoursierError;
import org.junit.Test;
import static org.junit.Assert.*;

import java.time.LocalDateTime;
import java.util.*;

public class VersionsTests {

    private List<String> almondSpark211_versions = Arrays.asList(
            "0.1.0", "0.1.1", "0.1.2", "0.1.3",
            "0.2.0", "0.3.0",
            "0.4.0", "0.4.1", "0.4.2",
            "0.5.0");

    @Test
    public void simple() {

        Versions versions = Versions.create()
                .withModule(coursierapi.Module.of("sh.almond", "ammonite-spark_2.11"));

        VersionListing res;
        try {
            res = versions.versions().getMergedListings();
        } catch (CoursierError e) {
            throw new RuntimeException(e);
        }

        VersionListing expectedMergedListing = VersionListing.of(
                "0.5.0", "0.5.0",
                almondSpark211_versions,
                LocalDateTime.parse("2019-07-12T08:58:24"));

        assertEquals(expectedMergedListing, res);

    }

}
