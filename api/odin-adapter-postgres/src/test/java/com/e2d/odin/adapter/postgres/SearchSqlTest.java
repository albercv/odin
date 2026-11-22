package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchSqlTest {

    @Test
    void plainNumberGivesTheSignedLexemeAndItsCompoundForms() {
        assertThat(SearchSql.numberVariants(List.of("su referencia 530"))).containsExactly("-530");
        assertThat(SearchSql.exactTokens(List.of("su referencia 530"))).containsExactly("-530", "/530", "530/");
    }

    @Test
    void slashedCaseNumberGivesItsGroups() {
        assertThat(SearchSql.numberVariants(List.of("expte. 2024/530"))).containsExactly("530", "-530");
        assertThat(SearchSql.exactTokens(List.of("expte. 2024/530"))).contains("2024/530", "-530", "/530", "530/");
    }

    @Test
    void hyphenatedCaseNumberGivesItsGroupAndTheLiteralForms() {
        assertThat(SearchSql.numberVariants(List.of("2024-530"))).containsExactly("530", "-530");
        assertThat(SearchSql.exactTokens(List.of("2024-530"))).contains("2024-530", "-530", "/530", "530/");
    }

    @Test
    void fourDigitNumbersOutsideTheYearRangeAreCaseNumbers() {
        assertThat(SearchSql.numberVariants(List.of("1530"))).containsExactly("-1530");
        assertThat(SearchSql.numberVariants(List.of("2100"))).containsExactly("-2100");
    }

    @Test
    void shortYearGroupIsDropped() {
        assertThat(SearchSql.numberVariants(List.of("exp. 530/24"))).containsExactly("530", "-530");
    }

    @Test
    void yearsAmountsDatesAndShortNumbersGetNothing() {
        for (String term : List.of("2025", "12/11/2024", "1.940", "24", "1,940 euros", "1.940,50")) {
            assertThat(SearchSql.numberVariants(List.of(term))).as(term).isEmpty();
            assertThat(SearchSql.exactTokens(List.of(term))).as(term)
                    .noneMatch(t -> t.startsWith("-") || t.startsWith("/") || t.endsWith("/"));
        }
    }

    @Test
    void compoundFormsRespectTheIdentifierLimit() {
        List<String> terms = List.of("111 222 333 444 555 666 777");
        assertThat(SearchSql.exactTokens(terms)).hasSize(10);
    }

    @Test
    void rankNormalisesByLengthAndRewardsTheShareOfQueryLexemes() {
        String rank = SearchSql.rank("p.tsv");

        assertThat(rank).startsWith("ts_rank_cd(p.tsv, q.q, 2)").contains("power(").contains("p.tsv @@ ql.lq")
                .contains("NULLIF((SELECT count(*) FROM ql), 0), 1)");
    }

    @Test
    void lexemesCteTakesEveryTermOfTheQuery() {
        assertThat(SearchSql.lexemes(3)).startsWith("ql AS (")
                .contains("to_tsvector('odin_spanish', :t0 || ' ' || :t1 || ' ' || :t2)");
    }
}
