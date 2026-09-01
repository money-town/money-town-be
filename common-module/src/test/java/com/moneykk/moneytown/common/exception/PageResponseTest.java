package com.moneykk.moneytown.common.exception;
import com.moneykk.moneytown.common.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


class PageResponseTest {
    @Test
    @DisplayName("Page 객체를 PageResponse로 변환한다")
    void convertPageToPageResponse() {
        // given
        Page<String> page = new PageImpl<>(
                List.of("A", "B"),
                PageRequest.of(1, 2),
                5
        );

        // when
        PageResponse<String> response = PageResponse.from(page);

        // then
        assertThat(response.content()).containsExactly("A", "B");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isFalse();
        assertThat(response.hasNext()).isTrue();
    }
}
