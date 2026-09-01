package com.moneykk.moneytown.common.exception;
import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseUpdatableEntityTest {
    @Test
    @DisplayName("엔티티를 논리 삭제한다")
    void softDelete() {
        // given
        TestEntity entity = new TestEntity();
        UUID deletedBy = UUID.randomUUID();

        // when
        entity.softDelete(deletedBy);

        // then
        assertThat(entity.isDeleted()).isTrue();
        assertThat(entity.getDeletedAt()).isNotNull();
        assertThat(entity.getDeletedBy()).isEqualTo(deletedBy);
    }

    @Test
    @DisplayName("논리 삭제된 엔티티를 복구한다")
    void restore() {
        // given
        TestEntity entity = new TestEntity();
        entity.softDelete(UUID.randomUUID());

        // when
        entity.restore();

        // then
        assertThat(entity.isDeleted()).isFalse();
        assertThat(entity.getDeletedAt()).isNull();
        assertThat(entity.getDeletedBy()).isNull();
    }

    private static class TestEntity extends BaseUpdatableEntity {
    }
}
