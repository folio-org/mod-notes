package org.folio.util.pf;

import java.util.Optional;
import java.util.function.Function;

class Lifted<T, R> implements Function<T, Optional<R>> {

  private PartialFunction<T, R> pf;

  Lifted(PartialFunction<T, R> pf) {
  }

  @Override
  public Optional<R> apply(T t) {
    return Optional.ofNullable(pf.applyOrElse(t, t1 -> null));
  }

}
