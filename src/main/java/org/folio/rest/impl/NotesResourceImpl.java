package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;
import java.util.function.Function;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.glassfish.jersey.internal.util.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.folio.common.OkapiParams;
import org.folio.common.pf.PartialFunction;
import org.folio.common.pf.PartialFunctions;
import org.folio.note.NoteService;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.resource.Notes;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.spring.SpringContextUtil;

public class NotesResourceImpl implements Notes {
  private static final String LOCATION_PREFIX = "/notes/";
  private final Logger logger = LoggerFactory.getLogger("mod-notes");

  @Autowired
  private NoteService noteService;
  @Autowired @Qualifier("defaultExcHandler")
  private PartialFunction<Throwable, Response> exceptionHandler;

  // Get this from the restVerticle, like the rest, when it gets defined there.
  public NotesResourceImpl(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, vertx.getOrCreateContext());
  }

  @Override
  @Validate
  public void getNotes(String query,
                       int offset, int limit, String lang,
                       Map<String, String> okapiHeaders,
                       Handler<AsyncResult<Response>> asyncResultHandler,
                       Context vertxContext) {
    logger.debug("Getting notes. " + offset + "+" + limit + " q=" + query);
    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    respond(noteService.getNotes(query, offset, limit, tenantId), GetNotesResponse::respond200WithApplicationJson, asyncResultHandler);
  }

  @Override
  @Validate
  public void postNotes(String lang, Note note, Map<String, String> okapiHeaders,
                        Handler<AsyncResult<Response>> asyncResultHandler, Context context) {
    succeededFuture()
      .compose(o -> noteService.addNote(note, new OkapiParams(okapiHeaders)))
      .map(handleSuccessfulPost())
      .otherwise(
        userNotFoundHandler()
          .orElse(exceptionHandler))
      .setHandler(asyncResultHandler);
  }

  @Override
  @Validate
  public void getNotesById(String id, String lang, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler, Context context) {
    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    respond(noteService.getOneNote(id, tenantId), GetNotesByIdResponse
      ::respond200WithApplicationJson, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteNotesById(String id, String lang, Map<String, String> okapiHeaders,
                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    respond(noteService.deleteNote(id, tenantId), o -> DeleteNotesByIdResponse.respond204(), asyncResultHandler);
  }

  @Override
  @Validate
  public void putNotesById(String id, String lang, Note note, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    respond(() -> {
        OkapiParams okapiParams = new OkapiParams(okapiHeaders);
        return noteService.updateNote(id, note, okapiParams);
      },
      o -> PutNotesByIdResponse.respond204(),
      asyncResultHandler);
  }

  private Function<Note, Response> handleSuccessfulPost() {
    return note ->
      PostNotesResponse.respond201WithApplicationJson(note,
        PostNotesResponse.headersFor201().withLocation(LOCATION_PREFIX + note.getId()));
  }

  private PartialFunction<Throwable, Response> userNotFoundHandler() {
    return PartialFunctions.pf((NotFoundException.class::isInstance), t -> PostNotesResponse.respond400WithTextPlain(t.getMessage()));
  }

  private <T> void respond(Producer<Future<T>> futureProducer, Function<T, Response> mapper,
                           Handler<AsyncResult<Response>> asyncResultHandler) {
    Future<T> future = succeededFuture()
      .compose(o -> futureProducer.call());
    respond(future, mapper, asyncResultHandler);
  }

  private <T> void respond(Future<T> result, Function<T, Response> mapper,
                           Handler<AsyncResult<Response>> asyncResultHandler) {
    result.map(mapper)
      .otherwise(exceptionHandler)
      .setHandler(asyncResultHandler);
  }
}
