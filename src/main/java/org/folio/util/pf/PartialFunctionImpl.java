package org.folio.util.pf;

import java.util.function.Function;
import java.util.function.Predicate;

class PartialFunctionImpl<T, R> implements PartialFunction<T, R> {

  private Predicate<? super T> isDefinedAt;
  private Function<T, R> function;


  PartialFunctionImpl(Predicate<? super T> isDefinedAt, Function<T, R> function) {
    this.function = function;
    this.isDefinedAt = isDefinedAt;
  }

  @Override
  public boolean isDefinedAt(T t) {
    return isDefinedAt.test(t);
  }

  @Override
  public R applySuccessfully(T t) {
    return function.apply(t);
  }
}
