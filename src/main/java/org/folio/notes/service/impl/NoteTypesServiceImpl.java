package org.folio.notes.service.impl;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.folio.notes.config.properties.NoteTypesProperties;
import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;
import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.notes.domain.entity.projection.NoteTypeCount;
import org.folio.notes.domain.mapper.NoteTypesMapper;
import org.folio.notes.domain.repository.NoteTypesRepository;
import org.folio.notes.exception.NoteTypeNotFoundException;
import org.folio.notes.exception.NoteTypesLimitReached;
import org.folio.notes.service.NoteTypesService;
import org.folio.spring.data.OffsetRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteTypesServiceImpl implements NoteTypesService {

  private final NoteTypesRepository repository;
  private final NoteTypesMapper mapper;
  private final NoteTypesProperties noteTypesProperties;

  @Override
  public NoteTypeCollection getNoteTypeCollection(String query, Integer offset, Integer limit) {
    log.debug("getNoteTypeCollection:: trying to load note types by query: {}, offset: {}, limit: {}",
      query, offset, limit);
    var noteTypes = repository.findByCql(query, OffsetRequest.of(offset, limit));
    var noteTypeUsage = getAllNoteTypeUsage();
    log.info("getNoteTypeCollection:: note types loaded by query: {}, offset: {}, limit: {}",
      query, offset, limit);
    return mapper.toDtoCollection(noteTypes, noteTypeUsage);
  }

  @Override
  public NoteType getNoteType(UUID id) {
    log.debug("getNoteType:: trying to load note type by id: {}", id);
    return repository.findById(id)
      .map(entity -> {
        log.info("getNoteType:: note type loaded by id: {}", id);
        return mapper.toDto(entity);
      })
      .map(noteType -> noteType.usage(mapper.getNoteTypeUsage(id, getNoteTypeUsage(id))))
      .orElseThrow(() -> {
        NoteTypeNotFoundException noteTypeNotFoundException = notFound(id);
        log.warn("getNoteType:: Error loading note type with id: {}: {}", id, noteTypeNotFoundException.getMessage());
        return noteTypeNotFoundException;
      });
  }

  @Override
  public NoteType createNoteType(NoteType noteType) {
    log.debug("createNoteType:: trying to create note type with name: {}", noteType.getName());
    validateNoteTypeLimit();
    NoteTypeEntity entity = repository.save(mapper.toEntity(noteType));
    log.info("createNoteType:: created note type with name: {}", entity.getName());
    return mapper.toDto(entity);
  }

  @Override
  public void updateNoteType(UUID id, NoteType entity) {
    log.debug("updateNoteType:: trying to update note type with id: {}", id);
    repository.findById(id).ifPresentOrElse(existedEntity -> {
      repository.save(mapper.updateNoteType(entity, existedEntity));
      log.info("updateNoteType:: updated note type with id: {}", id);
    },
        throwNotFoundById(id, "updateNoteType"));
  }

  @Override
  public void removeNoteType(UUID id) {
    log.debug("removeNoteType:: trying to remove note type with id: {}", id);
    repository.findById(id)
      .ifPresentOrElse(entity -> {
        repository.delete(entity);
        log.info("removeNoteType:: removed note type with id: {}", id);
      }, throwNotFoundById(id, "removeNoteType"));
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

  private Runnable throwNotFoundById(UUID id, String methodName) {
    return () -> {
      NoteTypeNotFoundException noteTypeNotFoundException = notFound(id);
      log.warn(String.format("%s:: error loading note type with id: {}", methodName), id);
      throw noteTypeNotFoundException;
    };
  }

  private NoteTypeNotFoundException notFound(UUID id) {
    return new NoteTypeNotFoundException(id);
  }

  private void validateNoteTypeLimit() {
    var limit = noteTypesProperties.getDefaults().getLimit();

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
