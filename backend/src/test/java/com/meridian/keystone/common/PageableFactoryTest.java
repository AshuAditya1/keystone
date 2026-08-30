package com.meridian.keystone.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Paging and sorting parameters, which arrive straight from the query string and
 * so are treated as hostile.
 *
 * <p>The interesting cases are all abuse: a negative page, a page size large
 * enough to be a denial-of-service, and a sort field that is really an attempt to
 * traverse the entity graph. The last one matters most — Spring Data will happily
 * accept {@code sort=assignee.passwordHash} and turn it into SQL, so the
 * whitelist is a security control, not a nicety.
 */
class PageableFactoryTest {

    private static final Set<String> SORTABLE = Set.of("name", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    @Test
    @DisplayName("missing parameters fall back to the first page and the default sort")
    void defaults() {
        Pageable pageable = PageableFactory.of(null, null, null, SORTABLE, DEFAULT_SORT);
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(DEFAULT_SORT);
    }

    @Test
    @DisplayName("a valid request is passed through unchanged")
    void validRequest() {
        Pageable pageable = PageableFactory.of(3, 50, "createdAt,desc", SORTABLE, DEFAULT_SORT);
        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    @DisplayName("sort direction defaults to ascending and is case-insensitive")
    void sortDirection() {
        assertThat(PageableFactory.of(0, 10, "name", SORTABLE, DEFAULT_SORT).getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "name"));
        assertThat(PageableFactory.of(0, 10, "name,DESC", SORTABLE, DEFAULT_SORT).getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "name"));
        assertThat(PageableFactory.of(0, 10, "name,sideways", SORTABLE, DEFAULT_SORT).getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Test
    @DisplayName("whitespace around the sort field is tolerated")
    void sortIsTrimmed() {
        assertThat(PageableFactory.of(0, 10, "  name , desc ", SORTABLE, DEFAULT_SORT).getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "name"));
    }

    @Test
    @DisplayName("a nonsense page or size is corrected, not obeyed")
    void nonsenseIsCorrected() {
        assertThat(PageableFactory.of(-5, null, null, SORTABLE, DEFAULT_SORT).getPageNumber())
                .isZero();
        assertThat(PageableFactory.of(0, 0, null, SORTABLE, DEFAULT_SORT).getPageSize())
                .isEqualTo(20);
        assertThat(PageableFactory.of(0, -1, null, SORTABLE, DEFAULT_SORT).getPageSize())
                .isEqualTo(20);
    }

    @Test
    @DisplayName("an enormous page size is capped so one request cannot pull the table")
    void pageSizeIsCapped() {
        assertThat(PageableFactory.of(0, 100_000, null, SORTABLE, DEFAULT_SORT).getPageSize())
                .isEqualTo(100);
    }

    @Test
    @DisplayName("an un-whitelisted sort field is rejected, and the error says what is allowed")
    void unknownSortFieldIsRejected() {
        assertThatThrownBy(() ->
                PageableFactory.of(0, 20, "unitCost", SORTABLE, DEFAULT_SORT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot sort by 'unitCost'")
                .hasMessageContaining("createdAt")
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("a sort field cannot walk the entity graph to reach another table")
    void sortCannotTraverseRelations() {
        assertThatThrownBy(() ->
                PageableFactory.of(0, 20, "assignee.passwordHash", SORTABLE, DEFAULT_SORT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a blank sort parameter is treated as absent")
    void blankSortIsIgnored() {
        assertThat(PageableFactory.of(0, 20, "   ", SORTABLE, DEFAULT_SORT).getSort())
                .isEqualTo(DEFAULT_SORT);
    }
}
