package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.link.AccessLink;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresAccessLinkRepositoryIT extends AbstractPostgresIT {

    private final PostgresAccessLinkRepository links = new PostgresAccessLinkRepository(tx);

    private AccessLink saved() {
        CompanyId company = newCompany();
        UserId user = newUser(company);
        Document doc = new Document(new DocumentId(UUID.randomUUID()), company, Optional.empty(), "Ficha", "f.pdf",
                DocumentFormat.PDF, UUID.randomUUID().toString(), DocumentStatus.READY, Optional.empty(), true, user,
                NOW, Optional.empty(), 0, Optional.empty(), Optional.empty());
        docs.save(doc);
        AccessLink link = new AccessLink(UUID.randomUUID(), AccessLinkKind.ORIGINAL, company, user, Optional.empty(),
                Optional.of(doc.id()), NOW, NOW.plusSeconds(900), Optional.empty(), 0);
        links.save(link, "hash-" + link.id());
        return link;
    }

    @Test
    void bindKeepsTheFirstAddress() {
        AccessLink link = saved();

        assertThat(links.bind(link, "10.0.0.1")).contains("10.0.0.1");
        assertThat(links.bind(link, "10.0.0.2")).contains("10.0.0.1");
    }

    @Test
    void bindOfALinkDeletedMeanwhileIsEmptyInsteadOfAnError() {
        AccessLink link = saved();
        links.deleteOf(link.companyId(), link.userId());

        assertThat(links.bind(link, "10.0.0.1")).isEmpty();
    }
}
