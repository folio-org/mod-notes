package org.folio.notes.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.folio.notes.config.properties.NoteTypesProperties;
import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;
import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.notes.domain.mapper.NoteTypesMapper;
import org.folio.notes.domain.repository.NoteTypesRepository;
import org.folio.notes.domain.repository.NoteTypeTuple;
import org.folio.notes.exception.NoteTypeNotFoundException;
import org.folio.notes.exception.NoteTypesLimitReached;
import org.folio.notes.service.ConfigurationService;
import org.folio.notes.service.NoteTypesService;
import org.folio.spring.data.OffsetRequest;

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
    var noteTypeIds = noteTypes.getContent().stream()
      .map(NoteTypeEntity::getId)
      .collect(Collectors.toList());
    var usageNotes = getNoteUsage(noteTypeIds);
    return mapper.toDtoCollection(noteTypes, usageNotes);
  }

  @Override
  public NoteType getNoteType(UUID id) {
    var noteUsage = getNoteUsage(Collections.singletonList(id));
    return repository.findById(id)
      .map(mapper::toDto)
      .map(noteType-> mapper.populateUsageNote(noteType, noteUsage))
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

  private Map<String, Long> getNoteUsage(List<UUID> noteTypeIds) {
    return repository.findNoteUsage(noteTypeIds).stream()
      .collect(Collectors.groupingBy(NoteTypeTuple::getNoteTypeId, Collectors.counting()));
  }

}
