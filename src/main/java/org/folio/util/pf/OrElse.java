package org.folio.util.pf;

final class OrElse<T, R> implements PartialFunction<T, R> {

  private PartialFunction<T, R> f1;
  private PartialFunction<T, R> f2;


  OrElse(PartialFunction<T, R> f1, PartialFunction<T, R> f2) {
    this.f1 = f1;
    this.f2 = f2;
  }

  @Override
  public boolean isDefinedAt(T t) {
    return f1.isDefinedAt(t) || f2.isDefinedAt(t);
  }

  @Override
  public R applySuccessfully(T t) {
    return f1.isDefinedAt(t) ? f1.apply(t) : f2.apply(t);
  }

}
