package org.folio.rest.impl;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Map;
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
import org.folio.rest.jaxrs.model.NoteTypeUsage;
import org.folio.rest.jaxrs.resource.NoteTypes;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.tools.messages.Messages;
import org.folio.spring.SpringContextUtil;
import org.folio.type.NoteTypeService;

public class NoteTypesImpl implements NoteTypes {

  private static final String NOTE_TYPE_TABLE = "note_type";

  @Autowired
  private NoteTypeService typeService;
  @Autowired
  private Messages messages;
  @Autowired @Qualifier("default")
  private Function<Throwable, Response> exceptionHandler;


  public NoteTypesImpl(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, vertx.getOrCreateContext());
  }

  @Override
  public void getNoteTypes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<NoteTypeCollection> found = typeService.findByQuery(query, offset, limit, lang, tenantId(okapiHeaders));

    found.map(col -> updateNoteTypeUsage(col, 0)) // temporarily, until the usage is not calculated
      .map(GetNoteTypesResponse::respond200WithApplicationJson)
      .map(Response.class::cast)
      .otherwise(exceptionHandler)
      .setHandler(asyncResultHandler);
  }

  @Validate
  @Override
  public void postNoteTypes(String lang, NoteType entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<NoteType> saved = typeService.save(entity, tenantId(okapiHeaders));
    PgUtil.post(NOTE_TYPE_TABLE, entity, okapiHeaders, vertxContext, PostNoteTypesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getNoteTypesByTypeId(String typeId, String lang, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<NoteType> found = typeService.findById(typeId, tenantId(okapiHeaders));

    found.map(noteType -> { // temporarily, until the usage is not calculated
        noteType.setUsage(new NoteTypeUsage().withNoteTotal(0));
        return noteType;
      }) 
      .map(GetNoteTypesByTypeIdResponse::respond200WithApplicationJson)
      .map(Response.class::cast)
      .otherwise(exceptionHandler)
      .setHandler(asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteNoteTypesByTypeId(String typeId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<String> deleted = typeService.delete(typeId, tenantId(okapiHeaders));

    deleted.map(DeleteNoteTypesByTypeIdResponse.respond204())
      .map(Response.class::cast)
      .otherwise(exceptionHandler)
      .setHandler(asyncResultHandler);
  }

  @Override
  public void putNoteTypesByTypeId(String typeId, String lang, NoteType entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<NoteType> saved = typeService.save(entity, tenantId(okapiHeaders));
    PgUtil.put(NOTE_TYPE_TABLE, entity, typeId, okapiHeaders, vertxContext, PutNoteTypesByTypeIdResponse.class,
        asyncResultHandler);
  }

  private NoteTypeCollection updateNoteTypeUsage(NoteTypeCollection noteTypeCollection, int usage) {
    noteTypeCollection.getNoteTypes().forEach(noteType -> noteType.setUsage(new NoteTypeUsage().withNoteTotal(usage)));
    return noteTypeCollection;
  }

}
