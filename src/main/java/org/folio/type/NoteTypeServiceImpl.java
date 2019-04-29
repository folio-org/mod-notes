package org.folio.type;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.future;
import static io.vertx.core.Future.succeededFuture;

import java.util.List;

import javax.ws.rs.BadRequestException;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.folio.config.Configuration;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeCollection;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.util.OkapiParams;
import org.folio.util.exc.Exceptions;

@Component
public class NoteTypeServiceImpl implements NoteTypeService {

  private static final String NOTE_TYPES_NUMBER_LIMIT_PROP = "note.types.number.limit";

  @Autowired
  private NoteTypeRepository repository;
  @Autowired
  private Configuration configuration;
  @Value("${note.types.number.limit.default}")
  private int defaultNoteTypeLimit;

  @Override
  public Future<NoteTypeCollection> findByQuery(String query, int offset, int limit, String lang, String tenantId) {
    return repository.findByQuery(query, offset, limit, tenantId);
  }

  @Override
  public Future<NoteType> findById(String id, String tenantId) {
    return repository.findById(id, tenantId)
            .map(noteType -> noteType.orElseThrow(() -> Exceptions.notFound(NoteType.class, id)));
  }

  @Override
  public Future<List<NoteType>> findByIds(List<String> ids, String tenantId) {
    return repository.findByIds(ids, tenantId);
  }

  @Override
  public Future<NoteType> save(NoteType entity, OkapiParams params) {
    Future<Void> validation = validateNoteTypeLimit(params);

    Future<NoteType> result = future();

    validation.compose(v -> repository.save(entity, params.getTenant()))
      .setHandler(handleDuplicateType(entity.getName(), result));

    return result;
  }

  private Future<Void> validateNoteTypeLimit(OkapiParams params) {
    return
      CompositeFuture.all(
        configuration.getInt(NOTE_TYPES_NUMBER_LIMIT_PROP, defaultNoteTypeLimit, params),
        repository.count(params.getTenant()))
      .compose(compositeFuture -> {
        int limit = compositeFuture.resultAt(0);
        long count = compositeFuture.resultAt(1);

        return (count >= limit)
          ? Future.failedFuture(new BadRequestException("Maximum number of note types allowed is " + limit))
          : Future.succeededFuture();
      });
  }

  @Override
  public Future<Void> update(String id, NoteType entity, String tenantId) {
    entity.setId(id); // undesirable modification of input entity, but probably safe in this case

    Future<Boolean> duplFuture = future();
    repository.update(entity, tenantId).setHandler(handleDuplicateType(entity.getName(), duplFuture));

    return duplFuture.compose(updated -> failIfNotFound(updated, id));
  }

  @Override
  public Future<Void> delete(String id, String tenantId) {
    return repository.delete(id, tenantId)
            .compose(deleted -> failIfNotFound(deleted, id));
  }

  private <T> Handler<AsyncResult<T>> handleDuplicateType(String type, Future<T> result) {
    return ar -> {
      // TO BE REFACTORED:
      // There have to be separate exceptions per different DB errors: unique constraint/foreign key/invalid UUID
      // These exceptions should be thrown by repository
      if (ar.succeeded()) {
        result.complete(ar.result());
      } else {
        Throwable t = ar.cause();

        String msg = PgExceptionUtil.badRequestMessage(t);

        if (msg != null) {
          BadRequestException bre;
          if (msg.contains("duplicate key value violates unique constraint")) {
            bre = new BadRequestException("Note type '" + type + "' already exists");
          } else {
            bre = new BadRequestException(t);
          }

          result.fail(bre);
        } else {
          result.fail(t);
        }
      }
    };
  }

  private Future<Void> failIfNotFound(boolean found, String entityId) {
    return found ? succeededFuture() : failedFuture(Exceptions.notFound(NoteType.class, entityId));
  }

}
