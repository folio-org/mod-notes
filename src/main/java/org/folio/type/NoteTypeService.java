package org.folio.type;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Future;

import org.folio.rest.jaxrs.model.NoteType;

public interface NoteTypeService {

  Future<List<NoteType>> findByQuery(String query, int offset, int limit, String lang, String tenantId);

  Future<Optional<NoteType>> findById(String id, String tenantId);

  Future<List<NoteType>> findByIds(List<String> ids, String tenantId);

  Future<NoteType> save(NoteType entity, String tenantId);

  Future<Boolean> delete(String id, String tenantId);

}
