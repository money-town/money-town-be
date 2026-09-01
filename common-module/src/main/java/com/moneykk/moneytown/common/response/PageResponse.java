package com.moneykk.moneytown.common.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(List<T> content,
                              int page,
                              int size,
                              long totalElements,
                              int totalPages,
                              boolean first,
                              boolean last,
                              boolean hasNext) {


    public static <T> PageResponse<T> from(Page<T> pageResult) {
        return new PageResponse<>(pageResult.getContent(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.isFirst(),
                pageResult.isLast(),
                pageResult.hasNext());

    }
}
