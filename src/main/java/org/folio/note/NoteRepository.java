package org.folio.note;


import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;

import io.vertx.core.Future;

public interface NoteRepository {
  Future<NoteCollection> findByQuery(String cqlQuery, int offset, int limit, String tenantId);

  Future<Note> save(Note note, String tenantId);

  Future<Note> findOne(String id, String tenantId);

  Future<Void> delete(String id, String tenantId);

  Future<Void> save(String id, Note note, String tenantId);
}
