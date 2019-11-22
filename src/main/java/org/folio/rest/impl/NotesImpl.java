package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import static org.folio.common.pf.PartialFunctions.pf;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

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
import org.folio.note.NoteService;
import org.folio.rest.ResponseHelper;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.resource.Notes;
import org.folio.spring.SpringContextUtil;

public class NotesImpl implements Notes {
  private static final String LOCATION_PREFIX = "/notes/";
  private final Logger logger = LoggerFactory.getLogger("mod-notes");

  @Autowired
  private NoteService noteService;
  @Autowired @Qualifier("notesExcHandler")
  private PartialFunction<Throwable, Response> excHandler;

  public NotesImpl() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getNotes(String query,
                       int offset, int limit, String lang,
                       Map<String, String> okapiHeaders,
                       Handler<AsyncResult<Response>> asyncHandler,
                       Context vertxContext) {
    logger.debug("Getting notes. " + offset + "+" + limit + " q=" + query);

    ResponseHelper.respond(noteService.getNotes(query, offset, limit, tenantId(okapiHeaders)),
      GetNotesResponse::respond200WithApplicationJson, asyncHandler, excHandler);
  }

  @Override
  @Validate
  public void postNotes(String lang, Note note, Map<String, String> okapiHeaders,
                        Handler<AsyncResult<Response>> asyncHandler, Context context) {
    succeededFuture()
      .compose(o -> noteService.addNote(note, new OkapiParams(okapiHeaders)))
      .map(handleSuccessfulPost())
      .otherwise(
        userNotFoundHandler()
          .orElse(excHandler))
      .setHandler(asyncHandler);
  }

  @Override
  @Validate
  public void getNotesById(String id, String lang, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncHandler, Context context) {
    ResponseHelper.respond(noteService.getOneNote(id, tenantId(okapiHeaders)),
      GetNotesByIdResponse::respond200WithApplicationJson, asyncHandler, excHandler);
  }

  @Override
  @Validate
  public void deleteNotesById(String id, String lang, Map<String, String> okapiHeaders,
                              Handler<AsyncResult<Response>> asyncHandler, Context vertxContext) {
    ResponseHelper.respond(noteService.deleteNote(id, tenantId(okapiHeaders)),
      o -> DeleteNotesByIdResponse.respond204(), asyncHandler, excHandler);
  }

  @Override
  @Validate
  public void putNotesById(String id, String lang, Note note, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncHandler, Context vertxContext) {
    respond(() -> {
        OkapiParams okapiParams = new OkapiParams(okapiHeaders);
        return noteService.updateNote(id, note, okapiParams);
      },
      o -> PutNotesByIdResponse.respond204(),
      asyncHandler);
  }

  private Function<Note, Response> handleSuccessfulPost() {
    return note ->
      PostNotesResponse.respond201WithApplicationJson(note,
        PostNotesResponse.headersFor201().withLocation(LOCATION_PREFIX + note.getId()));
  }

  private PartialFunction<Throwable, Response> userNotFoundHandler() {
    return pf((NotFoundException.class::isInstance), t -> PostNotesResponse.respond400WithTextPlain(t.getMessage()));
  }

  private <T> void respond(Producer<Future<T>> futureProducer, Function<T, Response> mapper,
                           Handler<AsyncResult<Response>> asyncHandler) {
    Future<T> future = succeededFuture()
      .compose(o -> futureProducer.call());
    ResponseHelper.respond(future, mapper, asyncHandler, excHandler);
  }

}
