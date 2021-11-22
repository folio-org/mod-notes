package org.folio.notes.exception;

import java.util.UUID;

public class NoteTypeNotFoundException extends ResourceNotFoundException {

  protected static final String NOTE_TYPE_RESOURCE_NAME = "Note type";

  public NoteTypeNotFoundException(UUID id) {
    super(NOTE_TYPE_RESOURCE_NAME, id);
  }
}
