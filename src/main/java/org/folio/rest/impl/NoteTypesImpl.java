package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;
import java.util.Objects;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSONException;

import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeCollection;
import org.folio.rest.jaxrs.resource.NoteTypes;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.userlookup.UserLookUp;

public class NoteTypesImpl implements NoteTypes {

  private static final String NOTE_TYPE_TABLE = "note_type";
  private static final String NOTE_TYPE_VIEW = "note_type_view";
  private final Logger logger = LoggerFactory.getLogger("mod-notes");

  public NoteTypesImpl(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField("id");
  }

  @Override
  public void getNoteTypes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    CQLWrapper cql;
    try {
      cql = getCQL(query, limit, offset);
    } catch (Exception e) {
      ValidationHelper.handleError(e, asyncResultHandler);
      return;
    }
    getNoteTypes(vertxContext, tenantId, cql).map(noteTypes -> {
      asyncResultHandler.handle(succeededFuture(NoteTypes.GetNoteTypesResponse.respond200WithApplicationJson(noteTypes)));
      return null;
    }).otherwise(e -> {
      ValidationHelper.handleError(e, asyncResultHandler);
      return null;
    });
  }

  private Future<NoteTypeCollection> getNoteTypes(Context vertxContext, String tenantId, CQLWrapper cql) {
    Future<Results<NoteType>> future = Future.future();
    PostgresClient.getInstance(vertxContext.owner(), tenantId).get(NOTE_TYPE_VIEW, NoteType.class, new String[] {"*"}, cql,
        true, false, future.completer());

    return future.map(this::mapNoteTypeResults);
  }

  private NoteTypeCollection mapNoteTypeResults(Results<NoteType> results) {
    NoteTypeCollection noteTypeCollection = new NoteTypeCollection();
    noteTypeCollection.setNoteTypes(results.getResults());
    Integer totalRecords = results.getResultInfo().getTotalRecords();
    noteTypeCollection.setTotalRecords(totalRecords);
    return noteTypeCollection;
  }

  @Validate
  @Override
  public void postNoteTypes(String lang, NoteType entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    Future<Boolean> idExistsFuture = Future.succeededFuture(false);
    if (entity.getId() != null) {
      idExistsFuture = checkIdExists(okapiHeaders, vertxContext, entity.getId());
    }
    idExistsFuture.map(idExists -> {
      if (idExists) {
        asyncResultHandler.handle(succeededFuture(PostNoteTypesResponse.respond400WithTextPlain(
            "Note type with specified UUID already exists")));
      }
      return null;
    }).compose(o -> setNoteTypeCreator(entity, okapiHeaders)).compose(aVoid -> {
      PgUtil.post(NOTE_TYPE_TABLE, entity, okapiHeaders, vertxContext, PostNoteTypesResponse.class, asyncResultHandler);
      return null;
    }).otherwise(exception -> {
      final Throwable exceptionCause = exception.getCause();
      if (exceptionCause instanceof NotFoundException || exceptionCause instanceof NotAuthorizedException
          || exceptionCause instanceof IllegalArgumentException || exceptionCause instanceof IllegalStateException) {
        asyncResultHandler.handle(succeededFuture(PostNoteTypesResponse.respond400WithTextPlain(exceptionCause
            .getMessage())));
      } else if (exception instanceof IllegalArgumentException) {
        asyncResultHandler.handle(succeededFuture(PostNoteTypesResponse.respond400WithTextPlain(exception.getMessage())));
      } else {
        asyncResultHandler.handle(succeededFuture(PostNoteTypesResponse.respond500WithTextPlain(exception.getMessage())));
      }
      return null;
    });

  }

  @Validate
  @Override
  public void getNoteTypesByTypeId(String typeId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getOneNoteType(typeId, okapiHeaders, vertxContext).map(noteType -> {
      asyncResultHandler.handle(succeededFuture(NoteTypes.GetNoteTypesByTypeIdResponse.respond200WithApplicationJson(
          noteType)));
      return null;
    }).otherwise(exception -> {
      if (exception instanceof HttpStatusException) {

        final int cause = ((HttpStatusException) exception).getStatusCode();

        if (Response.Status.NOT_FOUND.getStatusCode() == cause) {
          asyncResultHandler.handle(succeededFuture(NoteTypes.GetNoteTypesByTypeIdResponse.respond404WithTextPlain(
              ((HttpStatusException) exception).getPayload())));
        } else {
          asyncResultHandler.handle(succeededFuture(NoteTypes.GetNoteTypesByTypeIdResponse.respond500WithTextPlain(exception
              .getMessage())));
        }
      } else {
        asyncResultHandler.handle(succeededFuture(NoteTypes.GetNoteTypesByTypeIdResponse.respond500WithTextPlain(exception
            .getMessage())));
      }
      return null;
    });
  }

  @Validate
  @Override
  public void deleteNoteTypesByTypeId(String typeId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(NOTE_TYPE_TABLE, typeId, okapiHeaders, vertxContext, DeleteNoteTypesByTypeIdResponse.class,
        asyncResultHandler);
  }

  @Override
  public void putNoteTypesByTypeId(String typeId, String lang, NoteType entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    getOneNoteType(typeId, okapiHeaders, vertxContext).compose(oldNoteType -> {
      setNoteTypeCreator(oldNoteType, entity);
      return setNoteTypeUpdater(entity, okapiHeaders);
    }).compose(voidObject -> {
      PgUtil.put(NOTE_TYPE_TABLE, entity, typeId, okapiHeaders, vertxContext, PutNoteTypesByTypeIdResponse.class,
          asyncResultHandler);
      return null;
    }).otherwise(exception -> {
      if (exception instanceof HttpStatusException) {

        final int cause = ((HttpStatusException) exception).getStatusCode();

        if (Response.Status.NOT_FOUND.getStatusCode() == cause) {
          asyncResultHandler.handle(succeededFuture(PutNoteTypesByTypeIdResponse.respond404WithTextPlain(
              ((HttpStatusException) exception).getPayload())));
        } else if (Response.Status.BAD_REQUEST.getStatusCode() == cause) {
          asyncResultHandler.handle(succeededFuture(PutNoteTypesByTypeIdResponse.respond400WithTextPlain(
              ((HttpStatusException) exception).getPayload())));
        } else {
          asyncResultHandler.handle(succeededFuture(PutNoteTypesByTypeIdResponse.respond500WithTextPlain(exception
              .getMessage())));
        }
      } else {
        asyncResultHandler.handle(succeededFuture(PutNoteTypesByTypeIdResponse.respond500WithTextPlain(exception
            .getMessage())));
      }
      return null;
    });
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws CQL2PgJSONException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(NOTE_TYPE_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  private Future<Boolean> checkIdExists(Map<String, String> okapiHeaders, Context vertxContext, String id) {
    Future<Boolean> future = Future.future();
    PgUtil.postgresClient(vertxContext, okapiHeaders).getById(NOTE_TYPE_TABLE, id, NoteType.class, result -> {
      if (result.succeeded() && result.result() != null) {
        future.complete(true);
      } else {
        future.complete(false);
      }
    });
    return future;
  }

  private void setNoteTypeCreator(NoteType oldNoteType, NoteType noteType) {
    noteType.getMetadata().setCreatedByUsername(oldNoteType.getMetadata().getCreatedByUsername());
  }

  private Future<Void> setNoteTypeCreator(NoteType note, Map<String, String> okapiHeaders) {
    return UserLookUp.getUserInfo(okapiHeaders).map(userLookUp -> {
      note.getMetadata().setCreatedByUsername(userLookUp.getUserName());
      return null;
    });
  }

  private Future<Void> setNoteTypeUpdater(NoteType note, Map<String, String> okapiHeaders) {
    return UserLookUp.getUserInfo(okapiHeaders).map(userLookUp -> {
      note.getMetadata().setUpdatedByUsername(userLookUp.getUserName());
      return null;
    });
  }

  private Future<NoteType> getOneNoteType(String id, Map<String, String> okapiHeaders, Context context) {

    Future<NoteType> future = Future.future();
    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    PostgresClient.getInstance(context.owner(), tenantId).getById(NOTE_TYPE_VIEW, id, NoteType.class, reply -> {
      if (reply.succeeded()) {
        NoteType note = reply.result();
        if (Objects.isNull(note)) {
          future.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), "Note type " + id + " not found"));
        } else {
          future.complete(note);
        }
      } else {
        String error = PgExceptionUtil.badRequestMessage(reply.cause());
        logger.error(error, reply.cause());
        if (error == null) {
          future.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), reply.cause()
              .getMessage()));
        } else {
          future.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), error));
        }
      }
    });
    return future;
  }
}
