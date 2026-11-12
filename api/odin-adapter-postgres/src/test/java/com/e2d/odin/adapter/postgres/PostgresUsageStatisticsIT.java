package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.identity.MemberView;
import com.e2d.odin.application.usage.CardCounts;
import com.e2d.odin.application.usage.CardDecisions;
import com.e2d.odin.application.usage.CompanyUsage;
import com.e2d.odin.application.usage.GapGroup;
import com.e2d.odin.application.usage.GapReport;
import com.e2d.odin.application.usage.PersonUsage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.knowledge.CardReview;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.knowledge.QuestionKind;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.knowledge.ReviewDecision;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresUsageStatisticsIT extends AbstractPostgresIT {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final Instant IN_NOVEMBER = Instant.parse("2026-11-10T09:00:00Z");

    private final PostgresQuestionLog questions = new PostgresQuestionLog(tx);
    private final PostgresUsageStatistics usage = new PostgresUsageStatistics(tx);

    private UserId person(CompanyId company, String firstName, String lastName) {
        UserId id = new UserId(UUID.randomUUID());
        User base = person(id);
        users.save(base.withNames(firstName, Optional.of(lastName)));
        memberships.save(new Membership(id, company, Set.of(Role.WORKER), true));
        return id;
    }

    private void ask(CompanyId company, UserId author, String text, int hits, Instant at, QuestionKind kind) {
        questions.record(new QuestionRecord(UUID.randomUUID(), company, author, text, hits, at, kind));
    }

    private void ask(CompanyId company, UserId author, String text, int hits, Instant at) {
        ask(company, author, text, hits, at, QuestionKind.SEARCH);
    }

    private KnowledgeCard save(CompanyId company, UserId author, Instant at, CardStatus status,
            Optional<CardReview> review) {
        var card = new KnowledgeCard(new CardId(UUID.randomUUID()), company, Optional.empty(), "Título", "Cuerpo",
                Optional.empty(), author, at, status, review);
        cards.save(card);
        return card;
    }

    private void saveCard(CompanyId company, UserId author, Instant at) {
        save(company, author, at, CardStatus.PENDING, Optional.empty());
    }

    private void saveDecided(CompanyId company, UserId author, ReviewDecision decision, CardStatus status) {
        var review = new CardReview(author, decision, IN_NOVEMBER, Optional.empty());
        save(company, author, IN_NOVEMBER, status, Optional.of(review));
    }

    /** Tres personas distintas de la empresa, el mínimo para que los huecos y el desglose de fichas se enseñen. */
    private List<UserId> threePeople(CompanyId company) {
        return List.of(person(company, "Ana", "Pérez"), person(company, "Beatriz", "Mora"),
                person(company, "Carlos", "Ruiz"));
    }

    private List<PersonUsage> people(CompanyId company) {
        return usage.peopleUsage(company, NOVEMBER, MADRID);
    }

    @Test
    void countsQuestionsByKindAndWithoutResults() {
        CompanyId company = newCompany();
        UserId ana = person(company, "Ana", "Pérez");
        ask(company, ana, "uno", 3, IN_NOVEMBER);
        ask(company, ana, "dos", 0, IN_NOVEMBER);
        ask(company, ana, "tres", 0, IN_NOVEMBER, QuestionKind.SIMILAR_CASES);
        ask(company, ana, "cuatro", 2, IN_NOVEMBER, QuestionKind.SIMILAR_CASES);
        ask(company, ana, "octubre", 0, Instant.parse("2026-10-10T09:00:00Z"));

        CompanyUsage result = usage.companyUsage(company, NOVEMBER, MADRID);

        assertThat(result.questions().search()).isEqualTo(2);
        assertThat(result.questions().similarCases()).isEqualTo(2);
        assertThat(result.questionsWithoutResults()).isEqualTo(2);
    }

    @Test
    void countsCardsByStatusAtCompanyLevelOnly() {
        CompanyId company = newCompany();
        List<UserId> authors = threePeople(company);
        UserId ana = authors.get(0);
        saveCard(company, ana, IN_NOVEMBER);
        saveDecided(company, authors.get(1), ReviewDecision.APPROVE, CardStatus.APPROVED);
        saveDecided(company, authors.get(2), ReviewDecision.CORRECT, CardStatus.APPROVED);
        saveDecided(company, ana, ReviewDecision.REJECT, CardStatus.REJECTED);
        saveCard(company, ana, Instant.parse("2026-10-10T09:00:00Z"));

        assertThat(usage.companyUsage(company, NOVEMBER, MADRID).cards()).isEqualTo(
                new CardCounts(4, 1, Optional.of(new CardDecisions(1, 1, 1)), false));
        assertThat(people(company)).filteredOn(p -> p.userId().equals(Optional.of(ana)))
                .singleElement().satisfies(p -> assertThat(p.cardsSaved()).isEqualTo(2));
        assertThat(PersonUsage.class.getRecordComponents()).extracting(c -> c.getName()).containsExactly("userId",
                "displayName", "memberCode", "questions", "cardsSaved", "departed");
    }

    @Test
    void cardBreakdownIsWithheldWithTwoAuthorsAndShownWithThree() {
        CompanyId company = newCompany();
        List<UserId> authors = threePeople(company);
        saveDecided(company, authors.get(0), ReviewDecision.APPROVE, CardStatus.APPROVED);
        saveDecided(company, authors.get(1), ReviewDecision.REJECT, CardStatus.REJECTED);

        assertThat(usage.companyUsage(company, NOVEMBER, MADRID).cards())
                .isEqualTo(new CardCounts(2, 0, Optional.empty(), true));

        saveDecided(company, authors.get(2), ReviewDecision.CORRECT, CardStatus.APPROVED);

        assertThat(usage.companyUsage(company, NOVEMBER, MADRID).cards())
                .isEqualTo(new CardCounts(3, 0, Optional.of(new CardDecisions(1, 1, 1)), false));
    }

    @Test
    void countsDocumentsByStatus() {
        CompanyId company = newCompany();
        UserId uploader = person(company, "Ana", "Pérez");
        for (DocumentStatus status : new DocumentStatus[] {DocumentStatus.READY, DocumentStatus.READY,
                DocumentStatus.FAILED, DocumentStatus.PENDING_READER, DocumentStatus.READER_PROCESSING,
                DocumentStatus.RECEIVED}) {
            newDocument(company, uploader, "doc " + status, status);
        }

        var documents = usage.companyUsage(company, NOVEMBER, MADRID).documents();

        assertThat(documents.uploaded()).isEqualTo(6);
        assertThat(documents.ready()).isEqualTo(2);
        assertThat(documents.failed()).isEqualTo(1);
        assertThat(documents.waitingForReader()).isEqualTo(2);
        assertThat(usage.companyUsage(company, YearMonth.of(2026, 10), MADRID).documents().uploaded()).isZero();
    }

    @Test
    void peopleAreOrderedByNameWhateverTheirCounts() {
        CompanyId company = newCompany();
        UserId zoe = person(company, "Zoe", "Zamora");
        person(company, "Álvaro", "Gil");
        UserId beatriz = person(company, "Beatriz", "Mora");
        for (int i = 0; i < 5; i++) {
            ask(company, zoe, "pregunta " + i, 1, IN_NOVEMBER);
            saveCard(company, zoe, IN_NOVEMBER);
        }
        ask(company, beatriz, "una", 1, IN_NOVEMBER);

        assertThat(people(company)).extracting(PersonUsage::displayName)
                .containsExactly("Álvaro Gil", "Beatriz Mora", "Zoe Zamora");
        assertThat(people(company)).extracting(PersonUsage::questions).containsExactly(0, 1, 5);
    }

    @Test
    void peopleCarryMemberCodeLikeMemberView() {
        CompanyId company = newCompany();
        UserId ana = person(company, "Ana", "Pérez");
        User user = users.findById(ana).orElseThrow();
        Membership membership = memberships.find(ana, company).orElseThrow();
        String expected = MemberView.of(user, membership, orgs.findCompany(company).orElseThrow().code(),
                new com.e2d.odin.domain.identity.Actor(ana, "x", Role.GROUP_ADMIN, Set.of())).memberCode();

        assertThat(people(company)).singleElement().satisfies(p -> {
            assertThat(p.memberCode()).contains(expected);
            assertThat(p.userId()).contains(ana);
        });
        assertThat(expected).isEqualTo(user.personCode() + "-" + orgs.findCompany(company).orElseThrow().code());
    }

    @Test
    void personWithLiveMembershipAndNoActivityAppearsWithZeros() {
        CompanyId company = newCompany();
        person(company, "Ana", "Pérez");

        assertThat(people(company)).singleElement().satisfies(p -> {
            assertThat(p.questions()).isZero();
            assertThat(p.cardsSaved()).isZero();
            assertThat(p.departed()).isFalse();
        });
    }

    @Test
    void departedPeopleAreSummedInOneAnonymousRow() {
        CompanyId company = newCompany();
        UserId ana = person(company, "Ana", "Pérez");
        UserId bea = person(company, "Beatriz", "Mora");
        UserId carlos = person(company, "Carlos", "Ruiz");
        ask(company, bea, "a", 1, IN_NOVEMBER);
        ask(company, bea, "b", 1, IN_NOVEMBER);
        ask(company, carlos, "c", 1, IN_NOVEMBER);
        saveCard(company, carlos, IN_NOVEMBER);
        depart(company, bea);
        depart(company, carlos);

        List<PersonUsage> result = people(company);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).userId()).contains(ana);
        assertThat(result.get(1)).isEqualTo(PersonUsage.departed(3, 1));
        assertThat(result.get(1).memberCode()).isEmpty();
    }

    @Test
    void departedRowIsDroppedWithOnePersonAndShownWithTwo() {
        CompanyId company = newCompany();
        UserId ana = person(company, "Ana", "Pérez");
        UserId bea = person(company, "Beatriz", "Mora");
        UserId carlos = person(company, "Carlos", "Ruiz");
        ask(company, bea, "a", 1, IN_NOVEMBER);
        ask(company, carlos, "c", 1, IN_NOVEMBER);
        depart(company, bea);

        assertThat(people(company)).extracting(PersonUsage::userId)
                .containsExactly(Optional.of(ana), Optional.of(carlos));

        depart(company, carlos);

        assertThat(people(company)).hasSize(2).last().isEqualTo(PersonUsage.departed(2, 0));
        assertThat(people(company).get(0).userId()).contains(ana);
    }

    private void depart(CompanyId company, UserId person) {
        memberships.delete(person, company, IN_NOVEMBER);
        users.delete(person, IN_NOVEMBER);
    }

    @Test
    void gapsGroupIgnoringAccentsCaseAndSpaces() {
        CompanyId company = newCompany();
        List<UserId> askers = threePeople(company);
        ask(company, askers.get(0), "Cuánto cuesta  el AGUA", 0, Instant.parse("2026-11-03T09:00:00Z"));
        ask(company, askers.get(1), "cuanto cuesta el agua", 0, Instant.parse("2026-11-05T09:00:00Z"));
        ask(company, askers.get(2), " CUANTO   cuesta el agua ", 0, Instant.parse("2026-11-04T09:00:00Z"));
        ask(company, askers.get(0), "otra cosa", 0, Instant.parse("2026-11-08T09:00:00Z"), QuestionKind.SIMILAR_CASES);
        ask(company, askers.get(0), "con resultados", 4, Instant.parse("2026-11-09T09:00:00Z"));

        List<GapGroup> gaps = usage.gaps(company, NOVEMBER, MADRID, 200).groups();

        assertThat(gaps.get(0).text()).isEqualTo("otra cosa");
        assertThat(gaps.get(1).text()).isIn("Cuánto cuesta  el AGUA", "cuanto cuesta el agua",
                " CUANTO   cuesta el agua ");
        assertThat(gaps).extracting(GapGroup::times).containsExactly(1, 3);
        assertThat(gaps).extracting(GapGroup::kind).containsExactly(QuestionKind.SIMILAR_CASES, QuestionKind.SEARCH);
        assertThat(gaps.get(1).lastAskedOn()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(usage.gaps(company, NOVEMBER, MADRID, 1).groups()).hasSize(1);
    }

    @Test
    void gapsHaveNoAuthorAndOnlyTheDay() {
        CompanyId company = newCompany();
        List<UserId> askers = threePeople(company);
        askers.forEach(a -> ask(company, a, "algo", 0, Instant.parse("2026-11-10T23:30:00Z")));

        GapGroup gap = usage.gaps(company, NOVEMBER, MADRID, 200).groups().get(0);

        assertThat(gap.lastAskedOn()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(GapGroup.class.getRecordComponents()).extracting(c -> c.getName())
                .containsExactly("text", "kind", "times", "lastAskedOn");
    }

    @Test
    void gapsAreWithheldWithTwoPeopleAndShownWithThree() {
        CompanyId company = newCompany();
        List<UserId> askers = threePeople(company);
        ask(company, askers.get(0), "uno", 0, IN_NOVEMBER);
        ask(company, askers.get(0), "dos", 0, IN_NOVEMBER);
        ask(company, askers.get(1), "tres", 0, IN_NOVEMBER);

        assertThat(usage.gaps(company, NOVEMBER, MADRID, 200)).isEqualTo(new GapReport(List.of(), true));

        ask(company, askers.get(2), "cuatro", 0, IN_NOVEMBER);

        GapReport shown = usage.gaps(company, NOVEMBER, MADRID, 200);
        assertThat(shown.withheld()).isFalse();
        assertThat(shown.groups()).hasSize(4);
    }

    @Test
    void nothingOfAnotherCompanyIsCounted() {
        CompanyId mine = newCompany();
        CompanyId other = newCompany();
        UserId shared = person(mine, "Ana", "Pérez");
        memberships.save(new Membership(shared, other, Set.of(Role.WORKER), true));
        UserId stranger = person(other, "Zacarías", "Otro");
        ask(other, shared, "secreto de la otra", 0, IN_NOVEMBER);
        ask(other, stranger, "otro secreto", 0, IN_NOVEMBER);
        saveCard(other, stranger, IN_NOVEMBER);
        newDocument(other, stranger, "de la otra", DocumentStatus.READY);

        CompanyUsage result = usage.companyUsage(mine, NOVEMBER, MADRID);

        assertThat(result.questionsWithoutResults()).isZero();
        assertThat(result.cards().saved()).isZero();
        assertThat(result.documents().uploaded()).isZero();
        assertThat(people(mine)).extracting(PersonUsage::displayName).containsExactly("Ana Pérez");
        assertThat(people(mine)).extracting(PersonUsage::questions).containsExactly(0);
        assertThat(usage.gaps(mine, NOVEMBER, MADRID, 200)).isEqualTo(new GapReport(List.of(), false));
    }

    @Test
    void monthBoundaryUsesInstallationZone() {
        CompanyId company = newCompany();
        List<UserId> askers = threePeople(company);
        for (UserId asker : askers) {
            ask(company, asker, "madrugada de noviembre", 0, Instant.parse("2026-10-31T23:30:00Z"));
            ask(company, asker, "madrugada de diciembre", 0, Instant.parse("2026-11-30T23:30:00Z"));
        }

        assertThat(usage.companyUsage(company, NOVEMBER, MADRID).questions().search()).isEqualTo(3);
        assertThat(usage.gaps(company, NOVEMBER, MADRID, 200).groups()).extracting(GapGroup::text)
                .containsExactly("madrugada de noviembre");
        assertThat(usage.companyUsage(company, NOVEMBER, ZoneId.of("UTC")).questions().search()).isEqualTo(3);
        assertThat(usage.gaps(company, NOVEMBER, ZoneId.of("UTC"), 200).groups()).extracting(GapGroup::text)
                .containsExactly("madrugada de diciembre");
    }
}
