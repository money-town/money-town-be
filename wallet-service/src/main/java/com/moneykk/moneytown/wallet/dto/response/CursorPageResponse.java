package com.moneykk.moneytown.wallet.dto.response;

import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.function.Function;

public record CursorPageResponse<T>(List<T> content, String nextCursor, boolean hasNext) {

    public static <E, T> CursorPageResponse<T> from(Slice<E> slice, Function<E, T> converter,
                                                      Function<E, String> cursorExtractor) {
        List<E> source = slice.getContent();
        List<T> content = source.stream().map(converter).toList();
        String nextCursor = slice.hasNext() && !source.isEmpty()
                ? cursorExtractor.apply(source.get(source.size() - 1))
                : null;

        return new CursorPageResponse<>(content, nextCursor, slice.hasNext());
    }
}
