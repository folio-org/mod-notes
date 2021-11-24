package org.folio.notes.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import org.folio.notes.domain.entity.LinkEntity;

public interface LinkRepository extends JpaRepository<LinkEntity, UUID> {

  Optional<LinkEntity> findByObjectIdAndObjectType(String objectId, String objectType);

}
