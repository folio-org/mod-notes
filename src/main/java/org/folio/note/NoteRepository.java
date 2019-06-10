package org.folio.note;


import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.persist.cql.CQLWrapper;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public interface NoteRepository {
  Future<NoteCollection> getNotes(String tenantId, CQLWrapper cql, Vertx vertx);

  Future<NoteCollection> getNotes(String cqlQuery, int offset, int limit, String tenantId, Vertx owner);

  Future<Note> saveNote(Note note, Vertx vertx, String tenantId);

  Future<Note> getOneNote(String id, String tenantId, Vertx vertx);

  Future<Void> deleteNote(String id, String tenantId, Vertx vertx);
}
