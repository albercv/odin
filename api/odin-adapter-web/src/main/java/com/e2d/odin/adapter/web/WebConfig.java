package com.e2d.odin.adapter.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/** Valores por defecto del adaptador: OpenAPI, interfaz de documentación y tope técnico de las subidas. */
@Configuration
@PropertySource("classpath:odin-web.properties")
public class WebConfig {
}
