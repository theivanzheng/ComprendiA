package es.comprendia.recurso;

import es.comprendia.servicio.TrabajoServicio;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class TranscripcionYoutubeCancelacionRecursoTest {

    private static final String ENDPOINT = "/api/transcripciones/youtube";

    @Inject
    TrabajoServicio trabajoServicio;

    @Test
    void cancelarTrabajoExistente_devuelveCanceladoYActualizaEstadoConsultable() {
        String idTrabajo = trabajoServicio.crearTrabajo();

        given()
        .when()
            .post(ENDPOINT + "/" + idTrabajo + "/cancelar")
        .then()
            .statusCode(200)
            .body("idTrabajo", equalTo(idTrabajo))
            .body("fase", equalTo("CANCELADO"));

        given()
        .when()
            .get(ENDPOINT + "/" + idTrabajo)
        .then()
            .statusCode(200)
            .body("id", equalTo(idTrabajo))
            .body("fase", equalTo("CANCELADO"))
            .body("error", equalTo("Trabajo cancelado por el usuario"));
    }

    @Test
    void cancelarTrabajoInexistente_devuelve404() {
        given()
        .when()
            .post(ENDPOINT + "/trabajo-inexistente/cancelar")
        .then()
            .statusCode(404)
            .body("error", notNullValue());
    }
}
