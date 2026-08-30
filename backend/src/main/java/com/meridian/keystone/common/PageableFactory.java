package com.meridian.keystone.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Builds a {@link Pageable} from raw request parameters.
 *
 * <p>Sort fields are checked against a whitelist so a client cannot sort by an
 * arbitrary entity path. An unknown field would otherwise surface as a 500 from
 * deep inside Spring Data; here it is a clean 400 naming what is allowed.
 */
public final class PageableFactory {

    private PageableFactory() {
    }

    private static final int MAX_SIZE = 100;

    public static Pageable of(Integer page,
                              Integer size,
                              String sort,
                              Set<String> sortableFields,
                              Sort defaultSort) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, MAX_SIZE);

        if (sort == null || sort.isBlank()) {
            return PageRequest.of(pageNumber, pageSize, defaultSort);
        }

        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!sortableFields.contains(field)) {
            throw new IllegalArgumentException(
                    "Cannot sort by '" + field + "'. Sortable fields: "
                            + String.join(", ", sortableFields.stream().sorted().toList()));
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return PageRequest.of(pageNumber, pageSize, Sort.by(direction, field));
    }
}
