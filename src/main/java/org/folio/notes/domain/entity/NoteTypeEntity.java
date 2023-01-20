package org.folio.notes.domain.entity;

import static org.folio.notes.domain.entity.NoteTypeEntity.FIND_ALL_NOTE_TYPES_USAGES_QUERY;
import static org.folio.notes.domain.entity.NoteTypeEntity.FIND_ALL_NOTE_TYPES_USAGES_QUERY_NAME;
import static org.folio.notes.domain.entity.NoteTypeEntity.FIND_NOTE_TYPE_USAGE_QUERY;
import static org.folio.notes.domain.entity.NoteTypeEntity.FIND_NOTE_TYPE_USAGE_QUERY_NAME;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NamedNativeQuery(
  name = FIND_ALL_NOTE_TYPES_USAGES_QUERY_NAME,
  query = FIND_ALL_NOTE_TYPES_USAGES_QUERY
)
@NamedNativeQuery(
  name = FIND_NOTE_TYPE_USAGE_QUERY_NAME,
  query = FIND_NOTE_TYPE_USAGE_QUERY
)

@Entity
@Table(name = "type", uniqueConstraints = {
  @UniqueConstraint(name = "uc_type_name", columnNames = {"name"})
})
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class NoteTypeEntity extends AuditableEntity {

  public static final String FIND_ALL_NOTE_TYPES_USAGES_QUERY_NAME = "NoteTypeEntity.findAllNoteTypesUsages";
  public static final String FIND_ALL_NOTE_TYPES_USAGES_QUERY = "SELECT cast(id as varchar) as typeId, "
    + "EXISTS (SELECT cast(type_id as varchar) FROM note "
    + "WHERE cast(type_id as varchar) = cast(type.id as varchar) LIMIT 1) as isAssigned FROM type";

  public static final String FIND_NOTE_TYPE_USAGE_QUERY_NAME = "NoteTypeEntity.findNoteTypeUsage";
  public static final String FIND_NOTE_TYPE_USAGE_QUERY = FIND_ALL_NOTE_TYPES_USAGES_QUERY
    + " WHERE cast(id as varchar) = cast(:noteTypeId as varchar)";

  @NotBlank
  @ToString.Include
  @Column(name = "name", nullable = false)
  private String name;

}
