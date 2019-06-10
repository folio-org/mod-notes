package org.folio.note;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.common.OkapiParams;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.userlookup.UserLookUp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public interface NoteService {
  Future<NoteCollection> getNotes(Context vertxContext, String tenantId, CQLWrapper cql);

  Future<NoteCollection> getNotes(String query, int offset, int limit, Context vertxContext, String tenantId);
  Future<Note> addNote(Note note, UserLookUp creatorUser, Vertx vertx, OkapiParams tenantId);
  Future<Note> getOneNote(String id, String tenantId, Vertx vertx);
  Future<Void> deleteNote(String id, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext, String tenantId);
  Future<Void> updateNoteWithUser(String id, Note note, Map<String, String> okapiHeaders, Context vertxContext);
}
