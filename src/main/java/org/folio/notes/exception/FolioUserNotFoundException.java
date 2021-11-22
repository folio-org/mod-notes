package org.folio.notes.exception;

import feign.error.FeignExceptionConstructor;

public class FolioUserNotFoundException extends FolioExternalException {

  @FeignExceptionConstructor
  public FolioUserNotFoundException(String body) {
    super(body);
  }
}
