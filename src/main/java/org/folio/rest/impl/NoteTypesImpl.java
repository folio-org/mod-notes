package org.folio.rest.impl;

import java.util.Map;

import java.util.Objects;
import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeUsage;
import org.folio.rest.jaxrs.resource.NoteTypes;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class NoteTypesImpl implements NoteTypes {

  private static final String NOTE_TYPE_TABLE = "note_type";
  private static final String IDFIELDNAME = "id";

  public NoteTypesImpl(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(IDFIELDNAME);
  }

  @Override
  public void getNoteTypes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(GetNoteTypesResponse.status(Response.Status.NOT_IMPLEMENTED).build()));
  }

  @Override
  public void postNoteTypes(String lang, NoteType entity, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(PostNoteTypesResponse.status(Response.Status.NOT_IMPLEMENTED).build()));
  }

  @Validate
  @Override
  public void getNoteTypesByTypeId(String typeId, String lang, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    Handler<AsyncResult<Response>> handlerWrapper = event -> {
      if(event.succeeded() && Response.Status.OK.getStatusCode() == event.result().getStatus()) {

        final NoteType noteType = (NoteType) event.result().getEntity();
        if (Objects.isNull(noteType.getUsage())) {
          noteType.setUsage(new NoteTypeUsage().withNoteTotal(0));
        }
      }
      asyncResultHandler.handle(event);
    };

    PgUtil.getById(NOTE_TYPE_TABLE, NoteType.class, typeId, okapiHeaders, vertxContext, GetNoteTypesByTypeIdResponse.class, handlerWrapper);

  }

  @Override
  public void deleteNoteTypesByTypeId(String typeId, String lang, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(DeleteNoteTypesByTypeIdResponse.status(Response.Status.NOT_IMPLEMENTED).build()));
  }

  @Override
  public void putNoteTypesByTypeId(String typeId, String lang, NoteType entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    removeNotStoredFields(entity);
    PgUtil.put(NOTE_TYPE_TABLE, entity, typeId, okapiHeaders, vertxContext, PutNoteTypesByTypeIdResponse.class, asyncResultHandler);
  }

  private void removeNotStoredFields(NoteType entity) {
    entity.setUsage(null);
  }
}
