package org.folio.notes.exception;

public class NoteTypesLimitReached extends RuntimeException {

  private static final String MESSAGE = "Maximum number of note types allowed is ";
  private final int limit;

  public NoteTypesLimitReached(int limit) {
    super(MESSAGE + limit);
    this.limit = limit;
  }

  public int getLimit() {
    return limit;
  }
}
