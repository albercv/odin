package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.FakeSecretHasher;
import com.e2d.odin.application.fake.InMemoryAdminSessionRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceTokenGenerator;
import com.e2d.odin.application.identity.MemberChanges;
import com.e2d.odin.application.identity.MemberView;
import com.e2d.odin.application.identity.ResolveActor;
import com.e2d.odin.application.identity.StoredAdminSessions;
import com.e2d.odin.application.identity.UpdateMember;
import com.e2d.odin.application.search.ListDocuments;
import com.e2d.odin.application.shared.SessionSettings;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UnauthorizedException;
import com.e2d.odin.domain.shared.UserId;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Una instalación de la fase 1.1 (hasta V6) con datos arranca con las migraciones nuevas y lo conserva. */
class MigrationFromPhaseOneIT {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID GROUP_ADMIN = UUID.randomUUID();
    private static final UUID COMPANY_ADMIN = UUID.randomUUID();
    private static final UUID ACTIVE_WORKER = UUID.randomUUID();
    private static final UUID INACTIVE_WORKER = UUID.randomUUID();
    private static final UUID DELETED_WORKER = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID DELETED_PROJECT = UUID.randomUUID();
    private static final UUID DOCUMENT_IN_DELETED_PROJECT = UUID.randomUUID();
    private static final UUID WAITING_FOR_READER = UUID.randomUUID();
    private static final UUID PROCESSING_LOCALLY = UUID.randomUUID();
    private static final UUID MIXED_PDF = UUID.randomUUID();
    private static final UUID FLAT_TEXT = UUID.randomUUID();
    private static final UUID KEY = UUID.randomUUID();
    private static final UUID DELETED_WORKER_KEY = UUID.randomUUID();
    private static final String DELETED_AT = "2026-10-01T10:00:00Z";

    private static DataSource database;
    private static JdbcClient jdbc;

    @BeforeAll
    static void migrateFromPhaseOneToLatest() {
        database = Db.freshDatabase("migration_from_phase_one");
        Map<String, String> placeholders = Map.of("appPassword", Db.APP_PASSWORD);
        Flyway.configure().dataSource(database).placeholders(placeholders).target("6").load().migrate();
        jdbc = JdbcClient.create(database);
        insertPhaseOneData();
        Flyway.configure().dataSource(database).placeholders(placeholders).load().migrate();
    }

    private static void insertPhaseOneData() {
        jdbc.sql("INSERT INTO company (id, name) VALUES (:id, 'Empresa')").param("id", COMPANY).update();
        jdbc.sql("INSERT INTO company_capability (company_id, capability) VALUES (:id, 'SEARCH_KNOWLEDGE')")
                .param("id", COMPANY).update();
        insertCompany("Talleres Demo Norte");
        insertCompany("Tintes Dorados  Nuevos");
        insertCompany("Ñandú Óptica");
        insertUser(GROUP_ADMIN, "GROUP_ADMIN", null, true);
        insertUser(COMPANY_ADMIN, "COMPANY_ADMIN", COMPANY, true);
        insertUser(ACTIVE_WORKER, "WORKER", COMPANY, true);
        insertUser(INACTIVE_WORKER, "WORKER", COMPANY, false);
        insertUser(DELETED_WORKER, "WORKER", COMPANY, true);
        jdbc.sql("UPDATE app_user SET deleted_at = :at WHERE id = :id")
                .param("at", OffsetDateTime.parse(DELETED_AT)).param("id", DELETED_WORKER).update();
        jdbc.sql("INSERT INTO project (id, company_id, name) VALUES (:id, :company, 'Obra')")
                .param("id", PROJECT).param("company", COMPANY).update();
        jdbc.sql("INSERT INTO project (id, company_id, name, deleted_at) VALUES (:id, :company, 'Vieja', now())")
                .param("id", DELETED_PROJECT).param("company", COMPANY).update();
        jdbc.sql("""
                INSERT INTO document (id, company_id, project_id, title, filename, format, sha256, status, paginated,
                    uploaded_by, uploaded_at, attempt_count)
                VALUES (:id, :company, :project, 'Plano viejo', 'f.pdf', 'PDF', 'abc', 'READY', true, :user, now(), 0)""")
                .param("id", DOCUMENT_IN_DELETED_PROJECT).param("company", COMPANY).param("project", DELETED_PROJECT)
                .param("user", ACTIVE_WORKER).update();
        insertReaderLaneDocuments();
        insertKey(KEY, ACTIVE_WORKER, "abcd1234", "h:secreto");
        insertKey(DELETED_WORKER_KEY, DELETED_WORKER, "wxyz5678", "h:otro");
    }

    private static void insertReaderLaneDocuments() {
        insertDocument(WAITING_FOR_READER, "PENDING_READER", true, 3);
        insertDocument(PROCESSING_LOCALLY, "PROCESSING", true, 2);
        insertDocument(MIXED_PDF, "READY", true, 1);
        insertPage(MIXED_PDF, 1, " ");
        insertPage(MIXED_PDF, 2, "con texto");
        insertPage(MIXED_PDF, 3, "\n");
        insertDocument(FLAT_TEXT, "READY", false, 1);
        insertPage(FLAT_TEXT, 1, "");
    }

    private static void insertDocument(UUID id, String status, boolean paginated, int attempts) {
        jdbc.sql("""
                INSERT INTO document (id, company_id, title, filename, format, sha256, status, paginated,
                    uploaded_by, uploaded_at, attempt_count)
                VALUES (:id, :company, 'Escaneo', 'f.pdf', 'PDF', :id::text, :status, :paginated, :user, now(),
                    :attempts)""")
                .param("id", id).param("company", COMPANY).param("status", status).param("paginated", paginated)
                .param("user", ACTIVE_WORKER).param("attempts", attempts).update();
    }

    private static void insertPage(UUID document, int number, String text) {
        jdbc.sql("""
                INSERT INTO document_page (document_id, company_id, number, text, reader_name, reader_version)
                VALUES (:id, :company, :number, :text, 'pdfbox', '3')""")
                .param("id", document).param("company", COMPANY).param("number", number).param("text", text).update();
    }

    private static List<Integer> pagesWithoutText(UUID document) {
        return List.of((Integer[]) jdbc.sql("SELECT pages_without_text FROM document WHERE id = :id")
                .param("id", document).query((rs, n) -> rs.getArray(1).getArray()).single());
    }

    private static void insertCompany(String name) {
        jdbc.sql("INSERT INTO company (id, name) VALUES (:id, :name)").param("id", UUID.randomUUID())
                .param("name", name).update();
    }

    private static String codeOfCompany(String name) {
        return jdbc.sql("SELECT code FROM company WHERE name = :name").param("name", name).query(String.class)
                .single();
    }

    private static void insertKey(UUID id, UUID user, String prefix, String secretHash) {
        jdbc.sql("""
                INSERT INTO access_key (id, user_id, label, prefix, secret_hash, created_at)
                VALUES (:id, :user, 'portatil', :prefix, :hash, now())""")
                .param("id", id).param("user", user).param("prefix", prefix).param("hash", secretHash).update();
    }

    private static void insertUser(UUID id, String role, UUID company, boolean active) {
        jdbc.sql("""
                INSERT INTO app_user (id, display_name, email, role, company_id, active)
                VALUES (:id, 'Persona', :email, :role, :company, :active)""")
                .param("id", id).param("email", id + "@x.test").param("role", role).param("company", company)
                .param("active", active).update();
    }

    private static Map<String, Object> membership(UUID user) {
        return jdbc.sql("""
                SELECT company_id, is_company_admin, is_worker, active, deleted_at
                FROM membership WHERE user_id = :user""").param("user", user).query().singleRow();
    }

    @Test
    void migrationGivesSimilarCasesOnlyToCompaniesThatCanSearch() {
        assertThat(jdbc.sql("SELECT count(*) FROM company").query(Long.class).single()).isEqualTo(4);
        assertThat(jdbc.sql("SELECT company_id FROM company_capability WHERE capability = 'SIMILAR_CASES'")
                .query(UUID.class).list()).containsExactly(COMPANY);
        assertThat(jdbc.sql("SELECT capability FROM company_capability WHERE company_id = :id ORDER BY capability")
                .param("id", COMPANY).query(String.class).list()).containsExactly("CASE_FILES", "SEARCH_KNOWLEDGE", "SIMILAR_CASES");
        assertThat(jdbc.sql("SELECT count(*) FROM company_capability").query(Long.class).single()).isEqualTo(3);
    }

    @Test
    void migrationLeavesExistingDocumentsAndQuestionsAsTheyWere() {
        assertThat(jdbc.sql("SELECT case_document FROM document WHERE id = :id").param("id", MIXED_PDF)
                .query(Boolean.class).single()).isFalse();
    }

    @Test
    void everyUserWithCompanyGetsOneMembershipWithItsRole() {
        assertThat(membership(COMPANY_ADMIN)).containsEntry("company_id", COMPANY)
                .containsEntry("is_company_admin", true).containsEntry("is_worker", false)
                .containsEntry("active", true).containsEntry("deleted_at", null);
        assertThat(membership(ACTIVE_WORKER)).containsEntry("company_id", COMPANY)
                .containsEntry("is_company_admin", false).containsEntry("is_worker", true)
                .containsEntry("active", true);
    }

    @Test
    void existingPeopleKeepTheirNameAsFirstNameAndGetACode() {
        List<Map<String, Object>> people = jdbc.sql("SELECT first_name, last_name, person_code FROM app_user")
                .query().listOfRows();

        assertThat(people).hasSize(5).allSatisfy(person -> {
            assertThat(person).containsEntry("first_name", "Persona").containsEntry("last_name", null);
            assertThat((String) person.get("person_code")).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}");
        });
        assertThat(people).extracting(person -> person.get("person_code")).doesNotHaveDuplicates();
    }

    @Test
    void existingCompaniesGetTheirInitialsAsCode() {
        assertThat(codeOfCompany("Talleres Demo Norte")).isEqualTo("TDN");
        assertThat(codeOfCompany("Tintes Dorados  Nuevos")).isEqualTo("TDN2");
        assertThat(codeOfCompany("Ñandú Óptica")).isEqualTo("NO");
        assertThat(codeOfCompany("Empresa")).isEqualTo("EMP");
    }

    @Test
    void existingCompaniesNeedTheirCodeReviewed() {
        assertThat(jdbc.sql("SELECT count(*) FROM company WHERE code_reviewed").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM company").query(Long.class).single()).isEqualTo(4);
    }

    @Test
    void groupAdminIsFlaggedAndHasNoMembership() {
        List<UUID> flagged = jdbc.sql("SELECT id FROM app_user WHERE group_admin").query(UUID.class).list();

        assertThat(flagged).containsExactly(GROUP_ADMIN);
        assertThat(jdbc.sql("SELECT count(*) FROM membership WHERE user_id = :user").param("user", GROUP_ADMIN)
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM membership").query(Long.class).single()).isEqualTo(4);
    }

    @Test
    void accessKeyKeepsItsUserAndGainsItsCompany() {
        Map<String, Object> key = jdbc.sql("SELECT user_id, company_id FROM access_key WHERE id = :id")
                .param("id", KEY).query().singleRow();

        assertThat(key).containsEntry("user_id", ACTIVE_WORKER).containsEntry("company_id", COMPANY);
    }

    @Test
    void inactiveWorkerBecomesInactiveMembership() {
        assertThat(membership(INACTIVE_WORKER)).containsEntry("active", false).containsEntry("is_worker", true);
        assertThat(jdbc.sql("SELECT active FROM app_user WHERE id = :id").param("id", INACTIVE_WORKER)
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void softDeletedUserKeepsItsDeletionOnTheMembershipAndItsKeyGainsTheCompany() {
        assertThat(membership(DELETED_WORKER).get("deleted_at")).isEqualTo(
                Timestamp.from(OffsetDateTime.parse(DELETED_AT).toInstant()));
        Map<String, Object> key = jdbc.sql("SELECT user_id, company_id FROM access_key WHERE id = :id")
                .param("id", DELETED_WORKER_KEY).query().singleRow();
        assertThat(key).containsEntry("user_id", DELETED_WORKER).containsEntry("company_id", COMPANY);
    }

    @Test
    void reactivatingAMigratedInactiveWorkerLetsTheirKeyWorkAgain() {
        ScopedTransactions tx = new ScopedTransactions(database);
        PostgresUserRepository users = new PostgresUserRepository(tx);
        PostgresMembershipRepository memberships = new PostgresMembershipRepository(tx);
        PostgresAccessKeyRepository keys = new PostgresAccessKeyRepository(tx);
        FakeSecretHasher secrets = new FakeSecretHasher();
        StoredAdminSessions sessions = new StoredAdminSessions(new InMemoryAdminSessionRepository(), secrets,
                new SequenceTokenGenerator(), new SessionSettings(), new MutableClock());
        ResolveActor resolve = new ResolveActor(keys, users, memberships, sessions, secrets);
        UserId worker = new UserId(INACTIVE_WORKER);
        keys.save(new AccessKey(new AccessKeyId(UUID.randomUUID()), worker, new CompanyId(COMPANY), "otra", "inact123",
                secrets.hash("clave"), Instant.now(), Optional.empty()));
        assertThatThrownBy(() -> resolve.fromAccessKey("odin_inact123_clave"))
                .isInstanceOf(UnauthorizedException.class);

        try {
            MemberView updated = new UpdateMember(users, memberships, new PostgresOrganizationRepository(tx),
                    new FakePasswordHasher(), sessions).execute(
                    Actors.companyAdmin(new CompanyId(COMPANY)), new CompanyId(COMPANY), worker,
                    new MemberChanges(Optional.empty(), Optional.of(true), Optional.empty()));

            assertThat(updated.active()).isTrue();
            assertThat(resolve.fromAccessKey("odin_inact123_clave").userId()).isEqualTo(worker);
        } finally {
            memberships.save(memberships.find(worker, new CompanyId(COMPANY)).orElseThrow().withActive(false));
            jdbc.sql("DELETE FROM access_key WHERE prefix = 'inact123'").update();
        }
    }

    @Test
    void everyWorkerMembershipOfTheCompanyBecomesMemberOfEveryProjectEvenDeletedOnes() {
        assertThat(jdbc.sql("SELECT restricted FROM project").query(Boolean.class).list()).containsOnly(true);
        assertThat(membersOf(PROJECT)).containsExactlyInAnyOrder(ACTIVE_WORKER, INACTIVE_WORKER);
        assertThat(membersOf(DELETED_PROJECT)).containsExactlyInAnyOrder(ACTIVE_WORKER, INACTIVE_WORKER);
    }

    private static List<UUID> membersOf(UUID project) {
        return jdbc.sql("SELECT user_id FROM project_member WHERE project_id = :id AND removed_at IS NULL")
                .param("id", project).query(UUID.class).list();
    }

    @Test
    void migratedWorkerStillFindsTheDocumentOfAProjectDeletedBeforeMigratingButNotItsFilter() {
        ScopedTransactions tx = new ScopedTransactions(database);
        ListDocuments list = new ListDocuments(new PostgresOrganizationRepository(tx),
                new PostgresProjectMemberRepository(tx), new PostgresDocumentRepository(tx), new PostgresTopicRepository(tx),
                new PostgresTaggingRepository(tx));
        Actor worker = new Actor(new UserId(ACTIVE_WORKER), "Persona", Role.WORKER, Set.of(new CompanyId(COMPANY)));

        assertThat(list.execute(worker, Optional.empty(), Optional.empty(), 0, Optional.empty()))
                .extracting(e -> e.entry().document().id().value())
                .contains(DOCUMENT_IN_DELETED_PROJECT);
        assertThatThrownBy(() -> list.execute(worker, Optional.of(new ProjectId(DELETED_PROJECT)),
                Optional.empty(), 0, Optional.empty())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void existingDocumentsAreNotSuperseded() {
        assertThat(jdbc.sql("SELECT superseded_by IS NULL AND superseded_at IS NULL FROM document WHERE id = :id")
                .param("id", DOCUMENT_IN_DELETED_PROJECT).query(Boolean.class).single()).isTrue();
    }

    @Test
    void reactivatingAMigratedInactiveWorkerShowsTheirProjects() {
        ScopedTransactions tx = new ScopedTransactions(database);
        assertThat(new PostgresProjectMemberRepository(tx).projectsOf(new CompanyId(COMPANY),
                new UserId(INACTIVE_WORKER))).containsExactly(new ProjectId(PROJECT));
    }

    @Test
    void migrationResetsAttemptsOfDocumentsWaitingForReader() {
        assertThat(jdbc.sql("SELECT attempt_count FROM document WHERE id = :id").param("id", WAITING_FOR_READER)
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT attempt_count FROM document WHERE id = :id").param("id", PROCESSING_LOCALLY)
                .query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void migrationFillsPagesWithoutTextOfExistingDocuments() {
        assertThat(pagesWithoutText(MIXED_PDF)).containsExactly(1, 3);
        assertThat(pagesWithoutText(FLAT_TEXT)).isEmpty();
        assertThat(pagesWithoutText(DOCUMENT_IN_DELETED_PROJECT)).isEmpty();
    }
}
