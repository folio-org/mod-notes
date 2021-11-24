package org.folio.notes.exception;

import java.util.UUID;

public abstract class ResourceNotFoundException extends RuntimeException {

  private static final String NOT_FOUND_MSG_TEMPLATE = "%s with ID [%s] was not found";

  protected ResourceNotFoundException(String resourceName, UUID id) {
    super(String.format(NOT_FOUND_MSG_TEMPLATE, resourceName, id));
  }
}
