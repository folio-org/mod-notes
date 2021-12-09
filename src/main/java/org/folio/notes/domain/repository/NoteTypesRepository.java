package org.folio.notes.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.spring.cql.JpaCqlRepository;

public interface NoteTypesRepository extends JpaCqlRepository<NoteTypeEntity, UUID> {

  @Query(value = "SELECT CAST(n.id AS varchar(50)) as noteId, CAST(n.type_id AS varchar(50)) as noteTypeId " +
    "FROM note n WHERE n.type_id IN (:noteTypeIds)", nativeQuery = true)
  List<NoteTypeTuple> findNoteUsage(@Param("noteTypeIds") List<UUID> noteTypeIds);
}
