package org.folio.notes.sevice.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.persistence.EntityNotFoundException;
import javax.ws.rs.BadRequestException;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;
import org.folio.notes.domain.repository.TypeRepository;
import org.folio.notes.mapper.TypeMapper;
import org.folio.notes.sevice.TypeService;
import org.folio.spring.data.OffsetRequest;

@Service
@RequiredArgsConstructor
public class TypeServiceImpl implements TypeService {

  @Value("${folio.notes.types.number.limit.default}")
  private final int noteTypesNumberLimit;

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
      throw new BadRequestException("Maximum number of note types allowed is " + noteTypesNumberLimit);
    }
  }
}
