package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.bootstrap.ApiClient.Reply;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class OpenApiContractIT extends AbstractIT {

    private static final Path CONTRACT = Path.of("..", "openapi.json");
    private static final JsonMapper MAPPER = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @LocalServerPort int port;

    @Test
    void servedContractMatchesTheVersionedFile() throws IOException {
        Reply reply = new ApiClient(port).get("/v1/openapi.json");
        assertThat(reply.status()).isEqualTo(200);
        JsonNode served = normalize(reply.json());
        if (Boolean.getBoolean("odin.updateOpenApi")) {
            Files.writeString(CONTRACT, MAPPER.writeValueAsString(served) + "\n");
        }
        assertThat(served).isEqualTo(MAPPER.readTree(Files.readString(CONTRACT)));
    }

    @Test
    void searchResponseSchemaIsDistinctFromItsRequestSchema() {
        JsonNode search = servedContract().at("/paths/~1v1~1search/post");
        JsonNode schemas = servedContract().at("/components/schemas");
        String request = search.at("/requestBody/content/application~1json/schema/$ref").asString();
        String response = search.at("/responses/200/content/application~1json/schema/$ref").asString();
        assertThat(schemas.at("/" + request.substring(request.lastIndexOf('/') + 1) + "/properties/queries").isObject())
                .isTrue();
        JsonNode responseProperties = schemas.at("/" + response.substring(response.lastIndexOf('/') + 1) + "/properties");
        assertThat(responseProperties.has("hits")).isTrue();
        assertThat(responseProperties.has("noSource")).isTrue();
    }

    @Test
    void everyDtoHasItsOwnSchema() {
        List<String> dtoNames = Stream.concat(Arrays.stream(Requests.class.getDeclaredClasses()),
                Arrays.stream(Responses.class.getDeclaredClasses())).map(Class::getSimpleName).toList();
        List<String> schemaNames = servedContract().at("/components/schemas").propertyNames().stream().toList();
        assertThat(dtoNames).doesNotHaveDuplicates();
        assertThat(schemaNames).containsExactlyInAnyOrderElementsOf(dtoNames);
    }

    @Test
    void uploadDeclares201And200ForDuplicateAndLogoutDeclares204() {
        JsonNode paths = servedContract().at("/paths");
        JsonNode upload = paths.at("/~1v1~1admin~1companies~1{companyId}~1documents/post/responses");
        assertThat(upload.propertyNames()).containsExactlyInAnyOrder("201", "200");
        assertThat(upload.at("/201/content/application~1json/schema/$ref").asString()).endsWith("/Upload");
        assertThat(paths.at("/~1v1~1admin~1session/delete/responses").propertyNames()).containsExactly("204");
    }

    @Test
    void fieldsTheApiAlwaysSendsAreRequired() {
        JsonNode schemas = servedContract().at("/components/schemas");
        assertThat(required(schemas, "DocumentView")).contains("id", "title", "status", "attemptCount")
                .doesNotContain("projectId", "failureReason");
        assertThat(required(schemas, "Hit")).contains("sourceType", "sourceId", "title", "authorName", "excerpt")
                .doesNotContain("pageNumber", "projectId");
        assertThat(required(schemas, "WorkerDocumentView")).contains("id", "title").doesNotContain("projectId");
        assertThat(required(schemas, "SearchRequest")).isEmpty();
    }

    private static List<String> required(JsonNode schemas, String name) {
        return schemas.at("/" + name + "/required").valueStream().map(JsonNode::asString).toList();
    }

    @Test
    void jsonResponsesDeclareApplicationJson() {
        JsonNode paths = servedContract().at("/paths");
        paths.properties().forEach(path -> path.getValue().properties().forEach(operation -> operation.getValue()
                .at("/responses").properties().forEach(response -> {
                    JsonNode content = response.getValue().path("content");
                    assertThat(content.has("*/*")).as(path.getKey() + " " + operation.getKey()).isFalse();
                })));
    }

    private JsonNode servedContract() {
        return new ApiClient(port).get("/v1/openapi.json").json();
    }

    private static JsonNode normalize(JsonNode contract) {
        ((ObjectNode) contract).remove("servers");
        return contract;
    }
}
