package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.folio.rest.impl.TestUtil.readFile;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import io.restassured.http.Header;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.http.protocol.HTTP;
import org.folio.rest.TestBase;
import org.junit.Test;

import io.restassured.RestAssured;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class NoteTypesImplTest extends TestBase {

  private final String NOT_EXISTING_STUB_ID = "9798274e-ce9d-46ab-aa28-00ca9cf4698a";
  private final String NOTE_TYPES_ENDPOINT = "/note-types";
  private final Header CONTENT_TYPE_HEADER = new Header(HTTP.CONTENT_TYPE, "application/json");

  @Test
  public void shouldReturn200WithNoteTypeWhenValidId () throws IOException, URISyntaxException {

    final String stubNoteType = readFile("post_note_type.json");
    final String stubId = "9c1e6f3c-682d-4af4-bd6b-20dad912ff94";

    NoteTypesTestUtil.insertNoteType(vertx, stubId, stubNoteType);

    RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .get(NOTE_TYPES_ENDPOINT + "/" + stubId)
      .then()
      .statusCode(200).extract().asString();
  }

  @Test
  public void shouldReturn404WhenInvalidId (){

    RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .get(NOTE_TYPES_ENDPOINT + "/" + NOT_EXISTING_STUB_ID)
      .then()
      .statusCode(404).extract().asString();
  }

  @Test
  public void shouldReturn500WhenErrorOccurred (){

    final String invalidStubId = "11111111-222-1111-2-111111111111";
    stubFor(
      get(new UrlPathPattern(new EqualToPattern(NOTE_TYPES_ENDPOINT +"/" + invalidStubId), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(500)));

    RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .get(NOTE_TYPES_ENDPOINT + "/" + invalidStubId)
      .then()
      .statusCode(500).extract().asString();
  }

  @Test
  public void shouldReturn501WhenDeleteEndpointNotImplemented (){

    stubFor(
      get(new UrlPathPattern(new EqualToPattern(NOTE_TYPES_ENDPOINT + "/" + NOT_EXISTING_STUB_ID), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(501)));

    RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .delete(NOTE_TYPES_ENDPOINT + "/" + NOT_EXISTING_STUB_ID)
      .then()
      .statusCode(501).extract().asString();
  }

  @Test
  public void shouldReturn501WhenPostEndpointNotImplemented () throws IOException, URISyntaxException {

    stubFor(
      get(new UrlPathPattern(new EqualToPattern(NOTE_TYPES_ENDPOINT), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(501)));

    RestAssured.given()
      .spec(getRequestSpecification())
      .header(CONTENT_TYPE_HEADER)
      .body(readFile("post_note_type.json"))
      .when()
      .post(NOTE_TYPES_ENDPOINT)
      .then()
      .statusCode(501).extract().asString();
  }

  @Test
  public void shouldReturn501WhenPutEndpointNotImplemented () throws IOException, URISyntaxException {

    stubFor(
      get(new UrlPathPattern(new EqualToPattern(NOTE_TYPES_ENDPOINT+ "/" + NOT_EXISTING_STUB_ID), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(501)));

    RestAssured.given()
      .spec(getRequestSpecification())
      .header(CONTENT_TYPE_HEADER)
      .body(readFile("post_note_type.json"))
      .when()
      .put(NOTE_TYPES_ENDPOINT + "/" + NOT_EXISTING_STUB_ID)
      .then()
      .statusCode(501).extract().asString();
  }

}
