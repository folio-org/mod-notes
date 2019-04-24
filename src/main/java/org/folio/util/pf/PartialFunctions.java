package org.folio.util.pf;

import java.util.function.Function;
import java.util.function.Predicate;

public class PartialFunctions {

  private static final PartialFunction EMPTY = new Empty<>();

  public static <T, R> PartialFunction<T, R> as(Predicate<? super T> isDefinedAt,
                                                Function<? super T, ? extends R> function) {
    return new PartialFunctionImpl<>(isDefinedAt, function);
  }

  @SuppressWarnings("unchecked")
  public static <T, R> PartialFunction<T, R> empty() {
    return (PartialFunction<T, R>) EMPTY;
  }

  public static <T, R> PartialFunction<T, R> orElse(PartialFunction<T, R> f1, PartialFunction<T, R> f2) {
    return new OrElse<>(f1, f2);
  }

  public static <T, R, V> PartialFunction<T, V> andThen(PartialFunction<T, R> pf, Function<? super R, ? extends V> after) {
    return new AndThen<>(pf, after);
  }

  public static <T, R> PartialFunction<T, R> logged(PartialFunction<T, R> pf, LogHandler<? super T> logger) {
    return new LoggedApplication<>(pf, logger);
  }
}
