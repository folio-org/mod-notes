package org.folio.notes.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import org.folio.notes.domain.entity.TypeEntity;

public interface TypeRepository extends JpaRepository<TypeEntity, UUID> {

}
