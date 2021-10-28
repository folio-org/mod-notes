package org.folio.notes.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.folio.notes.domain.entity.NoteEntity;

@Repository
public interface NoteRepository extends JpaRepository<NoteEntity, UUID> {

}
