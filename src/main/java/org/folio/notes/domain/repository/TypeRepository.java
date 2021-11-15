package org.folio.notes.domain.repository;

import java.util.UUID;

import org.folio.notes.domain.entity.TypeEntity;
import org.folio.spring.cql.JpaCqlRepository;

public interface TypeRepository extends JpaCqlRepository<TypeEntity, UUID> {

}
