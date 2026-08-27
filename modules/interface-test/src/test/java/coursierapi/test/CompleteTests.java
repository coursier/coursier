package coursierapi.test;

import coursierapi.Complete;
import coursierapi.CompleteResult;
import coursierapi.error.CoursierError;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

public class CompleteTests {

    private List<String> shapeless211_22_versions = Arrays.asList("2.2.0-RC1", "2.2.0-RC2", "2.2.0-RC3", "2.2.0-RC4", "2.2.0-RC5", "2.2.0-RC6", "2.2.0", "2.2.1", "2.2.4", "2.2.5");

    @Test
    public void simple() {

        Complete complete = Complete.create()
                .withInput("com.chuusai:shapeless_2.11:2.2.");

        CompleteResult res;
        try {
            res = complete.complete();
        } catch (CoursierError e) {
            throw new RuntimeException(e);
        }

        CompleteResult expectedRes = CompleteResult.of(27, shapeless211_22_versions);

        assertEquals(expectedRes, res);

    }

    @Test
    public void withScalaVersion() {

        Complete complete = Complete.create()
                .withScalaBinaryVersion("2.11")
                .withScalaVersion("2.11.12")
                .withInput("com.chuusai::shapeless:2.2.");

        CompleteResult res;
        try {
            res = complete.complete();
        } catch (CoursierError e) {
            throw new RuntimeException(e);
        }

        CompleteResult expectedRes = CompleteResult.of(23, shapeless211_22_versions);

        assertEquals(expectedRes, res);

    }

}
