package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.bootstrap.ApiClient.Reply;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static JsonNode normalize(JsonNode contract) {
        ((ObjectNode) contract).remove("servers");
        return contract;
    }
}
