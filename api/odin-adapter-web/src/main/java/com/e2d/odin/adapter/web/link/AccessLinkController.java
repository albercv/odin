package com.e2d.odin.adapter.web.link;

import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.application.link.DescribeUploadLink;
import com.e2d.odin.application.link.OpenOriginal;
import com.e2d.odin.application.link.UploadByLink;
import com.e2d.odin.domain.shared.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Las rutas con token: sin sesión ni clave, solo desde la red local y con el 404 de siempre ante cualquier fallo. */
@RestController
public class AccessLinkController {

    private final LocalNetworkGuard guard;
    private final DescribeUploadLink describeUpload;
    private final UploadByLink uploadByLink;
    private final OpenOriginal openOriginal;

    public AccessLinkController(LocalNetworkGuard guard, DescribeUploadLink describeUpload, UploadByLink uploadByLink,
            OpenOriginal openOriginal) {
        this.guard = guard;
        this.describeUpload = describeUpload;
        this.uploadByLink = uploadByLink;
        this.openOriginal = openOriginal;
    }

    @GetMapping("/v1/upload-links/{token}")
    public Responses.UploadLinkInfo describeUploadLink(HttpServletRequest request, @PathVariable String token) {
        return Responses.UploadLinkInfo.from(describeUpload.execute(token, clientAddress(request)));
    }

    @PostMapping(path = "/v1/upload-links/{token}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Responses.UploadReceipt uploadByLink(HttpServletRequest request, @PathVariable String token,
            @RequestParam("file") MultipartFile file) throws IOException {
        String address = clientAddress(request);
        return new Responses.UploadReceipt(
                uploadByLink.execute(token, address, file.getOriginalFilename(), file.getBytes()).filename());
    }

    @GetMapping("/v1/originals/{token}")
    public ResponseEntity<Resource> openOriginalByLink(HttpServletRequest request, @PathVariable String token) {
        return OriginalResponse.of(openOriginal.execute(token, clientAddress(request)));
    }

    private String clientAddress(HttpServletRequest request) {
        return guard.clientAddress(request)
                .orElseThrow(() -> new NotFoundException("Enlace no encontrado"));
    }
}
