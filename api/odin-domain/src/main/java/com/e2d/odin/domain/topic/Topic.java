package com.e2d.odin.domain.topic;

import com.e2d.odin.domain.shared.CompanyId;

/** Un tema de la lista de una empresa, con la definición que lee el etiquetador. */
public record Topic(TopicId id, CompanyId companyId, String name, String definition) {

    public static final int MAX_NAME = 60;
    public static final int MAX_DEFINITION = 500;
    public static final int MAX_PER_COMPANY = 30;
}
