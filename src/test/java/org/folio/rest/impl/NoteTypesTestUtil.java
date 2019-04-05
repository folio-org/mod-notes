package org.folio.rest.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.persist.PostgresClient;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.folio.rest.TestBase.STUB_TENANT;

class NoteTypesTestUtil {

  private static final String JSONB_COLUMN = "jsonb";
  private static final String NOTE_TYPE_TABLE = "note_type";

  private NoteTypesTestUtil() {
  }

  public static void insertNoteType(Vertx vertx, String stubId, String noteType) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    PostgresClient.getInstance(vertx).execute(
      "INSERT INTO " + getNoteTypesTableName() + "(" + " _id, " + JSONB_COLUMN + ") VALUES ('" + stubId + "' , '" + noteType + "');",
      event -> future.complete(null));
    future.join();
  }

  private static String getNoteTypesTableName() {
    return PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + NOTE_TYPE_TABLE;
  }

  public static List<NoteType> getAllNoteTypes(Vertx vertx) {
    ObjectMapper mapper = new ObjectMapper();
    CompletableFuture<List<NoteType>> future = new CompletableFuture<>();
    PostgresClient.getInstance(vertx).select(
      "SELECT * FROM " + getNoteTypesTableName(),
      event -> future.complete(event.result().getRows().stream()
        .map(row -> row.getString(JSONB_COLUMN))
        .map(json -> parseNoteType(mapper, json))
        .collect(Collectors.toList())));
    return future.join();
  }

  public static void deleteAllNoteTypes(Vertx vertx) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    PostgresClient.getInstance(vertx).execute(
      "DELETE FROM " + getNoteTypesTableName(),
      event -> future.complete(null));
    future.join();
  }


  private static NoteType parseNoteType(ObjectMapper mapper, String json) {
    try {
      return mapper.readValue(json, NoteType.class);
    } catch (IOException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Can't parse note type", e);
    }
  }
}
