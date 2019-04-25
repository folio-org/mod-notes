package org.folio.type;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.List;

import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeCollection;
import org.folio.util.exc.Exceptions;

@Component
public class NoteTypeServiceImpl implements NoteTypeService {

  @Autowired
  private NoteTypeRepository repository;

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
  public Future<NoteType> save(NoteType entity, String tenantId) {
    // TODO (Dima Tkachenko): call mod config to test for max number of types
    return repository.save(entity, tenantId);
  }

  @Override
  public Future<Void> update(String id, NoteType entity, String tenantId) {
    entity.setId(id); // undesirable modification of input entity, but probably safe in this case

    return repository.update(entity, tenantId)
            .compose(updated -> failIfNotFound(updated, id));
  }

  @Override
  public Future<Void> delete(String id, String tenantId) {
    return repository.delete(id, tenantId)
            .compose(deleted -> failIfNotFound(deleted, id));
  }

  private Future<Void> failIfNotFound(boolean found, String entityId) {
    return found ? succeededFuture() : failedFuture(Exceptions.notFound(NoteType.class, entityId));
  }

}
