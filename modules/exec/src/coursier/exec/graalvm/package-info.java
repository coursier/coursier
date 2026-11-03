/**
 * Internal: GraalVM substitutions, used in native images instead of the JNA-based
 * implementations of {@code coursier.exec}.
 *
 * <p>These are picked up automatically by {@code native-image}, and are not meant to be used
 * directly. The classes here reference image builder classes that aren't on the class path of
 * users, which is why they don't live in the {@code coursier.exec} package itself.
 */
package coursier.exec.graalvm;
