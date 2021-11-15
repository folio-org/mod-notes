package org.folio.notes.service;

import java.util.List;
import java.util.UUID;

import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;

public interface TypeService {

  NoteTypeCollection fetchTypeCollection(String query, Integer offset, Integer limit);

  NoteType fetchById(UUID id);

  List<NoteType> fetchByIds(List<UUID> ids);

  NoteType createType(NoteType entity);

  void updateType(UUID id, NoteType entity);

  void removeTypeById(UUID id);

  void populateDefaultType();
}
