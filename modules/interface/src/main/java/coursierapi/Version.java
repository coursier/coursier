package coursierapi;

import coursier.internal.api.ApiHelper;

public final class Version {

  public static int compare(String version0, String version1) {
    return ApiHelper.compareVersions(version0, version1);
  }

}
