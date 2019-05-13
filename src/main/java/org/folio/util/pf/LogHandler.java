package org.folio.util.pf;

@FunctionalInterface
public interface LogHandler<T> {

  void log(T t);

}
