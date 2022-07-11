package org.folio.notes.service.impl;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.notes.config.properties.NoteTypesProperties;
import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;
import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.notes.domain.entity.projection.NoteTypeCount;
import org.folio.notes.domain.mapper.NoteTypesMapper;
import org.folio.notes.domain.repository.NoteTypesRepository;
import org.folio.notes.exception.NoteTypeNotFoundException;
import org.folio.notes.exception.NoteTypesLimitReached;
import org.folio.notes.service.ConfigurationService;
import org.folio.notes.service.NoteTypesService;
import org.folio.spring.data.OffsetRequest;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteTypesServiceImpl implements NoteTypesService {

  private static final String NOTE_TYPE_LIMIT_CONFIG = "note-type-limit";

  private final ConfigurationService configurationService;
  private final NoteTypesRepository repository;
  private final NoteTypesMapper mapper;
  private final NoteTypesProperties noteTypesProperties;

  @Override
  public NoteTypeCollection getNoteTypeCollection(String query, Integer offset, Integer limit) {
    var noteTypes = repository.findByCQL(query, OffsetRequest.of(offset, limit));
    var noteTypeUsage = getAllNoteTypeUsage();
    return mapper.toDtoCollection(noteTypes, noteTypeUsage);
  }

  @Override
  public NoteType getNoteType(UUID id) {
    return repository.findById(id)
      .map(mapper::toDto)
      .map(noteType -> noteType.usage(mapper.getNoteTypeUsage(id, getNoteTypeUsage(id))))
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
      .ifPresentOrElse(existedEntity -> repository.save(mapper.updateNoteType(entity, existedEntity)),
        throwNotFoundById(id));
  }

  @Override
  public void removeNoteType(UUID id) {
    repository.findById(id)
      .ifPresentOrElse(repository::delete, throwNotFoundById(id));
  }

  @Override
  public void populateDefaultType() {
    if (repository.count() == 0) {
      NoteTypeEntity noteType = new NoteTypeEntity();
      noteType.setName(noteTypesProperties.getDefaults().getName());

      NoteTypeEntity savedType = repository.save(noteType);
      log.info("Added default note type '{}'", savedType.getName());
    }
  }

  private Runnable throwNotFoundById(UUID id) {
    return () -> {
      throw notFound(id);
    };
  }

  private NoteTypeNotFoundException notFound(UUID id) {
    return new NoteTypeNotFoundException(id);
  }

  private void validateNoteTypeLimit() {
    var defaultLimit = noteTypesProperties.getDefaults().getLimit();
    var limit = Integer.parseInt(configurationService.getConfigValue(NOTE_TYPE_LIMIT_CONFIG, defaultLimit));

    if (repository.count() >= limit) {
      throw new NoteTypesLimitReached(limit);
    }
  }

  private Map<UUID, Boolean> getAllNoteTypeUsage() {
    return repository.findAllNoteTypesUsages().stream()
      .collect(Collectors.toMap(NoteTypeCount::getTypeId, NoteTypeCount::getIsAssigned));
  }

  private Map<UUID, Boolean> getNoteTypeUsage(UUID noteTypeId) {
    return repository.findNoteTypeUsage(noteTypeId).stream()
      .collect(Collectors.toMap(NoteTypeCount::getTypeId, NoteTypeCount::getIsAssigned));
  }

}
