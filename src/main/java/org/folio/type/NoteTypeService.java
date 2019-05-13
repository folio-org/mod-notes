package org.folio.type;

import java.util.List;

import io.vertx.core.Future;

import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeCollection;
import org.folio.util.OkapiParams;

public interface NoteTypeService {

  Future<NoteTypeCollection> findByQuery(String query, int offset, int limit, String lang, String tenantId);

  Future<NoteType> findById(String id, String tenantId);

  Future<List<NoteType>> findByIds(List<String> ids, String tenantId);

  Future<NoteType> save(NoteType entity, OkapiParams params);

  Future<Void> update(String id, NoteType entity, OkapiParams params);

  Future<Void> delete(String id, String tenantId);

}
