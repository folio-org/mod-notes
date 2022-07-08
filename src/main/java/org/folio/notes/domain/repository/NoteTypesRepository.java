package org.folio.notes.domain.repository;

import java.util.List;
import java.util.UUID;

import org.folio.notes.domain.entity.projection.NoteTypeCount;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.spring.cql.JpaCqlRepository;

public interface NoteTypesRepository extends JpaCqlRepository<NoteTypeEntity, UUID> {

  @Query("SELECT t.id as typeId, EXISTS (SELECT n.type.id FROM NoteEntity AS n WHERE n.type.id = t.id) as isAssigned FROM NoteTypeEntity as t WHERE t.id IN (:noteTypeIds)")
  List<NoteTypeCount> findNoteUsage(@Param("noteTypeIds") List<UUID> noteTypeIds);

}
