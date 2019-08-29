package org.folio.note;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.folio.util.NoteTestData.NOTE_1;
import static org.folio.util.NoteTestData.NOTE_6_EMPTY_CONTENT;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import org.folio.common.OkapiParams;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Note;
import org.folio.spring.config.TestConfig;
import org.folio.userlookup.UserLookUp;
import org.folio.userlookup.UserLookUpService;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class NoteServiceImplTest {

  @Autowired
  @InjectMocks
  NoteServiceImpl noteService;
  @Mock
  NoteRepositoryImpl repository;
  @Mock
  UserLookUpService userLookUpService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void shouldSanitizeInputOnAdd() {
    Note note = Json.decodeValue(NOTE_1, Note.class)
      .withContent("<script></script><img/><br><iframe></iframe>")
      .withMetadata(new Metadata());

    ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
    when(userLookUpService.getUserInfo(any())).thenReturn(Future.succeededFuture(UserLookUp.builder().build()));
    when(repository.save(captor.capture(), any())).thenReturn(Future.succeededFuture(note));

    noteService.addNote(note, mock(OkapiParams.class));

    assertThat(captor.getValue().getContent(), not(containsString("<script>")));
    assertThat(captor.getValue().getContent(), not(containsString("<img/>")));
    assertThat(captor.getValue().getContent(), not(containsString("<iframe>")));
    assertThat(captor.getValue().getContent(), containsString("<br>"));
  }

  @Test
  public void shouldSanitizeInputOnUpdate() {
    Note note = Json.decodeValue(NOTE_1, Note.class)
      .withContent("<script></script><br><iframe></iframe>")
      .withMetadata(new Metadata());

    ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
    when(userLookUpService.getUserInfo(any())).thenReturn(Future.succeededFuture(UserLookUp.builder().build()));
    when(repository.update(eq(note.getId()), captor.capture(), any())).thenReturn(Future.succeededFuture());

    noteService.updateNote(note.getId(), note, mock(OkapiParams.class));

    assertThat(captor.getValue().getContent(), not(containsString("<script>")));
    assertThat(captor.getValue().getContent(), not(containsString("<iframe>")));
    assertThat(captor.getValue().getContent(), containsString("<br>"));
  }

  @Test
  public void shouldSanitizeInputOnUpdateWithNoContent() {
    Note note = Json.decodeValue(NOTE_6_EMPTY_CONTENT, Note.class)
      .withMetadata(new Metadata());

    ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
    when(userLookUpService.getUserInfo(any())).thenReturn(Future.succeededFuture(UserLookUp.builder().build()));
    when(repository.update(eq(note.getId()), captor.capture(), any())).thenReturn(Future.succeededFuture());

    noteService.updateNote(note.getId(), note, mock(OkapiParams.class));

    assertThat(captor.getValue().getContent(), nullValue());
  }

  @Test
  public void shouldSanitizeInputOnUpdateWithEmptyContent() {
    Note note = Json.decodeValue(NOTE_6_EMPTY_CONTENT, Note.class)
      .withContent("")
      .withMetadata(new Metadata());

    ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
    when(userLookUpService.getUserInfo(any())).thenReturn(Future.succeededFuture(UserLookUp.builder().build()));
    when(repository.update(eq(note.getId()), captor.capture(), any())).thenReturn(Future.succeededFuture());

    noteService.updateNote(note.getId(), note, mock(OkapiParams.class));

    assertThat(captor.getValue().getContent(), is(""));
  }
}
