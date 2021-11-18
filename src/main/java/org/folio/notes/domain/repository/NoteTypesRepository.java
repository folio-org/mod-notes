package org.folio.notes.domain.repository;

import java.util.UUID;

import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.spring.cql.JpaCqlRepository;

public interface NoteTypesRepository extends JpaCqlRepository<NoteTypeEntity, UUID> {

}
