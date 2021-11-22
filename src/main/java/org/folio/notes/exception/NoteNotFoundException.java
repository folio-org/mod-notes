package org.folio.notes.exception;

import java.util.UUID;

public class NoteNotFoundException extends ResourceNotFoundException {

  protected static final String NOTE_RESOURCE_NAME = "Note";

  public NoteNotFoundException(UUID id) {
    super(NOTE_RESOURCE_NAME, id);
  }
}
