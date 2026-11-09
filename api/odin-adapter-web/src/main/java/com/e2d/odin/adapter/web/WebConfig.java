package com.e2d.odin.adapter.web;

import com.e2d.odin.adapter.web.dto.Responses;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/** Valores por defecto del adaptador: OpenAPI, interfaz de documentación y tope técnico de las subidas. */
@Configuration
@PropertySource("classpath:odin-web.properties")
public class WebConfig {

    /** En las respuestas, los campos que la API siempre envía con valor se publican como obligatorios. */
    @Bean
    OpenApiCustomizer requiredResponseFields() {
        return api -> Arrays.stream(Responses.class.getDeclaredClasses()).filter(Class::isRecord).forEach(type -> {
            Schema<?> schema = api.getComponents().getSchemas().get(type.getSimpleName());
            Arrays.stream(type.getRecordComponents())
                    .filter(c -> !c.getAnnotatedType().isAnnotationPresent(Nullable.class))
                    .forEach(c -> schema.addRequiredItem(c.getName()));
        });
    }
}
