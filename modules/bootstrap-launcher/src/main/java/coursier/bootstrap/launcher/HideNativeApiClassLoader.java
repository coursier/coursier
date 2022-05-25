package coursier.bootstrap.launcher;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

class HideNativeApiClassLoader extends ClassLoader {

  HideNativeApiClassLoader(ClassLoader parent) {
    super(parent);
  }

  private static final String toHide = "META-INF/services/coursier.jniutils.NativeApi";

  @Override
  public URL getResource(String name) {
    if (name.equals(toHide))
      return null;
    return super.getResource(name);
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    if (name.equals(toHide))
      return Collections.emptyEnumeration();
    return super.getResources(name);
  }

}
