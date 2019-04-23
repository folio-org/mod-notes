package org.folio.util.pf;

public class NotDefinedException extends RuntimeException {

  public NotDefinedException(Object arg) {
    super("Not defined for: " + arg);
  }

}
