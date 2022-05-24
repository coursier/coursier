package coursier.bootstrap.launcher.credentials;

import java.io.Serializable;

/**
 * Java copy of coursier.credentials.Password
 */
public final class Password<T> implements Serializable {

  private static final long serialVersionUID = 1L;

  private final T value;

  public Password(T value) {
    this.value = value;
  }

  public T getValue() {
    return value;
  }

  @Override
  public String toString() {
    return "****";
  }

  @Override
  public int hashCode() {
    return "****".hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Password<?> other = (Password<?>) obj;
    if (value == null) {
      return other.value == null;
    } else return value.equals(other.value);
  }

}
