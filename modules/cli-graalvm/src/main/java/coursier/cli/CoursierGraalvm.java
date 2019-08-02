package coursier.cli;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

public class CoursierGraalvm {

    static {
        // Run static initializers of these at build time, when resources are available
        String info = org.jline.utils.InfoCmp.getLoadedInfoCmp("ansi");
        String ver = coursier.util.Properties.version();
    }

    public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());
        coursier.cli.Coursier.main(args);
    }
}
