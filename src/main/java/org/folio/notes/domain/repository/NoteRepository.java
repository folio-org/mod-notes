package org.folio.notes.domain.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import java.util.UUID;
import org.folio.notes.domain.entity.BaseEntity_;
import org.folio.notes.domain.entity.LinkEntity;
import org.folio.notes.domain.entity.LinkEntity_;
import org.folio.notes.domain.entity.NoteEntity;
import org.folio.notes.domain.entity.NoteEntity_;
import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.notes.domain.entity.NoteTypeEntity_;
import org.folio.spring.cql.JpaCqlRepository;
import org.hibernate.query.sqm.internal.SqmCriteriaNodeBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface NoteRepository extends JpaCqlRepository<NoteEntity, UUID>, JpaSpecificationExecutor<NoteEntity> {

  String NOTE_COLLECTION_GRAPH = "note-collection-graph";

  static Specification<NoteEntity> domainEq(String domain) {
    return (root, query, cb) -> cb.equal(root.get(NoteEntity_.domain), domain);
  }

  static Specification<NoteEntity> contentLike(String text) {
    return (root, query, cb) ->
      ((SqmCriteriaNodeBuilder) cb).ilike(root.get(NoteEntity_.indexedContent), "%" + text + "%");
  }

  static Specification<NoteEntity> typeNameIn(List<String> typeNames) {
    return (root, query, cb) -> {
      Join<NoteEntity, NoteTypeEntity> typeJoin = root.join(NoteEntity_.type);
      return typeJoin.get(NoteTypeEntity_.name).in(typeNames);
    };
  }

  static Specification<NoteEntity> linkIs(String objectId, String objectType) {
    return (root, query, cb) -> linkExists(objectId, objectType, root, query, cb);
  }

  static Specification<NoteEntity> linkIsNot(String objectId, String objectType) {
    return (root, query, cb) -> cb.not(linkExists(objectId, objectType, root, query, cb));
  }

  private static Predicate linkExists(String objectId, String objectType, Root<NoteEntity> root, CriteriaQuery<?> query,
                                      CriteriaBuilder cb) {
    Subquery<LinkEntity> subQuery = query.subquery(LinkEntity.class);
    Root<LinkEntity> subRoot = subQuery.from(LinkEntity.class);
    SetJoin<LinkEntity, NoteEntity> subJoin = subRoot.join(LinkEntity_.notes, JoinType.INNER);

    Predicate equalNoteId = cb.equal(subJoin.get(BaseEntity_.id), root.get(BaseEntity_.id));
    Predicate equalObjectId = cb.equal(subRoot.get(LinkEntity_.objectId), objectId);
    Predicate equalObjectType = cb.equal(subRoot.get(LinkEntity_.objectType), objectType);

    subQuery.select(subRoot).where(equalNoteId, equalObjectId, equalObjectType);

    return cb.exists(subQuery);
  }

  @NonNull
  @EntityGraph(value = NOTE_COLLECTION_GRAPH, type = EntityGraph.EntityGraphType.LOAD)
  Page<NoteEntity> findAll(@Nullable Specification<NoteEntity> spec, @NonNull Pageable pageable);
}

