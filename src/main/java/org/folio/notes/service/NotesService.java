package org.folio.notes.service;

import java.util.List;
import java.util.UUID;

import org.folio.notes.domain.dto.LinkStatusFilter;
import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.dto.NoteCollection;
import org.folio.notes.domain.dto.NoteLinkUpdateCollection;
import org.folio.notes.domain.dto.NotesOrderBy;
import org.folio.notes.domain.dto.OrderDirection;

public interface NotesService {

  NoteCollection getNoteCollection(String query, Integer offset, Integer limit);

  NoteCollection getNoteCollection(String domain, String objectType, String objectId, String search, List<String> noteType,
                                   LinkStatusFilter status, NotesOrderBy orderBy, OrderDirection order, Integer offset,
                                   Integer limit);

  Note getNote(UUID id);

  Note createNote(Note note);

  void updateLinks(String objectType, String objectId, NoteLinkUpdateCollection noteLinkUpdateCollection);

  void updateNote(UUID id, Note note);

  void deleteNote(UUID id);
}
