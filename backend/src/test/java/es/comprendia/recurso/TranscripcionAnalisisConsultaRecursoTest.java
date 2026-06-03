package es.comprendia.recurso;

import es.comprendia.entidad.CapituloVideo;
import es.comprendia.entidad.ConceptoClaveVideo;
import es.comprendia.entidad.Video;
import es.comprendia.repositorio.CapituloVideoRepositorio;
import es.comprendia.repositorio.ConceptoClaveVideoRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class TranscripcionAnalisisConsultaRecursoTest {

    private static final String ENDPOINT = "/api/transcripciones";

    @Inject
    VideoRepositorio videoRepositorio;

    @Inject
    CapituloVideoRepositorio capituloVideoRepositorio;

    @Inject
    ConceptoClaveVideoRepositorio conceptoClaveVideoRepositorio;

    @Test
    void obtenerCapitulosVideoExistente_devuelveListaOrdenada() {
        Long idVideo = sembrarVideoConAnalisis();

        given()
        .when()
            .get(ENDPOINT + "/" + idVideo + "/capitulos")
        .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("[0].titulo", equalTo("Introducción al tema"))
            .body("[0].descripcion", equalTo("Presentación del objetivo de la clase"))
            .body("[0].tiempoInicio", equalTo(0.0f))
            .body("[0].tiempoFin", equalTo(180.0f))
            .body("[0].orden", equalTo(1))
            .body("[0].origen", equalTo("test"))
            .body("[1].titulo", equalTo("Aplicación práctica"))
            .body("[1].orden", equalTo(2));
    }

    @Test
    void obtenerConceptosVideoExistente_devuelveListaOrdenada() {
        Long idVideo = sembrarVideoConAnalisis();

        given()
        .when()
            .get(ENDPOINT + "/" + idVideo + "/conceptos")
        .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("[0].nombre", equalTo("RAG"))
            .body("[0].definicion", equalTo("Técnica para responder usando contexto recuperado"))
            .body("[0].tiempoInicio", equalTo(45.0f))
            .body("[0].orden", equalTo(1))
            .body("[1].nombre", equalTo("Embeddings"))
            .body("[1].orden", equalTo(2));
    }

    @Test
    void obtenerCapitulosVideoInexistente_devuelve404() {
        given()
        .when()
            .get(ENDPOINT + "/999999999/capitulos")
        .then()
            .statusCode(404)
            .body("error", notNullValue());
    }

    @Test
    void obtenerConceptosVideoInexistente_devuelve404() {
        given()
        .when()
            .get(ENDPOINT + "/999999999/conceptos")
        .then()
            .statusCode(404)
            .body("error", notNullValue());
    }

    private Long sembrarVideoConAnalisis() {
        return QuarkusTransaction.requiringNew().call(() -> {
            Video video = new Video();
            video.youtubeId = "test-" + UUID.randomUUID();
            video.titulo = "Clase de prueba";
            video.fuenteTranscripcion = "test";
            video.fechaCreacion = LocalDateTime.of(2026, 5, 10, 10, 0);
            videoRepositorio.persistAndFlush(video);

            CapituloVideo segundoCapitulo = new CapituloVideo();
            segundoCapitulo.video = video;
            segundoCapitulo.titulo = "Aplicación práctica";
            segundoCapitulo.descripcion = "Resolución guiada del ejercicio principal";
            segundoCapitulo.tiempoInicio = 180.0;
            segundoCapitulo.tiempoFin = 420.0;
            segundoCapitulo.ordenCapitulo = 2;
            segundoCapitulo.origen = "test";
            capituloVideoRepositorio.persist(segundoCapitulo);

            CapituloVideo primerCapitulo = new CapituloVideo();
            primerCapitulo.video = video;
            primerCapitulo.titulo = "Introducción al tema";
            primerCapitulo.descripcion = "Presentación del objetivo de la clase";
            primerCapitulo.tiempoInicio = 0.0;
            primerCapitulo.tiempoFin = 180.0;
            primerCapitulo.ordenCapitulo = 1;
            primerCapitulo.origen = "test";
            capituloVideoRepositorio.persist(primerCapitulo);

            ConceptoClaveVideo segundoConcepto = new ConceptoClaveVideo();
            segundoConcepto.video = video;
            segundoConcepto.nombre = "Embeddings";
            segundoConcepto.definicion = "Representación vectorial de fragmentos de texto";
            segundoConcepto.tiempoInicio = 210.0;
            segundoConcepto.ordenConcepto = 2;
            conceptoClaveVideoRepositorio.persist(segundoConcepto);

            ConceptoClaveVideo primerConcepto = new ConceptoClaveVideo();
            primerConcepto.video = video;
            primerConcepto.nombre = "RAG";
            primerConcepto.definicion = "Técnica para responder usando contexto recuperado";
            primerConcepto.tiempoInicio = 45.0;
            primerConcepto.ordenConcepto = 1;
            conceptoClaveVideoRepositorio.persist(primerConcepto);

            return video.id;
        });
    }
}
