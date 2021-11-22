package org.folio.notes.exception;

import feign.error.FeignExceptionConstructor;

public class FolioExternalException extends RuntimeException {

  @FeignExceptionConstructor
  public FolioExternalException(String message) {
    super(message);
  }
}
