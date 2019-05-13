package org.folio.util.exc;

import static java.lang.String.format;

import javax.ws.rs.NotFoundException;

public class Exceptions {

  private static final String NOT_FOUND_MSG = "%s not found by id: %s";

  private Exceptions() {
  }

  public static NotFoundException notFound(String entity, String id) {
    return new NotFoundException(format(NOT_FOUND_MSG, entity, id));
  }

  public static NotFoundException notFound(Class<?> entityClass, String id) {
    return notFound(entityClass.getSimpleName(), id);
  }
}
