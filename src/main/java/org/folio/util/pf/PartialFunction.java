package org.folio.util.pf;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Inspired by <a href="https://www.scala-lang.org/api/current/scala/PartialFunction.html">Scala implementation</a>
 * of partial functions.
 * 
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 */
public interface PartialFunction<T, R> extends Function<T, R> {

  @Override
  default R apply(T t) {
    return applyOrElse(t, PartialFunctions.empty());
  }

  default R applyOrElse(T t, Function<? super T, ? extends R> otherwise) {
    Objects.requireNonNull(otherwise);
    return isDefinedAt(t) ? applySuccessfully(t) : otherwise.apply(t);
  }

  boolean isDefinedAt(T t);

  /**
   * Important:
   * The method shouldn't be called directly. It is more of internal one.
   * Use {@link #apply(Object)} or {@link #applyOrElse(Object, Function)}
   */
  R applySuccessfully(T t);

  default PartialFunction<T, R> orElse(PartialFunction<T, R> fallback) {
    Objects.requireNonNull(fallback);
    return PartialFunctions.orElse(this, fallback);
  }

  @Override
  default <V> PartialFunction<T, V> andThen(Function<? super R, ? extends V> after) {
    Objects.requireNonNull(after);
    return PartialFunctions.andThen(this, after);
  }

  default Function<T, Optional<R>> lift() {
    return PartialFunctions.lift(this);
  }
}
