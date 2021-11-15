package org.folio.notes.service.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.folio.notes.domain.dto.Metadata;
import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;
import org.folio.notes.domain.entity.TypeEntity;
import org.folio.notes.domain.repository.TypeRepository;
import org.folio.notes.exception.TypesLimitReached;
import org.folio.notes.mapper.TypeMapper;
import org.folio.notes.service.TypeService;
import org.folio.spring.data.OffsetRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class TypeServiceImpl implements TypeService {

  @Value("${folio.notes.types.default.name}")
  private String defaultNoteTypeName;

  @Value("${folio.notes.types.number.limit.default}")
  private Integer noteTypesNumberLimit;

  private final TypeRepository repository;
  private final TypeMapper mapper;

  @Override
  public NoteTypeCollection fetchTypeCollection(String query, Integer offset, Integer limit) {
    return mapper.toDtoCollection(repository.findByCQL(query, OffsetRequest.of(offset, limit)));
  }

  @Override
  public NoteType fetchById(UUID id) {
    return repository.findById(id)
      .map(mapper::toDto)
      .orElseThrow(() -> notFound(id));
  }

  @Override
  public List<NoteType> fetchByIds(List<UUID> ids) {
    return repository.findAllById(ids).stream()
      .map(mapper::toDto)
      .collect(Collectors.toList());
  }

  @Override
  public NoteType createType(NoteType noteType) {
    validateNoteTypeLimit();

    return mapper.toDto(repository.save(mapper.toEntity(noteType)));
  }

  @Override
  public void updateType(UUID id, NoteType entity) {
    repository.findById(id)
      .ifPresentOrElse(existedEntity -> repository.save(mapper.updateNoteType(entity, existedEntity)), throwNotFoundById(id));
  }

  @Override
  public void removeTypeById(UUID id) {
    repository.findById(id)
      .ifPresentOrElse(repository::delete, throwNotFoundById(id));
  }

  @Override
  public void populateDefaultType() {
    if (repository.count() == 0) {
      NoteType type = new NoteType()
        .name(defaultNoteTypeName)
        .metadata(new Metadata()
          .createdDate(OffsetDateTime.now())
          .updatedDate(OffsetDateTime.now()));

      TypeEntity savedType = repository.save(mapper.toEntity(type));
      log.info("Added default note type '{}'", savedType.getName());
    }
  }

  private Runnable throwNotFoundById(UUID id) {
    return () -> {
      throw notFound(id);
    };
  }

  private EntityNotFoundException notFound(UUID id) {
    return new EntityNotFoundException(String.format("Note type with id [%s] was not found", id));
  }

  private void validateNoteTypeLimit() {
    if (repository.count() >= noteTypesNumberLimit) {
      throw new TypesLimitReached(noteTypesNumberLimit);
    }
  }
}
