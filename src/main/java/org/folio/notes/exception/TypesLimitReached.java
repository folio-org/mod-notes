package org.folio.notes.exception;

public class TypesLimitReached extends RuntimeException {

  private static final String MESSAGE = "Maximum number of note types allowed is ";

  public TypesLimitReached(int limit) {
    super(MESSAGE + limit);
  }
}
