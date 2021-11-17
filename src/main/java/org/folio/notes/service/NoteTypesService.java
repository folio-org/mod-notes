package org.folio.notes.service;

import java.util.UUID;

import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;

public interface NoteTypesService {

  NoteTypeCollection getNoteTypeCollection(String query, Integer offset, Integer limit);

  NoteType getNoteType(UUID id);

  NoteType createNoteType(NoteType entity);

  void updateNoteType(UUID id, NoteType entity);

  void removeNoteType(UUID id);

  void populateDefaultType();
}
