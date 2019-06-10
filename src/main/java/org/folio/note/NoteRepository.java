package org.folio.note;


import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;

import io.vertx.core.Future;

public interface NoteRepository {
  Future<NoteCollection> getNotes(String cqlQuery, int offset, int limit, String tenantId);

  Future<Note> saveNote(Note note, String tenantId);

  Future<Note> getOneNote(String id, String tenantId);

  Future<Void> deleteNote(String id, String tenantId);

  Future<Void> updateNote(String id, Note note, String tenantId);
}
