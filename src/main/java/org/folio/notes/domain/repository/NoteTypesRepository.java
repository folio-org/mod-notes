package org.folio.notes.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.notes.domain.entity.projection.NoteTypeCount;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteTypesRepository extends JpaCqlRepository<NoteTypeEntity, UUID> {

  @Query(name = NoteTypeEntity.FIND_ALL_NOTE_TYPES_USAGES_QUERY_NAME)
  List<NoteTypeCount> findAllNoteTypesUsages();

  @Query(name = NoteTypeEntity.FIND_NOTE_TYPE_USAGE_QUERY_NAME)
  Optional<NoteTypeCount> findNoteTypeUsage(@Param("noteTypeId") UUID noteTypeId);

}
