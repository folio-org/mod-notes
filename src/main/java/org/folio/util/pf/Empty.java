package org.folio.util.pf;

class Empty<T, R> implements PartialFunction<T, R> {

  @Override
  public R apply(T t) {
    throw new NotDefinedException(t);
  }

  @Override
  public boolean isDefinedAt(T t) {
    return false;
  }

  @Override
  public R applySuccessfully(T t) {
    return null;
  }
}
