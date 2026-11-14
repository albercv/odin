package com.e2d.odin.adapter.web.worker;

import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.link.IssueUploadLink;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectUploadController {

    private final IssueUploadLink issueUploadLink;

    public ProjectUploadController(IssueUploadLink issueUploadLink) {
        this.issueUploadLink = issueUploadLink;
    }

    @PostMapping("/v1/projects/{projectId}/upload-links")
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.IssuedLink issueProjectUploadLink(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID projectId) {
        return Responses.IssuedLink.from(issueUploadLink.forProject(actor, new ProjectId(projectId)));
    }
}
