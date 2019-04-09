package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeCollection;
import org.folio.rest.jaxrs.model.NoteTypeUsage;
import org.folio.rest.jaxrs.resource.NoteTypes;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSONException;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.vertx.core.Future.succeededFuture;
import io.vertx.core.Vertx;

public class NoteTypesImpl implements NoteTypes {

  private static final String NOTE_TYPE_TABLE = "note_type";
  private static final String IDFIELDNAME = "id";

  public NoteTypesImpl(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(IDFIELDNAME);
  }

  private final Messages messages = Messages.getInstance();

  @Override
  public void getNoteTypes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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

  @Override
  public void postNoteTypes(String lang, NoteType entity, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(succeededFuture(PostNoteTypesResponse.status(Response.Status.NOT_IMPLEMENTED).build()));
  }

  @Validate
  @Override
  public void getNoteTypesByTypeId(String typeId, String lang, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    Handler<AsyncResult<Response>> handlerWrapper = event -> {
      if (event.succeeded() && Response.Status.OK.getStatusCode() == event.result().getStatus()) {

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

    asyncResultHandler.handle(succeededFuture(DeleteNoteTypesByTypeIdResponse.status(Response.Status.NOT_IMPLEMENTED).build()));
  }

  @Override
  public void putNoteTypesByTypeId(String typeId, String lang, NoteType entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    removeNotStoredFields(entity);
    PgUtil.put(NOTE_TYPE_TABLE, entity, typeId, okapiHeaders, vertxContext, PutNoteTypesByTypeIdResponse.class, asyncResultHandler);
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws CQL2PgJSONException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(NOTE_TYPE_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  private void removeNotStoredFields(NoteType entity) {
    entity.setUsage(null);
  }
}
