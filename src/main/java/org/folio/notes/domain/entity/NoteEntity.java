package org.folio.notes.domain.entity;

import java.util.HashSet;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedSubgraph;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.folio.notes.domain.repository.NoteRepository;

@Entity
@NamedEntityGraph(
  name = NoteRepository.NOTE_COLLECTION_GRAPH,
  attributeNodes = {
    @NamedAttributeNode(value = "type", subgraph = "type-subgraph"),
    @NamedAttributeNode(value = "links")
  },
  subgraphs = {
    @NamedSubgraph(
      name = "type-subgraph",
      attributeNodes = {
        @NamedAttributeNode("id"),
        @NamedAttributeNode("name")
      }
    )
  }
)
@Table(name = "note", indexes = {
  @Index(name = "idx_note_content", columnList = "indexed_content"),
  @Index(name = "idx_note_type_id", columnList = "type_id")
})
@Getter
@Setter
public class NoteEntity extends AuditableEntity {

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "content")
  private String content;

  @Column(name = "indexed_content")
  private String indexedContent;

  @Column(name = "domain", nullable = false)
  private String domain;

  @Column(name = "pop_up_on_user")
  private boolean popUpOnUser;

  @Column(name = "pop_up_on_check_out")
  private boolean popUpOnCheckOut;

  @ManyToOne(optional = false)
  @JoinColumn(name = "type_id", nullable = false)
  private NoteTypeEntity type;

  @JoinTable(name = "note_link",
    joinColumns = @JoinColumn(name = "note_id", referencedColumnName = "id"),
    inverseJoinColumns = @JoinColumn(name = "link_id", referencedColumnName = "id"))
  @ManyToMany
  private Set<LinkEntity> links;

  public void addLink(LinkEntity link) {
    if (links == null) {
      links = new HashSet<>();
    }
    links.add(link);
  }

  public void deleteLink(LinkEntity link) {
    if (links != null) {
      links.remove(link);
    }
  }
}
