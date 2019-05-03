package org.folio.type;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Future;

import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeCollection;

public interface NoteTypeRepository {

  Future<NoteTypeCollection> findByQuery(String query, int offset, int limit, String tenantId);

  Future<Optional<NoteType>> findById(String id, String tenantId);

  Future<List<NoteType>> findByIds(List<String> ids, String tenantId);

  Future<Long> count(String tenantId);

  Future<NoteType> save(NoteType entity, String tenantId);

  Future<Boolean> update(NoteType entity, String tenantId);

  Future<Boolean> delete(String id, String tenantId);
}
