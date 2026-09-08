package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.event.UserAccountEventWriter;
import com.moneykk.moneytown.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KycService kycService;

    @Mock
    private UserAccountEventWriter userAccountEventWriter;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("본인 탈퇴 시 UserWithdrawn 이벤트를 기록한다")
    void deleteUserRecordsWithdrawnEvent() {
        UUID userId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        User user = createUser();

        given(userRepository.findByUserIdAndIsDeletedFalse(userId))
                .willReturn(Optional.of(user));

        userService.deleteUser(userId, correlationId);

        then(userAccountEventWriter)
                .should()
                .recordWithdrawn(userId, userId, correlationId);
    }

    @Test
    @DisplayName("관리자 탈퇴 처리 시 대상 사용자와 처리자를 구분하여 기록한다")
    void adminDeleteUserRecordsWithdrawnEvent() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        User user = createUser();

        given(userRepository.findByUserIdAndIsDeletedFalse(userId))
                .willReturn(Optional.of(user));

        userService.deleteUserByAdmin(adminId, userId, correlationId);

        then(userAccountEventWriter)
                .should()
                .recordWithdrawn(userId, adminId, correlationId);
    }

    private User createUser() {
        return User.create(
                "user@example.com",
                "encoded-password",
                "사용자",
                "01012345678"
        );
    }
}
