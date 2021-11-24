package org.folio.notes.exception;

import feign.error.FeignExceptionConstructor;

public class FolioUnauthorizedException extends FolioExternalException {

  @FeignExceptionConstructor
  public FolioUnauthorizedException(String body) {
    super(body);
  }
}
