package org.folio.notes.exception;

public class NoteTypesLimitReached extends RuntimeException {

  private static final String MESSAGE = "Maximum number of note types allowed is ";

  public NoteTypesLimitReached(int limit) {
    super(MESSAGE + limit);
  }
}
