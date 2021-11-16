package org.folio.notes.service;

import java.util.UUID;

import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;

public interface NoteTypesService {

  NoteTypeCollection getNoteTypesCollection(String query, Integer offset, Integer limit);

  NoteType getById(UUID id);

  NoteType createNoteType(NoteType entity);

  void updateNoteType(UUID id, NoteType entity);

  void removeNoteTypeById(UUID id);

  void populateDefaultType();
}
