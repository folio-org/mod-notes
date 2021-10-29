package org.folio.notes.domain.entity;

import java.util.Objects;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "link", indexes = {
  @Index(name = "idx_link_object_id_and_type", columnList = "object_id, object_type")
}, uniqueConstraints = {
  @UniqueConstraint(name = "uc_link_object_id_and_type", columnNames = {"object_id", "object_type"})
})
@Getter @Setter
public class LinkEntity extends BaseEntity {

  @Column(name = "object_id", nullable = false)
  private String objectId;

  @Column(name = "object_type", nullable = false)
  private String objectType;

  @ManyToMany(mappedBy = "links")
  private Set<NoteEntity> notes;

  @Override public int hashCode() {
    return Objects.hash(objectId, objectType);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    LinkEntity that = (LinkEntity) o;
    return Objects.equals(objectId, that.objectId) && Objects.equals(objectType, that.objectType);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" +
      "objectId = " + objectId + ", " +
      "objectType = " + objectType + ")";
  }
}
