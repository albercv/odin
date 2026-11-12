package com.e2d.odin.adapter.web.admin;

import com.e2d.odin.adapter.web.dto.Responses;
import com.e2d.odin.adapter.web.security.CurrentActor;
import com.e2d.odin.application.usage.GetUsageStats;
import com.e2d.odin.application.usage.ListGaps;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Uso descriptivo y huecos de una empresa, por mes. */
@RestController
public class UsageController {

    private final GetUsageStats usageStats;
    private final ListGaps gaps;

    public UsageController(GetUsageStats usageStats, ListGaps gaps) {
        this.usageStats = usageStats;
        this.gaps = gaps;
    }

    @GetMapping("/v1/admin/companies/{companyId}/usage-stats")
    public Responses.UsageStatsView usageStats(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestParam Optional<String> month) {
        return Responses.UsageStatsView.from(usageStats.execute(actor, new CompanyId(companyId), month));
    }

    @GetMapping("/v1/admin/companies/{companyId}/gaps")
    public List<Responses.GapView> gaps(@RequestAttribute(CurrentActor.ATTRIBUTE) Actor actor,
            @PathVariable UUID companyId, @RequestParam Optional<String> month) {
        return gaps.execute(actor, new CompanyId(companyId), month).stream().map(Responses.GapView::from).toList();
    }
}
