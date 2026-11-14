package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.intake.AddFileToIntake;
import com.e2d.odin.application.intake.CloseIntakeUpload;
import com.e2d.odin.application.intake.GetIntakeBatch;
import com.e2d.odin.application.intake.IntakeBatchView;
import com.e2d.odin.application.intake.IntakeFileResult;
import com.e2d.odin.application.intake.StartIntake;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.intake.IntakeBatch;
import com.e2d.odin.domain.intake.IntakeBatchId;
import com.e2d.odin.domain.intake.IntakeChannel;
import com.e2d.odin.domain.intake.IntakeCounts;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ValidationException;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** La primera carga de una empresa: abrir el lote, subir ficheros de forma reanudable y cerrar la subida. */
@RestController
public class IntakeController {

    private static final String BATCHES = "/v1/admin/companies/{companyId}/intake-batches";

    private final StartIntake start;
    private final AddFileToIntake addFile;
    private final CloseIntakeUpload closeUpload;
    private final GetIntakeBatch get;

    public IntakeController(StartIntake start, AddFileToIntake addFile, CloseIntakeUpload closeUpload,
            GetIntakeBatch get) {
        this.start = start;
        this.addFile = addFile;
        this.closeUpload = closeUpload;
        this.get = get;
    }

    @PostMapping(BATCHES)
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.IntakeBatchView start(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestBody Requests.StartIntakeRequest request) {
        IntakeBatch batch = start.execute(actor, new CompanyId(companyId), channel(request.channel()));
        return Responses.IntakeBatchView.from(new IntakeBatchView(batch, new IntakeCounts(0, 0)));
    }

    @GetMapping(BATCHES + "/{batchId}")
    public Responses.IntakeBatchView get(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId) {
        return Responses.IntakeBatchView.from(get.execute(actor, new CompanyId(companyId), new IntakeBatchId(batchId)));
    }

    @PostMapping(path = BATCHES + "/{batchId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "201", description = "Documento nuevo, pendiente de confirmar")
    @ApiResponse(responseCode = "200", description = "No se creó nada: ya estaba o se descartó en este lote")
    public ResponseEntity<Responses.IntakeFile> addFile(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId, @RequestParam("file") MultipartFile file,
            @RequestParam String path) throws IOException {
        IntakeFileResult result = addFile.execute(actor, new CompanyId(companyId), new IntakeBatchId(batchId), path,
                file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.status(result.outcome() == IntakeFileResult.Outcome.CREATED ? HttpStatus.CREATED
                : HttpStatus.OK).body(Responses.IntakeFile.from(result));
    }

    @GetMapping(BATCHES + "/{batchId}/files")
    public Responses.KnownFiles knownFiles(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId, @RequestParam List<String> sha256) {
        return new Responses.KnownFiles(List.copyOf(
                get.knownFiles(actor, new CompanyId(companyId), new IntakeBatchId(batchId), sha256)));
    }

    @PostMapping(BATCHES + "/{batchId}/close-upload")
    public Responses.IntakeBatchView closeUpload(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID batchId) {
        closeUpload.execute(actor, new CompanyId(companyId), new IntakeBatchId(batchId));
        return get(actor, companyId, batchId);
    }

    private static IntakeChannel channel(String value) {
        try {
            return IntakeChannel.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationException("El canal tiene que ser COMMAND o PANEL");
        }
    }
}
