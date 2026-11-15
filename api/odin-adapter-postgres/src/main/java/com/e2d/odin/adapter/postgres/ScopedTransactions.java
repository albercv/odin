package com.e2d.odin.adapter.postgres;

import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.ConflictException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class ScopedTransactions {

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    private final boolean includeWithdrawn;
    private final boolean includeIntake;

    public ScopedTransactions(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.includeWithdrawn = false;
        this.includeIntake = false;
    }

    private ScopedTransactions(JdbcClient jdbc, TransactionTemplate transaction, boolean includeWithdrawn,
            boolean includeIntake) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.includeWithdrawn = includeWithdrawn;
        this.includeIntake = includeIntake;
    }

    /** Las mismas transacciones, pero con lo retirado a la vista (pantalla de documentos del administrador). */
    public ScopedTransactions includeWithdrawn(boolean include) {
        return new ScopedTransactions(jdbc, transaction, include, includeIntake);
    }

    /** Las mismas transacciones, pero con lo pendiente de la primera carga a la vista (solo la bandeja y su lectura). */
    public ScopedTransactions includeIntake(boolean include) {
        return new ScopedTransactions(jdbc, transaction, includeWithdrawn, include);
    }

    /** Sin visibilidad de proyectos solo se ve lo que no tiene proyecto. */
    public <T> T inScope(CompanyScope scope, Function<JdbcClient, T> work) {
        return inScope(scope, null, work);
    }

    public <T> T inScope(CompanyScope scope, ProjectVisibility visibility, Function<JdbcClient, T> work) {
        try {
            return inTransaction(scope, visibility, work);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("Ya existe un registro con esos datos");
        }
    }

    private <T> T inTransaction(CompanyScope scope, ProjectVisibility visibility, Function<JdbcClient, T> work) {
        return transaction.execute(status -> {
            jdbc.sql("SELECT set_config('app.company_ids', :ids, true), set_config('app.group_scope', :group, true)")
                    .param("ids", Sql.companyIds(scope))
                    .param("group", scope.allCompanies() ? "on" : "off")
                    .query().singleRow();
            jdbc.sql("SELECT set_config('app.include_withdrawn', :on, true)")
                    .param("on", includeWithdrawn ? "on" : "off").query().singleRow();
            jdbc.sql("SELECT set_config('app.include_intake', :on, true)")
                    .param("on", includeIntake ? "on" : "off").query().singleRow();
            if (visibility != null) {
                setProjectVariables(visibility);
            }
            return work.apply(jdbc);
        });
    }

    private void setProjectVariables(ProjectVisibility visibility) {
        jdbc.sql("SELECT set_config('app.project_ids', :ids, true), set_config('app.all_projects', :all, true)")
                .param("ids", Sql.projectIds(visibility))
                .param("all", visibility.allProjects() ? "on" : "off")
                .query().singleRow();
    }

    public void runInScope(CompanyScope scope, Consumer<JdbcClient> work) {
        runInScope(scope, null, work);
    }

    public void runInScope(CompanyScope scope, ProjectVisibility visibility, Consumer<JdbcClient> work) {
        inScope(scope, visibility, jdbc -> {
            work.accept(jdbc);
            return null;
        });
    }
}
