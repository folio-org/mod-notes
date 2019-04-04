package org.folio.rest.impl;

import static org.folio.rest.TestBase.STUB_TENANT;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Vertx;

class NoteTypesTestUtil {

  private static final String JSONB_COLUMN = "jsonb";
  private static final String NOTE_TYPE_TABLE = "note_type";

  private NoteTypesTestUtil() {
  }

  public static void insertNoteType(Vertx vertx, String stubId, String noteType) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    PostgresClient.getInstance(vertx).execute(
      "INSERT INTO " + getNoteTypesTableName() + "(" + " _id, " + JSONB_COLUMN + ") VALUES ('" + stubId + "' , '" + noteType + "');" ,
      event -> future.complete(null));
    future.join();
  }

    private static String getNoteTypesTableName() {
      return PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + NOTE_TYPE_TABLE;
    }

}
