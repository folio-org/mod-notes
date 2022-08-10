package org.folio.notes.service.impl;

import static org.folio.notes.domain.repository.NoteRepository.contentLike;
import static org.folio.notes.domain.repository.NoteRepository.domainEq;
import static org.folio.notes.domain.repository.NoteRepository.linkIs;
import static org.folio.notes.domain.repository.NoteRepository.linkIsNot;
import static org.folio.notes.domain.repository.NoteRepository.typeNameIn;
import static org.springframework.data.jpa.domain.Specification.where;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.folio.notes.domain.dto.LinkStatus;
import org.folio.notes.domain.dto.LinkStatusFilter;
import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.dto.NoteCollection;
import org.folio.notes.domain.dto.NoteLinkUpdate;
import org.folio.notes.domain.dto.NoteLinkUpdateCollection;
import org.folio.notes.domain.dto.NotesOrderBy;
import org.folio.notes.domain.dto.OrderDirection;
import org.folio.notes.domain.entity.AuditableEntity_;
import org.folio.notes.domain.entity.LinkEntity;
import org.folio.notes.domain.entity.NoteEntity;
import org.folio.notes.domain.entity.NoteEntity_;
import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.notes.domain.mapper.NoteCollectionMapper;
import org.folio.notes.domain.mapper.NotesMapper;
import org.folio.notes.domain.repository.LinkRepository;
import org.folio.notes.domain.repository.NoteRepository;
import org.folio.notes.exception.NoteNotFoundException;
import org.folio.notes.service.NotesService;
import org.folio.notes.util.HtmlSanitizer;
import org.folio.spring.data.OffsetRequest;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class NotesServiceImpl implements NotesService {

  private static final Sort.Order DEFAULT_FIELD_ORDER = Sort.Order.asc(NoteEntity_.INDEXED_CONTENT);
  private static final Map<NotesOrderBy, Sort.Direction> DEFAULT_SORT_DIRECTION;
  private static final Map<NotesOrderBy, String> DTO_TO_ENTITY_SORT;

  static {
    DEFAULT_SORT_DIRECTION =
      Map.of(
        NotesOrderBy.TITLE, Sort.Direction.ASC,
        NotesOrderBy.CONTENT, Sort.Direction.ASC,
        NotesOrderBy.NOTETYPE, Sort.Direction.ASC,
        NotesOrderBy.UPDATEDDATE, Sort.Direction.DESC
      );

    DTO_TO_ENTITY_SORT =
      Map.of(
        NotesOrderBy.TITLE, NoteEntity_.TITLE,
        NotesOrderBy.CONTENT, NoteEntity_.INDEXED_CONTENT,
        NotesOrderBy.NOTETYPE, NoteEntity_.TYPE,
        NotesOrderBy.UPDATEDDATE, AuditableEntity_.UPDATED_DATE
      );
  }

  private final NoteRepository noteRepository;
  private final LinkRepository linkRepository;
  private final NotesMapper notesMapper;
  private final NoteCollectionMapper noteCollectionMapper;
  private final HtmlSanitizer sanitizer;

  @Override
  public NoteCollection getNoteCollection(String query, Integer offset, Integer limit) {
    var noteEntities = noteRepository.findByCQL(query, OffsetRequest.of(offset, limit));
    return noteCollectionMapper.toDtoCollection(noteEntities);
  }

  @Override
  public NoteCollection getNoteCollection(String domain, String objectType, String objectId, String search,
                                          List<String> noteTypes, LinkStatusFilter status, NotesOrderBy orderBy,
                                          OrderDirection order, Integer offset,
                                          Integer limit) {
    Specification<NoteEntity> spec = domainEq(domain);

    switch (status) {
      case ASSIGNED:
        spec = spec.and(linkIs(objectId, objectType));
        break;
      case UNASSIGNED:
        spec = spec.and(linkIsNot(objectId, objectType));
        break;
      default:
        break;
    }

    if (StringUtils.isNotBlank(search)) {
      spec = spec.and(contentLike(search));
    }

    if (!CollectionUtils.isEmpty(noteTypes)) {
      spec = spec.and(typeNameIn(noteTypes));
    }

    Sort sort = getSort(orderBy, order);

    Page<NoteEntity> t = noteRepository.findAll(where(spec), OffsetRequest.of(offset, limit, sort));
    return noteCollectionMapper.toDtoCollection(t);
  }

  @Override
  public Note getNote(UUID id) {
    return notesMapper.toDto(noteRepository.findById(id).orElseThrow(() -> notFoundException(id)));
  }

  @Transactional
  @Override
  public Note createNote(Note note) {
    return notesMapper.toDto(saveNote(note, notesMapper::toEntity));
  }

  @Transactional
  @Override
  public void updateLinks(String objectType, String objectId, NoteLinkUpdateCollection noteLinkUpdateCollection) {
    var linkEntity = fetchOrSaveLink(objectId, objectType);

    var linkUpdates = noteLinkUpdateCollection.getNotes();
    var noteIds = linkUpdates.stream()
      .map(NoteLinkUpdate::getId)
      .collect(Collectors.toList());

    var noteEntities = noteRepository.findAllById(noteIds);
    for (NoteEntity noteEntity : noteEntities) {
      var linkStatusChange = linkUpdates.stream()
        .filter(noteLinkUpdate -> noteLinkUpdate.getId().equals(noteEntity.getId()))
        .findFirst()
        .map(NoteLinkUpdate::getStatus)
        .orElseThrow();

      if (linkStatusChange == LinkStatus.UNASSIGNED) {
        noteEntity.deleteLink(linkEntity);
      } else {
        noteEntity.addLink(linkEntity);
      }
      if (noteEntity.getLinks().isEmpty()) {
        noteRepository.delete(noteEntity);
      } else {
        noteRepository.save(noteEntity);
      }
    }
  }

  @Transactional
  @Override
  public void updateNote(UUID id, Note dto) {
    if (dto.getLinks().isEmpty()) {
      deleteNote(id);
    } else {
      noteRepository.findById(id)
        .ifPresentOrElse(entity -> saveNote(dto, noteMapFunction(dto, entity)), throwNotFoundById(id));
    }
  }

  @Transactional
  @Override
  public void deleteNote(UUID id) {
    noteRepository.findById(id)
      .ifPresentOrElse(entity -> noteRepository.deleteById(id), throwNotFoundById(id));
  }

  private Sort getSort(NotesOrderBy orderBy, OrderDirection order) {
    var sort = Sort.unsorted();
    if (orderBy != null) {
      var orderedField = DTO_TO_ENTITY_SORT.get(orderBy);
      var direction = getOrderDirection(orderBy, order);
      var fieldOrder = new Sort.Order(direction, orderedField);
      sort = Sort.by(fieldOrder, DEFAULT_FIELD_ORDER);
    }
    return sort;
  }

  private Sort.Direction getOrderDirection(NotesOrderBy orderBy, OrderDirection order) {
    if (order != null) {
      return Sort.Direction.fromString(order.getValue());
    } else {
      return DEFAULT_SORT_DIRECTION.get(orderBy);
    }
  }

  private Function<Note, NoteEntity> noteMapFunction(Note dto, NoteEntity noteEntity) {
    if (!dto.getTypeId().equals(noteEntity.getType().getId())) {
      noteEntity.setType(new NoteTypeEntity());
    }

    return noteDto -> notesMapper.updateNote(noteDto, noteEntity);
  }

  private NoteEntity saveNote(Note dto, Function<Note, NoteEntity> mapFunction) {
    var noteEntity = mapFunction.apply(dto);
    manageNoteLinks(noteEntity);
    noteEntity.setContent(sanitizer.sanitize(noteEntity.getContent()));
    return noteRepository.save(noteEntity);
  }

  private void manageNoteLinks(NoteEntity noteEntity) {
    if (noteEntity.getLinks() != null) {
      var linkEntities = noteEntity.getLinks().stream()
        .map(this::fetchOrSaveLink)
        .collect(Collectors.toSet());
      noteEntity.setLinks(linkEntities);
    }
  }

  private LinkEntity fetchOrSaveLink(String objectId, String objectType) {
    LinkEntity linkEntity = new LinkEntity();
    linkEntity.setObjectId(objectId);
    linkEntity.setObjectType(objectType);
    return fetchOrSaveLink(linkEntity);
  }

  private LinkEntity fetchOrSaveLink(LinkEntity linkEntity) {
    var linkEntityExample = Example.of(linkEntity);
    var one = linkRepository.findOne(linkEntityExample);
    return one.orElseGet(() -> linkRepository.save(linkEntity));
  }

  private NoteNotFoundException notFoundException(UUID id) {
    return new NoteNotFoundException(id);
  }

  private Runnable throwNotFoundById(UUID id) {
    return () -> {
      throw notFoundException(id);
    };
  }
}
