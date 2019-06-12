package org.folio.note;

import org.folio.common.OkapiParams;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;

import io.vertx.core.Future;

public interface NoteService {
  Future<NoteCollection> getNotes(String query, int offset, int limit, String tenantId);

  Future<Note> addNote(Note note, OkapiParams okapiParams);

  Future<Note> getOneNote(String id, String tenantId);

  Future<Void> deleteNote(String id, String tenantId);

  Future<Void> updateNote(String id, Note note, OkapiParams okapiParams);
}
