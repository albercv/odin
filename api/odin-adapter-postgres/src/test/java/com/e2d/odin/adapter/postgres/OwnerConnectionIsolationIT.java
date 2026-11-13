package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.casefile.CaseFileAction;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.knowledge.QuestionKind;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.search.SearchQuery;
import com.e2d.odin.domain.search.SimilarCasesQuery;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El rol dueño se salta la RLS: el filtro de empresa del propio SQL tiene que bastar. */
class OwnerConnectionIsolationIT extends AbstractPostgresIT {

    private final ScopedTransactions ownerTx = new ScopedTransactions(Db.owner());
    private final PostgresDocumentRepository ownerDocs = new PostgresDocumentRepository(ownerTx);
    private final PostgresKnowledgeCardRepository ownerCards = new PostgresKnowledgeCardRepository(ownerTx);
    private final PostgresKnowledgeSearch ownerSearch = new PostgresKnowledgeSearch(ownerTx);
    private final PostgresProjectMemberRepository ownerProjectMembers = new PostgresProjectMemberRepository(ownerTx);
    private final PostgresMembershipRepository ownerMemberships = new PostgresMembershipRepository(ownerTx);
    private final PostgresOrganizationRepository ownerOrgs = new PostgresOrganizationRepository(ownerTx);
    private final PostgresTopicRepository topics = new PostgresTopicRepository(tx);
    private final PostgresTopicRepository ownerTopics = new PostgresTopicRepository(ownerTx);
    private final PostgresTaggingRepository tagging = new PostgresTaggingRepository(tx);
    private final PostgresTaggingRepository ownerTagging = new PostgresTaggingRepository(ownerTx);

    private CompanyId a;
    private CompanyId b;
    private Document documentOfA;
    private KnowledgeCard pendingOfA;
    private UserId pendingAuthor;

    @BeforeEach
    void seedCompanyA() {
        a = newCompany();
        b = newCompany();
        documentOfA = newDocument(a, newUser(a), "Póliza de A", DocumentStatus.READY);
        docs.replacePages(documentOfA.id(),
                List.of(new DocumentPage(documentOfA.id(), 1, "filtración secreta de A", "lector", "1")));
        docs.markCaseDocuments(a, Set.of(documentOfA.id()), true);
        pendingOfA = card(CardStatus.PENDING);
        pendingAuthor = pendingOfA.authorId();
        new PostgresQuestionLog(tx).record(new QuestionRecord(UUID.randomUUID(), a, pendingAuthor, "filtración", 1, NOW,
                QuestionKind.SEARCH));
        card(CardStatus.APPROVED);
    }

    private KnowledgeCard card(CardStatus status) {
        KnowledgeCard c = new KnowledgeCard(new CardId(UUID.randomUUID()), a, Optional.empty(), "Ficha de A",
                "filtración resuelta en A", Optional.empty(), newUser(a), NOW, status, Optional.empty());
        cards.save(c);
        return c;
    }

    private int readEverything(CompanyScope scope) {
        int seen = ownerDocs.findById(scope, ProjectVisibility.all(), documentOfA.id()).stream().toList().size();
        seen += ownerDocs.list(scope, ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10).size();
        seen += ownerDocs.findPage(scope, ProjectVisibility.all(), documentOfA.id(), 1).stream().toList().size();
        seen += ownerCards.findForReview(scope, ProjectVisibility.all(), pendingOfA.id()).stream().toList().size();
        seen += ownerCards.listByStatus(scope, ProjectVisibility.all(), CardStatus.PENDING).size();
        seen += ownerDocs.list(scope, ProjectVisibility.all(), Optional.empty(), Optional.empty(), Optional.of(true),
                Optional.empty(), 0, 10).size();
        seen += ownerCards.listByAuthor(scope, ProjectVisibility.all(), pendingAuthor, Optional.empty(), 100).size();
        CompanyId asked = scope.allCompanies() ? a : scope.companies().iterator().next();
        seen += new PostgresQuestionLog(ownerTx).listByAuthor(asked, pendingAuthor, 50).size();
        seen += ownerSearch.findSimilarCases(new SimilarCasesQuery(List.of("filtración"), scope,
                ProjectVisibility.all(), Optional.empty(), 10, pendingAuthor)).cases().size();
        return seen + ownerSearch.search(new SearchQuery(List.of("filtración"), scope, ProjectVisibility.all(),
                Optional.empty(), 10, pendingAuthor)).hits().size();
    }

    @Test
    void caseFilesRespectScopeAndVisibilityThroughAConnectionThatBypassesRls() {
        ProjectId project = newProject(a, "Obra");
        UserId user = newUser(a);
        Document theCase = projectDocument(project, user).withCaseDocument(true);
        Document member = projectDocument(project, user);
        docs.save(theCase);
        docs.markCaseDocuments(a, Set.of(theCase.id()), true);
        docs.save(member);
        var files = new PostgresCaseFileRepository(tx);
        var ownerFiles = new PostgresCaseFileRepository(ownerTx);
        files.add(new CaseFileEntry(a, theCase.id(), member.id(), user, NOW));
        files.record(new CaseFileEvent(UUID.randomUUID(), a, theCase.id(), member.id(), CaseFileAction.ADDED, user,
                NOW));
        var none = ProjectVisibility.of(Set.of());

        assertThat(ownerFiles.entries(CompanyScope.of(a), ProjectVisibility.of(Set.of(project)), theCase.id()))
                .hasSize(1);
        assertThat(ownerFiles.events(CompanyScope.of(a), ProjectVisibility.of(Set.of(project)), theCase.id()))
                .hasSize(1);
        assertThat(ownerFiles.entries(CompanyScope.of(b), ProjectVisibility.all(), theCase.id())).isEmpty();
        assertThat(ownerFiles.events(CompanyScope.of(b), ProjectVisibility.all(), theCase.id())).isEmpty();
        assertThat(ownerFiles.entries(CompanyScope.of(a), none, theCase.id())).isEmpty();
        assertThat(ownerFiles.events(CompanyScope.of(a), none, theCase.id())).isEmpty();
    }

    private Document projectDocument(ProjectId project, UserId user) {
        return new Document(new DocumentId(UUID.randomUUID()), a, Optional.of(project), "Doc", "f.pdf",
                DocumentFormat.PDF, UUID.randomUUID().toString(), DocumentStatus.READY, Optional.empty(), true, user,
                NOW, Optional.empty(), 0, Optional.empty(), Optional.empty());
    }

    @Test
    void withdrawnDocumentIsHiddenThroughAConnectionThatBypassesRls() {
        docs.markWithdrawal(documentOfA.withWithdrawal(Optional.of(NOW), Optional.of(documentOfA.uploadedBy())));
        CompanyScope scope = CompanyScope.of(a);
        ProjectVisibility all = ProjectVisibility.all();

        assertThat(ownerDocs.findEntry(scope, all, documentOfA.id())).isEmpty();
        assertThat(ownerDocs.findPage(scope, all, documentOfA.id(), 1)).isEmpty();
        assertThat(ownerDocs.list(scope, all, Optional.empty(), Optional.empty(), 0, 10)).isEmpty();
        assertThat(ownerSearch.search(new SearchQuery(List.of("filtración"), scope, all, Optional.empty(), 10,
                pendingAuthor)).hits()).extracting(h -> h.sourceId()).doesNotContain(documentOfA.id().value());
        assertThat(ownerSearch.findSimilarCases(new SimilarCasesQuery(List.of("filtración"), scope, all,
                Optional.empty(), 10, pendingAuthor)).cases()).extracting(c -> c.sourceId())
                .doesNotContain(documentOfA.id().value());
    }

    @Test
    void pageCountsRespectCompanyAndWithdrawalThroughAConnectionThatBypassesRls() {
        var ownerFiles = new PostgresCaseFileRepository(ownerTx);
        var ids = List.of(documentOfA.id());

        assertThat(ownerFiles.pageCounts(CompanyScope.of(a), ProjectVisibility.all(), ids)).containsEntry(
                documentOfA.id(), 1);
        assertThat(ownerFiles.pageCounts(CompanyScope.of(b), ProjectVisibility.all(), ids)).isEmpty();

        docs.markWithdrawal(documentOfA.withWithdrawal(Optional.of(NOW), Optional.of(documentOfA.uploadedBy())));

        assertThat(ownerFiles.pageCounts(CompanyScope.of(a), ProjectVisibility.all(), ids)).isEmpty();
    }

    @Test
    void scopeOfBReadsNothingOfAThroughAConnectionThatBypassesRls() {
        assertThat(readEverything(CompanyScope.of(b))).isZero();
    }

    @Test
    void sameConnectionStillReadsAWithItsOwnScope() {
        assertThat(readEverything(CompanyScope.of(a))).isEqualTo(14);
        assertThat(readEverything(CompanyScope.all())).isEqualTo(14);
    }

    @Test
    void closedProjectIsHiddenFromEmptyVisibilityThroughAConnectionThatBypassesRls() {
        ProjectId project = newProject(a, "Cerrado");
        UserId user = newUser(a);
        Document closed = new Document(new DocumentId(UUID.randomUUID()), a, Optional.of(project), "Cerrado",
                "f.pdf", DocumentFormat.PDF, UUID.randomUUID().toString(), DocumentStatus.READY, Optional.empty(),
                true, user, NOW, Optional.empty(), 0, Optional.empty(), Optional.empty());
        docs.save(closed);
        docs.replacePages(closed.id(), List.of(new DocumentPage(closed.id(), 1, "zafiro cerrado", "lector", "1")));
        KnowledgeCard closedCard = new KnowledgeCard(new CardId(UUID.randomUUID()), a, Optional.of(project),
                "Ficha cerrada", "zafiro cerrado", Optional.empty(), user, NOW, CardStatus.PENDING,
                Optional.empty());
        cards.save(closedCard);
        CompanyScope scope = CompanyScope.of(a);
        ProjectVisibility none = ProjectVisibility.of(Set.of());

        assertThat(ownerDocs.findById(scope, none, closed.id())).isEmpty();
        assertThat(ownerDocs.list(scope, none, Optional.of(project), Optional.empty(), 0, 10)).isEmpty();
        assertThat(ownerDocs.findPage(scope, none, closed.id(), 1)).isEmpty();
        assertThat(ownerCards.findForReview(scope, none, closedCard.id())).isEmpty();
        assertThat(ownerCards.listByStatus(scope, none, CardStatus.PENDING))
                .extracting(KnowledgeCard::id).doesNotContain(closedCard.id());
        assertThat(ownerSearch.search(new SearchQuery(List.of("zafiro"), scope, none, Optional.empty(), 10, user))
                .hits()).isEmpty();
        assertThat(ownerSearch.findSimilarCases(new SimilarCasesQuery(List.of("zafiro"), scope, none,
                Optional.empty(), 10, user)).cases()).isEmpty();
        assertThat(ownerCards.listByAuthor(scope, none, user, Optional.empty(), 100)).isEmpty();
    }

    @Test
    void closedProjectCasesAreHiddenFromEmptyVisibilityThroughAConnectionThatBypassesRls() {
        ProjectId project = newProject(a, "Cerrado");
        UserId user = newUser(a);
        Document closed = new Document(new DocumentId(UUID.randomUUID()), a, Optional.of(project), "Cerrado",
                "f.pdf", DocumentFormat.PDF, UUID.randomUUID().toString(), DocumentStatus.READY, Optional.empty(),
                true, user, NOW, Optional.empty(), 0, Optional.empty(), Optional.empty());
        docs.save(closed);
        docs.markCaseDocuments(a, Set.of(closed.id()), true);
        docs.replacePages(closed.id(), List.of(new DocumentPage(closed.id(), 1, "zafiro cerrado", "lector", "1")));
        cards.save(new KnowledgeCard(new CardId(UUID.randomUUID()), a, Optional.of(project), "Ficha aprobada cerrada",
                "zafiro cerrado", Optional.empty(), user, NOW, CardStatus.APPROVED, Optional.empty()));
        CompanyScope scope = CompanyScope.of(a);
        var none = ProjectVisibility.of(Set.of());

        assertThat(ownerSearch.findSimilarCases(new SimilarCasesQuery(List.of("zafiro"), scope, none,
                Optional.empty(), 10, Db.NOBODY)).cases()).isEmpty();
        assertThat(ownerDocs.list(scope, none, Optional.empty(), Optional.empty(), Optional.of(true),
                Optional.empty(), 0, 10)).extracting(e -> e.document().id()).doesNotContain(closed.id());
        assertThat(ownerSearch.findSimilarCases(new SimilarCasesQuery(List.of("zafiro"), scope,
                ProjectVisibility.of(Set.of(project)), Optional.empty(), 10, Db.NOBODY)).cases()).hasSize(2);
    }

    @Test
    void caseMarkOfAnotherCompanyChangesNothingThroughAConnectionThatBypassesRls() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> ownerDocs.markCaseDocuments(b, Set.of(documentOfA.id()), false))
                .isInstanceOf(com.e2d.odin.domain.shared.NotFoundException.class);

        assertThat(ownerDocs.findById(CompanyScope.of(a), ProjectVisibility.all(), documentOfA.id()).orElseThrow()
                .caseDocument()).isTrue();
        assertThat(ownerDocs.list(CompanyScope.of(b), ProjectVisibility.all(), Optional.empty(), Optional.empty(),
                Optional.of(true), Optional.empty(), 0, 10)).isEmpty();
    }

    @Test
    void scopeOfBSeesNeitherProjectMembersNorMembershipsOfAThroughAConnectionThatBypassesRls() {
        ProjectId project = newProject(a, "Obra");
        UserId wanda = newUser(a);
        UserId pedro = newUser(a);
        projectMembers.add(a, project, wanda);

        assertThat(ownerProjectMembers.listMembers(b, project)).isEmpty();
        assertThat(ownerProjectMembers.projectsOf(b, wanda)).isEmpty();
        assertThat(ownerProjectMembers.deletedProjectsOf(b, wanda)).isEmpty();
        assertThat(ownerMemberships.find(wanda, b)).isEmpty();
        assertThat(ownerMemberships.listByCompany(b)).isEmpty();
        ownerProjectMembers.add(b, project, pedro);
        ownerProjectMembers.remove(b, project, wanda);
        ownerProjectMembers.removeAllOf(b, wanda);
        assertThat(projectMembers.listMembers(a, project)).containsExactly(wanda);
        assertThat(ownerProjectMembers.listMembers(a, project)).containsExactly(wanda);
        assertThat(ownerMemberships.find(wanda, a)).isPresent();
    }

    @Test
    void listCompaniesFiltersByScopeThroughAConnectionThatBypassesRls() {
        assertThat(ownerOrgs.listCompanies(CompanyScope.of(b))).extracting(c -> c.id()).containsExactly(b);
        assertThat(ownerOrgs.listCompanies(CompanyScope.of(a))).extracting(c -> c.id()).containsExactly(a);
        assertThat(ownerOrgs.listCompanies(CompanyScope.all())).extracting(c -> c.id())
                .containsExactlyInAnyOrder(a, b);
    }

    @Test
    void markIntoAnotherCompanyResolvesNothingThroughAConnectionThatBypassesRls() {
        Document foreign = newDocument(b, newUser(b), "Ajeno de B", DocumentStatus.READY);
        docs.replacePages(foreign.id(), List.of(new DocumentPage(foreign.id(), 1, "baliza vieja", "lector", "1")));
        forceMarkAcrossCompanies(foreign, documentOfA);
        CompanyScope scope = CompanyScope.of(b);

        var entry = ownerDocs.findEntry(scope, ProjectVisibility.all(), foreign.id()).orElseThrow();
        assertThat(entry.supersededBy()).isEmpty();
        assertThat(entry.replacement()).isEmpty();
        assertThat(ownerDocs.list(scope, ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10))
                .allSatisfy(e -> {
                    assertThat(e.supersededBy()).isEmpty();
                    assertThat(e.replacement()).isEmpty();
                });
        var hits = ownerSearch.search(new SearchQuery(List.of("baliza"), scope, ProjectVisibility.all(),
                Optional.empty(), 10, Db.NOBODY)).hits();
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).replacement()).isEmpty();
    }

    @Test
    void projectFilterNeverBringsCompanyWideDocumentsOfAnotherCompanyThroughAConnectionThatBypassesRls() {
        ProjectId projectOfB = newProject(b, "Obra de B");
        var query = new SearchQuery(List.of("filtración"), CompanyScope.of(b), ProjectVisibility.all(),
                Optional.of(projectOfB), 10, Db.NOBODY);
        assertThat(ownerSearch.search(query).hits()).isEmpty();
    }

    private Topic topicOf(CompanyId company, String name) {
        Topic topic = new Topic(new TopicId(UUID.randomUUID()), company, name, "Definición de " + name);
        topics.save(topic);
        return topic;
    }

    @Test
    void scopeOfBCannotReadOrChangeTopicsOfAThroughAConnectionThatBypassesRls() {
        Topic water = topicOf(a, "Agua");
        int versionOfA = topics.listVersion(a);

        assertThat(ownerTopics.find(b, water.id())).isEmpty();
        assertThat(ownerTopics.list(b)).isEmpty();
        ownerTopics.save(new Topic(water.id(), b, "Robado", "x"));
        ownerTopics.delete(b, water.id(), NOW);

        assertThat(topics.find(a, water.id())).contains(water);
        assertThat(topics.listVersion(a)).isEqualTo(versionOfA);
    }

    @Test
    void scopeOfBReadsNoTopicsOrTaggingOfAAndDecidesNothingThroughAConnectionThatBypassesRls() {
        Topic water = topicOf(a, "Agua");
        Topic fires = topicOf(a, "Incendios");
        UserId admin = newUser(a);
        tagging.decide(CompanyScope.of(a), documentOfA.id(), water.id(), TopicState.ACTIVE, admin, NOW);
        CompanyScope scopeOfB = CompanyScope.of(b);
        var all = ProjectVisibility.all();

        ownerTagging.decide(scopeOfB, documentOfA.id(), fires.id(), TopicState.ACTIVE, newUser(b), NOW);
        ownerTagging.retag(scopeOfB, documentOfA.id());

        assertThat(ownerTagging.topicsOf(scopeOfB, documentOfA.id())).isEmpty();
        assertThat(ownerTagging.activeTopicsOf(scopeOfB, all, List.of(documentOfA.id()))).isEmpty();
        assertThat(ownerTagging.taggingOf(scopeOfB, all, List.of(documentOfA.id()))).isEmpty();
        assertThat(ownerTagging.pendingCount(b, all)).isZero();
        assertThat(ownerTagging.backlog(b).pendingDocuments()).isZero();
        assertThat(tagging.topicsOf(CompanyScope.of(a), documentOfA.id())).extracting(t -> t.topicId())
                .containsExactly(water.id());
        assertThat(ownerTagging.taggingOf(CompanyScope.of(a), all, List.of(documentOfA.id()))).hasSize(1);
    }

    @Test
    void topicFilterOfAnotherCompanyBringsNoDocumentsThroughAConnectionThatBypassesRls() {
        Topic water = topicOf(a, "Agua");
        tagging.decide(CompanyScope.of(a), documentOfA.id(), water.id(), TopicState.ACTIVE, newUser(a), NOW);
        newDocument(b, newUser(b), "Propio de B", DocumentStatus.READY);

        assertThat(ownerDocs.list(CompanyScope.of(b), ProjectVisibility.all(), Optional.empty(),
                Optional.of(water.id()), Optional.empty(), 0, 10)).isEmpty();
        assertThat(ownerDocs.list(CompanyScope.of(a), ProjectVisibility.all(), Optional.empty(),
                Optional.of(water.id()), Optional.empty(), 0, 10)).hasSize(1);
    }

    @Test
    void scopeOfBCountsNothingOfAUsageGapsOrDeliveredVolumeThroughAConnectionThatBypassesRls() {
        var month = java.time.YearMonth.of(2026, 11);
        var zone = java.time.ZoneId.of("Europe/Madrid");
        var ownerUsage = new PostgresUsageStatistics(ownerTx);
        var ownerMeter = new PostgresDeliveryMeter(ownerTx, zone);
        for (int i = 0; i < 3; i++) {
            new PostgresQuestionLog(tx).record(new QuestionRecord(UUID.randomUUID(), a, newUser(a), "hueco secreto", 0,
                    NOW, QuestionKind.SEARCH));
        }
        ownerMeter.record(a, com.e2d.odin.application.usage.WorkerTool.SEARCH, 500, NOW);

        var usageOfB = ownerUsage.companyUsage(b, month, zone);
        assertThat(usageOfB.questions().search()).isZero();
        assertThat(usageOfB.questionsWithoutResults()).isZero();
        assertThat(usageOfB.cards().saved()).isZero();
        assertThat(usageOfB.documents().uploaded()).isZero();
        assertThat(ownerUsage.peopleUsage(b, month, zone)).isEmpty();
        assertThat(ownerUsage.gaps(b, month, zone, 200).groups()).isEmpty();
        assertThat(ownerMeter.totals(b, month)).isEmpty();

        assertThat(ownerUsage.companyUsage(a, month, zone).questionsWithoutResults()).isEqualTo(3);
        assertThat(ownerUsage.peopleUsage(a, month, zone)).isNotEmpty();
        assertThat(ownerUsage.gaps(a, month, zone, 200).groups()).extracting(g -> g.text())
                .containsExactly("hueco secreto");
        assertThat(ownerMeter.totals(a, month)).hasSize(1);
    }
}
