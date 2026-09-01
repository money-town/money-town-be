package com.moneykk.moneytown.common.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean hasNext
) {

    public static <E, T> PageResponse<T> from(
            Page<E> page,
            Function<E, T> converter
    ) {
        List<T> content = page.getContent().stream()
                .map(converter)
                .toList();

        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext()
        );
    }
}