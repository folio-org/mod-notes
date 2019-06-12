package org.folio.note;


import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;

import io.vertx.core.Future;

public interface NoteRepository {
  Future<NoteCollection> findByQuery(String cqlQuery, int offset, int limit, String tenantId);

  Future<Note> save(Note note, String tenantId);

  /**
   * Returns note with given id.
   * If note with given id doesn't exist then returns failed Future with NotFoundException as a cause.
   */
  Future<Note> findOne(String id, String tenantId);

  /**
   * Deletes note with given id.
   * If note with given id doesn't exist then returns failed Future with NotFoundException as a cause.
   */
  Future<Void> delete(String id, String tenantId);

  /**
   * Updates note with given id.
   * If note with given id doesn't exist then returns failed Future with NotFoundException as a cause.
   */
  Future<Void> update(String id, Note note, String tenantId);
}
