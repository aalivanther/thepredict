package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static akyrion.thepredict.adapter.jev.SystemOneContractServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelCatalogCheckTest {
    private final SystemOneContractServer server = SystemOneContractServer.start();
    private final List<SystemOneClient> clients = new ArrayList<>();

    @AfterEach
    void stop() {
        clients.forEach(SystemOneClient::close);
        server.close();
        assertEquals(0, server.posts(), "a check never consumes POST /v1/systemone");
    }

    @Test
    void aListedAliasIsReady() throws Exception {
        assertEquals(new ServiceCheck(ServiceState.READY, ModelCatalogParser.LISTED), check("jev-latest"));
        var request = server.received().getFirst();
        assertEquals("GET", request.method());
        assertEquals("/v1/models", request.path());
        assertEquals("Bearer " + JevFixtures.KEY, request.header("Authorization"));
    }

    @Test
    void aVersionedModelMissingFromTheListIsReadyButUnverified() throws Exception {
        assertEquals(new ServiceCheck(ServiceState.READY, ModelCatalogParser.UNVERIFIED), check("jev-1.13.0"));
    }

    @Test
    void anUnlistedAliasIsAnInvalidModel() throws Exception {
        assertEquals(ServiceState.INVALID_MODEL, check("jev-nightly").state());
    }

    @Test
    void anEmptyListKeepsTheVersionedRule() throws Exception {
        server.onModels(json(200, SystemOneContractServer.fixture("models-empty.json")));
        assertEquals(ServiceState.READY, check("jev-1.13.0").state());
        assertEquals(ServiceState.INVALID_MODEL, check("jev-latest").state());
    }

    @Test
    void rejectedCredentialsAreInvalidCredentials() throws Exception {
        server.onModels(json(401, "{\"detail\":\"Invalid API key\"}"));
        assertEquals(new ServiceCheck(ServiceState.INVALID_CREDENTIALS, "HTTP 401: the service rejected the credentials"),
                check("jev-1.13.0"));
        server.onModels(json(403, "{}"));
        assertEquals(ServiceState.INVALID_CREDENTIALS, check("jev-1.13.0").state());
    }

    @Test
    void overloadRateLimitAndServerErrorsPause() throws Exception {
        server.onModels(json(529, "{}"));
        assertEquals(new ServiceCheck(ServiceState.PAUSED, "HTTP 529: service overloaded"), check("jev-1.13.0"));
        server.onModels(json(429, "{}"));
        assertEquals(ServiceState.PAUSED, check("jev-1.13.0").state());
        server.onModels(json(500, "{}"));
        assertEquals(ServiceState.PAUSED, check("jev-1.13.0").state());
    }

    @Test
    void aBadRequestOnTheStartupCheckPausesWithAFixedDetail() throws Exception {
        server.onModels(json(400, "{\"detail\":{\"error_type\":\"api_usage_error\",\"message\":\"secret-marker\"}}"));
        assertEquals(new ServiceCheck(ServiceState.PAUSED, "HTTP 400: the service rejected the request"),
                check("jev-1.13.0"));
        server.onModels(json(400, "not json secret-marker"));
        assertEquals(new ServiceCheck(ServiceState.PAUSED, "HTTP 400: the service rejected the request"),
                check("jev-1.13.0"));
    }

    @Test
    void brokenJsonPausesWithoutQuotingTheBody() throws Exception {
        server.onModels(json(200, "{\"models\": [zz_marker"));
        assertEquals(new ServiceCheck(ServiceState.PAUSED, "invalid model list: model list is not valid JSON"),
                check("jev-1.13.0"));
    }

    @Test
    void aTimeoutPauses() throws Exception {
        server.onModels(SystemOneContractServer.stallBeforeHeaders());
        var client = track(new SystemOneClient(JevFixtures.settings(server.baseUrl(), Duration.ofMillis(150)),
                JevFixtures.CLOCK));
        assertEquals(new ServiceCheck(ServiceState.PAUSED, "no response within the request timeout"),
                client.check().get(10, TimeUnit.SECONDS));
    }

    @Test
    void theFixtureFollowsModelMetadataListExactly() {
        var root = JsonParser.parseString(SystemOneContractServer.fixture("models.json")).getAsJsonObject();
        assertEquals(Set.of("models"), root.keySet());
        for (var model : root.getAsJsonArray("models")) {
            assertEquals(Set.of("name", "description", "release_date"), model.getAsJsonObject().keySet());
        }
        assertEquals(Set.of("jev-latest", "jev-preview"),
                ModelCatalogParser.names(SystemOneContractServer.fixture("models.json")));
    }

    @Test
    void extraOrMissingMetadataBesidesTheNameIsTolerated() {
        var body = "{\"models\":[{\"name\":\"jev-latest\",\"context_window\":65536,\"tags\":[\"x\"]},"
                + "{\"name\":\"jev-1.14.0\",\"description\":\"d\"}],\"next_page\":null}";
        assertEquals(Set.of("jev-latest", "jev-1.14.0"), ModelCatalogParser.names(body));
    }

    @Test
    void anotherShapeIsRejected() {
        assertThrows(SystemOneProtocolException.class, () -> ModelCatalogParser.names("{\"data\":[]}"));
        assertThrows(SystemOneProtocolException.class, () -> ModelCatalogParser.names("{\"models\":[{\"id\":\"x\"}]}"));
        assertThrows(SystemOneProtocolException.class, () -> ModelCatalogParser.names("{\"models\":[\"jev-latest\"]}"));
    }

    private ServiceCheck check(String model) throws Exception {
        var settings = new SystemOneSettings(server.baseUrl(), model, JevFixtures.KEY, Duration.ofSeconds(2),
                Duration.ofSeconds(5), 262_144, 12_000, "0.0.1");
        return track(new SystemOneClient(settings, JevFixtures.CLOCK)).check().get(10, TimeUnit.SECONDS);
    }

    private SystemOneClient track(SystemOneClient client) {
        clients.add(client);
        return client;
    }
}
