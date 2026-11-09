package com.e2d.odin.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanyScopeTest {

    @Test
    void includes() {
        CompanyId mine = new CompanyId(UUID.randomUUID());
        CompanyId other = new CompanyId(UUID.randomUUID());

        assertThat(CompanyScope.of(mine).includes(mine)).isTrue();
        assertThat(CompanyScope.of(mine).includes(other)).isFalse();
        assertThat(CompanyScope.all().includes(other)).isTrue();
    }
}
