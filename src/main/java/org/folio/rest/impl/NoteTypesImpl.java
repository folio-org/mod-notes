package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.springframework.beans.factory.annotation.Autowired;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSONException;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeCollection;
import org.folio.rest.jaxrs.model.NoteTypeUsage;
import org.folio.rest.jaxrs.resource.NoteTypes;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.spring.SpringContextUtil;
import org.folio.type.NoteTypeService;

public class NoteTypesImpl implements NoteTypes {

  private static final String NOTE_TYPE_TABLE = "note_type";

  @Autowired
  private NoteTypeService typeService;
  @Autowired
  private Messages messages;


  public NoteTypesImpl(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, vertx.getOrCreateContext());
  }

  @Override
  public void getNoteTypes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<List<NoteType>> found = typeService.findByQuery(query, offset, limit, lang, tenantId(okapiHeaders));

    vertxContext.runOnContext(v -> {
      try {
        CQLWrapper cql = getCQL(query, limit, offset);
        PgUtil.postgresClient(vertxContext, okapiHeaders).get(NOTE_TYPE_TABLE, NoteType.class,
          new String[]{"*"}, cql, true, false,
          reply -> {
            try {
              if (reply.succeeded()) {
                NoteTypeCollection noteType = new NoteTypeCollection();
                List<NoteType> noteTypesList = reply.result().getResults();
                noteTypesList.forEach(noteT -> noteT.setUsage(new NoteTypeUsage().withNoteTotal(0)));
                noteType.setNoteTypes(noteTypesList);
                noteType.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(succeededFuture(GetNoteTypesResponse
                  .respond200WithApplicationJson(noteType)));
              } else {
                asyncResultHandler.handle(succeededFuture(GetNoteTypesResponse
                  .respond400WithTextPlain((messages.getMessage(
                    lang, MessageConsts.InvalidURLPath)))));
              }
            } catch (Exception e) {
              asyncResultHandler.handle(succeededFuture(GetNoteTypesResponse
                .respond500WithTextPlain((messages.getMessage(
                  lang, MessageConsts.InternalServerError)))));
            }
          });
      } catch (Exception fe) {
        asyncResultHandler.handle(succeededFuture(GetNoteTypesResponse.respond400WithTextPlain(
          "CQL Parsing Error for '" + query + "': " + fe.getLocalizedMessage())));
      }
    });
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
    Future<Optional<NoteType>> found = typeService.findById(typeId, tenantId(okapiHeaders));
    Handler<AsyncResult<Response>> handlerWrapper = event -> {
      if (event.succeeded() && Response.Status.OK.getStatusCode() == event.result().getStatus()) {

        final NoteType noteType = (NoteType) event.result().getEntity();
        if (Objects.isNull(noteType.getUsage())) {
          noteType.setUsage(new NoteTypeUsage().withNoteTotal(0));
        }
      }
      asyncResultHandler.handle(event);
    };

    PgUtil.getById(NOTE_TYPE_TABLE, NoteType.class, typeId, okapiHeaders, vertxContext, GetNoteTypesByTypeIdResponse.class,
      handlerWrapper);

  }

  @Validate
  @Override
  public void deleteNoteTypesByTypeId(String typeId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<Boolean> deleted = typeService.delete(typeId, tenantId(okapiHeaders));
    PgUtil.deleteById(NOTE_TYPE_TABLE, typeId, okapiHeaders, vertxContext, DeleteNoteTypesByTypeIdResponse.class,
        asyncResultHandler);
  }

  @Override
  public void putNoteTypesByTypeId(String typeId, String lang, NoteType entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future<NoteType> saved = typeService.save(entity, tenantId(okapiHeaders));
    PgUtil.put(NOTE_TYPE_TABLE, entity, typeId, okapiHeaders, vertxContext, PutNoteTypesByTypeIdResponse.class,
        asyncResultHandler);
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws CQL2PgJSONException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(NOTE_TYPE_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

}
