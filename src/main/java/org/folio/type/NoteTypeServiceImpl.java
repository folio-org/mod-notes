package org.folio.type;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Future;
import org.springframework.stereotype.Component;

import org.folio.rest.jaxrs.model.NoteType;

@Component
public class NoteTypeServiceImpl implements NoteTypeService {

  @Override
  public Future<List<NoteType>> findByQuery(String query, int offset, int limit, String lang, String tenantId) {
    return null;
  }

  @Override
  public Future<Optional<NoteType>> findById(String id, String tenantId) {
    return null;
  }

  @Override
  public Future<List<NoteType>> findByIds(List<String> ids, String tenantId) {
    return null;
  }

  @Override
  public Future<NoteType> save(NoteType entity, String tenantId) {
    return null;
  }

  @Override
  public Future<Boolean> delete(String id, String tenantId) {
    return null;
  }

}
