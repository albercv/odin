package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Requests;
import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.topic.CreateTopic;
import com.e2d.odin.application.topic.DeleteTopic;
import com.e2d.odin.application.topic.ListTopics;
import com.e2d.odin.application.topic.TopicChanges;
import com.e2d.odin.application.topic.UpdateTopic;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.topic.TopicId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/companies/{companyId}/topics")
public class TopicController {

    private final CreateTopic create;
    private final UpdateTopic update;
    private final DeleteTopic delete;
    private final ListTopics list;

    public TopicController(CreateTopic create, UpdateTopic update, DeleteTopic delete, ListTopics list) {
        this.create = create;
        this.update = update;
        this.delete = delete;
        this.list = list;
    }

    @GetMapping
    public List<Responses.TopicView> listTopics(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId) {
        return list.execute(actor, new CompanyId(companyId)).stream().map(Responses.TopicView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.TopicView createTopic(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestBody Requests.TopicRequest request) {
        return Responses.TopicView.from(create.execute(actor, new CompanyId(companyId), request.name(),
                request.definition()));
    }

    @PatchMapping("/{topicId}")
    public Responses.TopicView updateTopic(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @PathVariable UUID topicId, @RequestBody Requests.TopicPatch request) {
        TopicChanges changes = new TopicChanges(Optional.ofNullable(request.name()),
                Optional.ofNullable(request.definition()));
        return Responses.TopicView.from(update.execute(actor, new CompanyId(companyId), new TopicId(topicId),
                changes));
    }

    @DeleteMapping("/{topicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTopic(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor, @PathVariable UUID companyId,
            @PathVariable UUID topicId) {
        delete.execute(actor, new CompanyId(companyId), new TopicId(topicId));
    }
}
