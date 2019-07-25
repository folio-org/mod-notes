package org.folio.type;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.List;
import java.util.function.Function;

import javax.ws.rs.BadRequestException;

import com.rits.cloning.Cloner;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.folio.common.OkapiParams;
import org.folio.config.Configuration;
import org.folio.db.exc.DbExcUtils;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeCollection;
import org.folio.service.exc.ServiceExceptions;
import org.folio.userlookup.UserLookUp;

@Component
public class NoteTypeServiceImpl implements NoteTypeService {

  private static final String NOTE_TYPES_NUMBER_LIMIT_PROP = "note.types.number.limit";

  @Autowired
  private NoteTypeRepository repository;
  @Autowired
  private Configuration configuration;
  @Autowired @Qualifier("restModelCloner")
  private Cloner cloner;
  @Value("${note.types.number.limit.default}")
  private int defaultNoteTypeLimit;

  @Override
  public Future<NoteTypeCollection> findByQuery(String query, int offset, int limit, String lang, String tenantId) {
    return repository.findByQuery(query, offset, limit, tenantId);
  }

  @Override
  public Future<NoteType> findById(String id, String tenantId) {
    return repository.findById(id, tenantId)
            .map(noteType -> noteType.orElseThrow(() -> ServiceExceptions.notFound(NoteType.class, id)));
  }

  @Override
  public Future<List<NoteType>> findByIds(List<String> ids, String tenantId) {
    return repository.findByIds(ids, tenantId);
  }

  @Override
  public Future<NoteType> save(NoteType entity, OkapiParams params) {
    Future<Void> validation = validateNoteTypeLimit(params);

    return validation.compose(v -> populateCreator(entity, params))
      .compose(type -> repository.save(type, params.getTenant()))
      .recover(handleDuplicateType(entity));
  }

  private <T> Function<Throwable, Future<T>> handleDuplicateType(NoteType entity) {
    return t -> {
      Throwable exc = t;

      if (DbExcUtils.isUniqueViolation(t)) {
        exc = new BadRequestException("Note type '" + entity.getName() + "' already exists");
      }

      return Future.failedFuture(exc);
    };
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
          ? failedFuture(new BadRequestException("Maximum number of note types allowed is " + limit))
          : Future.succeededFuture();
      });
  }

  @Override
  public Future<Void> update(String id, NoteType entity, OkapiParams params) {
    NoteType updating = cloner.deepClone(entity);
    updating.setId(id);

    return populateUpdater(updating, params)
      .compose(type -> repository.update(type, params.getTenant()))
      .compose(updated -> failIfNotFound(updated, id))
      .recover(handleDuplicateType(updating));
  }

  private Future<NoteType> populateCreator(NoteType entity, OkapiParams params) {
    return UserLookUp.getUserInfo(params.getHeadersAsMap()).map(user -> {
      if (entity.getMetadata() == null) {
        return entity;
      }

      NoteType result = cloner.deepClone(entity);
      result.getMetadata().setCreatedByUsername(user.getUserName());

      return result;
    });
  }

  private Future<NoteType> populateUpdater(NoteType entity, OkapiParams params) {
    return UserLookUp.getUserInfo(params.getHeadersAsMap()).map(user -> {
      if (entity.getMetadata() == null) {
        return entity;
      }

      NoteType result = cloner.deepClone(entity);
      result.getMetadata().setUpdatedByUsername(user.getUserName());

      return result;
    });
  }

  @Override
  public Future<Void> delete(String id, String tenantId) {
    return repository.delete(id, tenantId)
            .compose(deleted -> failIfNotFound(deleted, id))
            .recover(handleReferencedType());
  }

  private Function<Throwable, Future<Void>> handleReferencedType() {
    return t -> {
      Throwable exc = t;

      if (DbExcUtils.isFKViolation(t)) {
        exc = new BadRequestException("Note type is referenced by note(s) and cannot be deleted");
      }

      return Future.failedFuture(exc);
    };
  }

  private Future<Void> failIfNotFound(boolean found, String entityId) {
    return found ? succeededFuture() : failedFuture(ServiceExceptions.notFound(NoteType.class, entityId));
  }

}
