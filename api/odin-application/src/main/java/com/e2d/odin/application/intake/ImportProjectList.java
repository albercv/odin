package com.e2d.odin.application.intake;

import com.e2d.odin.application.port.IntakeRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.CandidateOrigin;
import com.e2d.odin.domain.intake.DecisionState;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.ProjectCandidate;
import com.e2d.odin.domain.intake.ProjectRules;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ValidationException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * La lista de proyectos exportada del programa de gestión: CSV o TXT en UTF-8 con un nombre por línea. Los nombres
 * son candidatos {@code IMPORTED}; con lista importada ya no se descubren (Excel queda fuera, D-080).
 */
public class ImportProjectList {

    private static final String BYTE_ORDER_MARK = "﻿";

    private final IntakeRepository intake;

    public ImportProjectList(IntakeRepository intake) {
        this.intake = intake;
    }

    public List<ProjectCandidate> execute(Actor actor, CompanyId companyId, IntakeBatchId batchId, byte[] content) {
        IntakeBatches.require(intake, actor, companyId, batchId);
        Map<String, String> names = names(decode(content));
        if (names.isEmpty()) {
            throw new ValidationException("La lista no tiene ningún nombre");
        }
        List<ProjectCandidate> existing = intake.candidates(companyId, batchId);
        existing.stream().filter(c -> c.origin() == CandidateOrigin.DISCOVERED && c.state() == DecisionState.PROPOSED)
                .forEach(c -> intake.save(c.reject()));
        Set<String> known = existing.stream().map(c -> ProjectRules.normalize(c.name())).collect(Collectors.toSet());
        names.forEach((key, name) -> {
            if (!known.contains(key)) {
                intake.save(new ProjectCandidate(companyId, batchId, name, CandidateOrigin.IMPORTED, 0,
                        DecisionState.PROPOSED, Optional.empty()));
            }
        });
        return intake.candidates(companyId, batchId);
    }

    private static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            throw new ValidationException("La lista tiene que estar en UTF-8");
        }
    }

    /** Una línea, un nombre; sin vacías ni repetidas (comparadas normalizadas, queda la primera grafía). */
    private static Map<String, String> names(String text) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String line : text.replace(BYTE_ORDER_MARK, "").split("\\R")) {
            String key = ProjectRules.normalize(line);
            if (!key.isEmpty()) {
                names.putIfAbsent(key, line.strip());
            }
        }
        return names;
    }
}
