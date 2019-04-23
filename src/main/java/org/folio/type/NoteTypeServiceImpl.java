package org.folio.type;

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
    return null;
  }

  @Override
  public Future<String> delete(String id, String tenantId) {
    return repository.delete(id, tenantId)
              .map(deleted -> {
                if (deleted) {
                  return id;
                } else {
                  throw Exceptions.notFound(NoteType.class, id);
                }
              });
  }

}
