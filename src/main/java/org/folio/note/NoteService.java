package org.folio.note;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.common.OkapiParams;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.userlookup.UserLookUp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public interface NoteService {
  Future<NoteCollection> getNotes(String query, int offset, int limit, String tenantId, Vertx vertx);
  Future<Note> addNote(Note note, UserLookUp creatorUser, Vertx vertx, OkapiParams tenantId);
  Future<Note> getOneNote(String id, String tenantId, Vertx vertx);
  Future<Void> deleteNote(String id, Handler<AsyncResult<Response>> asyncResultHandler, String tenantId, Vertx vertx);
  Future<Void> updateNoteWithUser(String id, Note note, Map<String, String> okapiHeaders, Context vertxContext);
}
