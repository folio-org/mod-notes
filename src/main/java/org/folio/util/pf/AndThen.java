package org.folio.util.pf;

import java.util.function.Function;

final class AndThen<T, R, V> implements PartialFunction<T, V> {

  private PartialFunction<T, R> pf;
  private Function<? super R, ? extends V> after;


  AndThen(PartialFunction<T, R> pf, Function<? super R, ? extends V> after) {
    this.pf = pf;
    this.after = after;
  }

  @Override
  public boolean isDefinedAt(T t) {
    return pf.isDefinedAt(t);
  }

  @Override
  public V applySuccessfully(T t) {
    return after.apply(pf.apply(t));
  }

}
