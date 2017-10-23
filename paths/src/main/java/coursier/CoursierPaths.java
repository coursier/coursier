package coursier;

import java.io.File;

import io.github.soc.directories.ProjectDirectories;

/**
 * Computes Coursier's directories according to the standard
 * defined by operating system Coursier is running on.
 * 
 * @implNote If more paths e. g. for configuration or application data is required,
 *           use {@link #coursierDirectories} and do not roll your own logic.
 */
public final class CoursierPaths {
	private CoursierPaths() {
		throw new Error();
	}
	
	private static ProjectDirectories coursierDirectories;
	
	public static File cacheDirectory() {
        String path = System.getenv("COURSIER_CACHE");

        if (path == null)
          path = System.getProperty("coursier.cache");

        if (path == null) {
        	File coursierDotFile = new File(System.getProperty("user.home") + "/.coursier");
        	if (coursierDotFile.isDirectory())
        		path = System.getProperty("user.home") + "/.coursier/cache/";
        }

        if (path == null) {
        	 coursierDirectories = ProjectDirectories.fromProjectName("Coursier");
        	 path = coursierDirectories.projectCacheDir;
        }

        File coursierCacheDirectory = new File(path).getAbsoluteFile();

        if (coursierCacheDirectory.getName().equals("v1"))
        	coursierCacheDirectory = coursierCacheDirectory.getParentFile();
        
        return coursierCacheDirectory.getAbsoluteFile();
	}
}
