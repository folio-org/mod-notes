package org.folio.rest.impl;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeCollection;
import org.folio.rest.jaxrs.resource.NoteTypes;
import org.folio.spring.SpringContextUtil;
import org.folio.type.NoteTypeService;
import org.folio.util.OkapiParams;
import org.folio.util.pf.PartialFunction;

public class NoteTypesImpl implements NoteTypes {

  @Autowired
  private NoteTypeService typeService;
  @Autowired @Qualifier("defaultExcHandler")
  private PartialFunction<Throwable, Response> exceptionHandler;


  public NoteTypesImpl() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getNoteTypes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<NoteTypeCollection> found = typeService.findByQuery(query, offset, limit, lang, tenantId(okapiHeaders));

    respond(found, GetNoteTypesResponse::respond200WithApplicationJson, asyncResultHandler);
  }

  @Validate
  @Override
  public void postNoteTypes(String lang, NoteType entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<NoteType> saved = typeService.save(entity, new OkapiParams(okapiHeaders));

    respond(saved,
      noteType -> PostNoteTypesResponse.respond201WithApplicationJson(noteType, PostNoteTypesResponse.headersFor201()),
      asyncResultHandler);
  }

  @Validate
  @Override
  public void getNoteTypesByTypeId(String typeId, String lang, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<NoteType> found = typeService.findById(typeId, tenantId(okapiHeaders));

    respond(found, GetNoteTypesByTypeIdResponse::respond200WithApplicationJson, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteNoteTypesByTypeId(String typeId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<Void> deleted = typeService.delete(typeId, tenantId(okapiHeaders));

    respond(deleted, v -> DeleteNoteTypesByTypeIdResponse.respond204(), asyncResultHandler);
  }

  @Validate
  @Override
  public void putNoteTypesByTypeId(String typeId, String lang, NoteType entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<Void> updated = typeService.update(typeId, entity, new OkapiParams(okapiHeaders));

    respond(updated, v -> PutNoteTypesByTypeIdResponse.respond204(), asyncResultHandler);
  }

  private <T> Future<Response> respond(Future<T> result, Function<T, Response> mapper,
                                       Handler<AsyncResult<Response>> asyncResultHandler) {
    return result.map(mapper)
            .otherwise(throwable -> {
              // TD (Dima Tkachenko): review code
              Throwable cause = (throwable instanceof CompletionException) && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
              return exceptionHandler.apply(cause);
            })
            .setHandler(asyncResultHandler);
  }

}
