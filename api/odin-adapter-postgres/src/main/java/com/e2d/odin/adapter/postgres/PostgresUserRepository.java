package com.e2d.odin.adapter.postgres;

import com.e2d.odin.application.port.UserRepository;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class PostgresUserRepository implements UserRepository {

    private static final String COLUMNS = "id, display_name, email, group_admin, active, password_hash";
    private static final CompanyScope ALL = CompanyScope.all();
    private final ScopedTransactions tx;

    public PostgresUserRepository(ScopedTransactions tx) {
        this.tx = tx;
    }

    @Override
    public void save(User u) {
        tx.runInScope(ALL, jdbc -> jdbc.sql("""
                INSERT INTO app_user (id, display_name, email, group_admin, active, password_hash)
                VALUES (:id, :name, :email, :groupAdmin, :active, :hash)
                ON CONFLICT (id) DO UPDATE SET display_name = :name, email = :email, group_admin = :groupAdmin,
                    active = :active, password_hash = :hash""")
                .param("id", u.id().value()).param("name", u.displayName()).param("email", u.email().orElse(null))
                .param("groupAdmin", u.groupAdmin()).param("active", u.active()).param("hash", u.passwordHash().orElse(null)).update());
    }

    @Override
    public Optional<User> findById(UserId id) {
        return tx.inScope(ALL, jdbc -> jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE id = :id AND deleted_at IS NULL")
                .param("id", id.value()).query((rs, n) -> user(rs)).optional());
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return tx.inScope(ALL, jdbc -> jdbc
                .sql("SELECT " + COLUMNS + " FROM app_user WHERE lower(email) = lower(:email) AND deleted_at IS NULL")
                .param("email", email).query((rs, n) -> user(rs)).optional());
    }

    @Override
    public Optional<User> findGroupAdmin() {
        return tx.inScope(ALL, jdbc -> jdbc
                .sql("SELECT " + COLUMNS + " FROM app_user WHERE group_admin AND deleted_at IS NULL LIMIT 1")
                .query((rs, n) -> user(rs)).optional());
    }

    @Override
    public void delete(UserId id, Instant at) {
        tx.runInScope(ALL, jdbc -> jdbc.sql("UPDATE app_user SET deleted_at = :at WHERE id = :id AND deleted_at IS NULL")
                .param("id", id.value()).param("at", Sql.ts(at)).update());
    }

    private static User user(ResultSet rs) throws SQLException {
        return new User(new UserId(Sql.uuid(rs, "id")), rs.getString("display_name"),
                Optional.ofNullable(rs.getString("email")), rs.getBoolean("group_admin"), rs.getBoolean("active"),
                Optional.ofNullable(rs.getString("password_hash")));
    }
}
