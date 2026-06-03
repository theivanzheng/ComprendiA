package es.comprendia.recurso;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class TranscripcionYoutubeRecursoTest {

    private static final String ENDPOINT = "/api/transcripciones/youtube";

    @Test
    void urlValidaYoutubeComConWww_devuelve202() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"urlVideo\": \"https://www.youtube.com/watch?v=abc123\"}")
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(202)
            .body("idTrabajo", notNullValue())
            .body("idTrabajo", not(emptyString()));
    }

    @Test
    void urlValidaYoutubeComSinWww_devuelve202() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"urlVideo\": \"https://youtube.com/watch?v=xyz456\"}")
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(202)
            .body("idTrabajo", notNullValue())
            .body("idTrabajo", not(emptyString()));
    }

    @Test
    void urlValidaYoutubeBe_devuelve202() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"urlVideo\": \"https://youtu.be/def789\"}")
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(202)
            .body("idTrabajo", notNullValue())
            .body("idTrabajo", not(emptyString()));
    }

    @Test
    void urlInvalidaVimeo_devuelve400() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"urlVideo\": \"https://vimeo.com/12345\"}")
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(400)
            .body("error", notNullValue());
    }

    @Test
    void urlVacia_devuelve400() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"urlVideo\": \"\"}")
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(400)
            .body("error", notNullValue());
    }

    @Test
    void bodySinUrlVideo_devuelve400() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(400)
            .body("error", notNullValue());
    }
}
