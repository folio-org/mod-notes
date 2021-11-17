package org.folio.notes.service.impl;

import java.util.UUID;
import javax.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;
import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.notes.domain.repository.NoteTypesRepository;
import org.folio.notes.exception.NoteTypesLimitReached;
import org.folio.notes.mapper.NoteTypesMapper;
import org.folio.notes.service.ConfigurationService;
import org.folio.notes.service.NoteTypesService;
import org.folio.spring.data.OffsetRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteTypesServiceImpl implements NoteTypesService {

  @Value("${folio.notes.types.default.name}")
  private String defaultNoteTypeName;

  @Value("${folio.notes.types.default.limit}")
  private String defaultNoteTypeLimit;

  private static final String NOTE_TYPE_LIMIT_CONFIG = "note-type-limit";

  private final ConfigurationService configurationService;
  private final NoteTypesRepository repository;
  private final NoteTypesMapper mapper;

  @Override
  public NoteTypeCollection getNoteTypesCollection(String query, Integer offset, Integer limit) {
    return mapper.toDtoCollection(repository.findByCQL(query, OffsetRequest.of(offset, limit)));
  }

  @Override
  public NoteType getById(UUID id) {
    return repository.findById(id)
      .map(mapper::toDto)
      .orElseThrow(() -> notFound(id));
  }

  @Override
  public NoteType createNoteType(NoteType noteType) {
    validateNoteTypeLimit();

    return mapper.toDto(repository.save(mapper.toEntity(noteType)));
  }

  @Override
  public void updateNoteType(UUID id, NoteType entity) {
    repository.findById(id)
      .ifPresentOrElse(existedEntity -> repository.save(mapper.updateNoteType(entity, existedEntity)), throwNotFoundById(id));
  }

  @Override
  public void removeNoteTypeById(UUID id) {
    repository.findById(id)
      .ifPresentOrElse(repository::delete, throwNotFoundById(id));
  }

  @Override
  public void populateDefaultType() {
    if (repository.count() == 0) {
      NoteTypeEntity noteType = new NoteTypeEntity();
      noteType.setName(defaultNoteTypeName);

      NoteTypeEntity savedType = repository.save(noteType);
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
    int limit = Integer.parseInt(configurationService.getConfigValue(NOTE_TYPE_LIMIT_CONFIG, defaultNoteTypeLimit));

    if (repository.count() >= limit) {
      throw new NoteTypesLimitReached(limit);
    }
  }
}
