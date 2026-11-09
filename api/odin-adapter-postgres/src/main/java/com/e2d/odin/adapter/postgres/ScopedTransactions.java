package com.e2d.odin.adapter.postgres;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class ScopedTransactions {

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    public ScopedTransactions(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public <T> T inScope(CompanyScope scope, Function<JdbcClient, T> work) {
        try {
            return inTransaction(scope, work);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("Ya existe un registro con esos datos");
        }
    }

    private <T> T inTransaction(CompanyScope scope, Function<JdbcClient, T> work) {
        return transaction.execute(status -> {
            String ids = scope.companies().stream().map(CompanyId::value).map(Object::toString)
                    .collect(Collectors.joining(","));
            jdbc.sql("SELECT set_config('app.company_ids', :ids, true), set_config('app.group_scope', :group, true)")
                    .param("ids", ids)
                    .param("group", scope.allCompanies() ? "on" : "off")
                    .query().singleRow();
            return work.apply(jdbc);
        });
    }

    public void runInScope(CompanyScope scope, Consumer<JdbcClient> work) {
        inScope(scope, jdbc -> {
            work.accept(jdbc);
            return null;
        });
    }
}
