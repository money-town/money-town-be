package com.moneykk.moneytown.analysis.fds.domain;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import com.moneykk.moneytown.common.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Table(name = "p_fds_user_states",
        uniqueConstraints = @UniqueConstraint(columnNames = "user_id")
)
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class FdsUserState extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "fds_user_state_id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private UserStatus status;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "block_reason", length = 500)
    private String blockedReason;

    private FdsUserState(UUID user_id){
        this.userId = user_id;
        this.status = UserStatus.NORMAL;
    }

    public static FdsUserState create(UUID user_id){
        return new FdsUserState(user_id);
    }

    public void block(String blockedReason){
        if(this.status == UserStatus.BLOCKED){
            throw new BusinessException(AnalysisErrorCode.FDS_ALREADY_BLOCKED);
        }
        this.blockedAt = Instant.now();
        this.status = UserStatus.BLOCKED;
        this.blockedReason = blockedReason;
    }

    public void unblock(){
        if(this.status != UserStatus.BLOCKED){
            throw new BusinessException(AnalysisErrorCode.FDS_ALREADY_NORMAL);
        }
        this.status = UserStatus.NORMAL;
        this.blockedAt = null;
        this.blockedReason = null;
    }

    public void markSuspicious(){
        this.status = UserStatus.SUSPICIOUS;
    }
}
