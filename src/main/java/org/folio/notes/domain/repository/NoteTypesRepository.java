package org.folio.notes.domain.repository;

import java.util.List;
import java.util.UUID;

import org.folio.notes.domain.entity.projection.NoteTypeCount;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.spring.cql.JpaCqlRepository;

public interface NoteTypesRepository extends JpaCqlRepository<NoteTypeEntity, UUID> {

  @Query("SELECT n.type.id as typeId, COUNT(n.id) as count FROM NoteEntity AS n WHERE n.type.id IN (:noteTypeIds) GROUP BY n.type.id")
  List<NoteTypeCount> findNoteUsage(@Param("noteTypeIds") List<UUID> noteTypeIds);

}
