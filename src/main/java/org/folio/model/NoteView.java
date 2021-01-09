package org.folio.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.UserDisplayInfo;

/**
 * Database representation of a note
 */
@JsonIgnoreProperties({"linkIds", "linkTypes"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NoteView {

  private String id;

  @NotNull
  private String typeId;

  private String type;
  
  @NotNull
  private String domain;

  @NotNull
  private String title;

  @NotNull
  private String content;

  private UserDisplayInfo creator;

  private UserDisplayInfo updater;

  private Metadata metadata;

  private List<Link> links = new ArrayList<>();

  @JsonIgnore
  private final Map<String, Object> additionalProperties = new HashMap<String, Object>();

  public String getId() {
    return id;
  }

  public String getTypeId() {
    return typeId;
  }

  public String getType() {
    return type;
  }
  
  public String getDomain() {
    return domain;
  }
  
  public String getTitle() {
    return title;
  }

  public String getContent() {
    return content;
  }

  public UserDisplayInfo getCreator() {
    return creator;
  }

  public UserDisplayInfo getUpdater() {
    return updater;
  }

  public Metadata getMetadata() {
    return metadata;
  }

  public List<Link> getLinks() {
    return links;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setTypeId(String typeId) {
    this.typeId = typeId;
  }

  public void setType(String type) {
    this.type = type;
  }
  
  public void setDomain(String domain) {
    this.domain = domain;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public void setCreator(UserDisplayInfo creator) {
    this.creator = creator;
  }

  public void setUpdater(UserDisplayInfo updater) {
    this.updater = updater;
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  public void setLinks(List<Link> links) {
    this.links = links;
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return this.additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
  }

  public NoteView withAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
    return this;
  }
}
