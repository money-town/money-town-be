package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionCompensationRequestedPayload;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletHold;
import com.moneykk.moneytown.wallet.entity.WalletHoldStatus;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.repository.WalletHoldRepository;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.repository.WalletTransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// pg_advisory_xact_lock(Postgres 전용) 검증이라 Mockito 대신 실제 Postgres(Testcontainers)를 쓴다.
// @DataJpaTest 기본 롤백 트랜잭션을 쓰면 스레드끼리 트랜잭션을 공유해 경합이 재현 안 되므로 NOT_SUPPORTED로 끈다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@Import({JpaAuditingConfig.class, WalletHoldService.class, WalletHoldServiceConcurrencyTest.TestMeterRegistryConfig.class})
class WalletHoldServiceConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private WalletHoldRepository walletHoldRepository;
    @Autowired
    private WalletTransactionRepository walletTransactionRepository;
    @Autowired
    private WalletHoldService walletHoldService;

    @Test
    void compensateHold_concurrentRefundRequests_appliesRefundOnlyOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        long holdAmount = 10_000L;

        Wallet wallet = walletRepository.save(new Wallet(userId));
        wallet.deposit(holdAmount);
        wallet.hold(holdAmount);
        walletRepository.saveAndFlush(wallet);

        WalletHold hold = walletHoldRepository.save(new WalletHold(wallet.getId(), subscriptionId, holdAmount));

        // confirmHold()와 동일하게 COMMITTED 전에 잔액을 먼저 차감해야 REFUND 후 잔액 검증이 정확하다.
        wallet.deductHold(holdAmount);
        hold.commit();
        walletRepository.saveAndFlush(wallet);
        walletHoldRepository.saveAndFlush(hold);

        EventEnvelope<SubscriptionCompensationRequestedPayload> event = EventEnvelope.of(
                "SubscriptionCompensationRequested", subscriptionId.toString(), userId, "corr-1",
                new SubscriptionCompensationRequestedPayload("ADMIN_FORCE_CANCEL"));

        // 같은 보상 요청이 두 번 동시에 들어온 상황(예: 컨슈머 재시도로 인한 중복 수신)을 재현
        runConcurrently(2, () -> walletHoldService.compensateHold(event));

        WalletHold resultHold = walletHoldRepository.findBySubscriptionId(subscriptionId).orElseThrow();
        assertThat(resultHold.getStatus()).isEqualTo(WalletHoldStatus.REFUNDED);

        Wallet resultWallet = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(resultWallet.getBalance()).isEqualTo(holdAmount); // REFUND가 두 번 적용됐다면 2배가 됐을 것

        List<WalletTransaction> refundTransactions =
                walletTransactionRepository.findAll().stream()
                        .filter(t -> t.getType() == WalletTransactionType.REFUND)
                        .toList();
        assertThat(refundTransactions).hasSize(1);
    }

    @Test
    void confirmHold_concurrentDeductRequests_appliesDeductOnlyOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        long holdAmount = 10_000L;

        Wallet wallet = walletRepository.save(new Wallet(userId));
        wallet.deposit(holdAmount);
        wallet.hold(holdAmount);
        walletRepository.saveAndFlush(wallet);

        walletHoldRepository.saveAndFlush(new WalletHold(wallet.getId(), subscriptionId, holdAmount));

        EventEnvelope<Object> event = EventEnvelope.of(
                "SubscriptionConfirmed", subscriptionId.toString(), userId, "corr-1", null);

        // 같은 확정 이벤트가 두 번 동시에 들어온 상황(예: 컨슈머 재시도로 인한 중복 수신)을 재현
        runConcurrently(2, () -> walletHoldService.confirmHold(event));

        WalletHold resultHold = walletHoldRepository.findBySubscriptionId(subscriptionId).orElseThrow();
        assertThat(resultHold.getStatus()).isEqualTo(WalletHoldStatus.COMMITTED);

        Wallet resultWallet = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(resultWallet.getBalance()).isEqualTo(0L); // DEDUCT가 두 번 적용됐다면 마이너스가 됐을 것

        List<WalletTransaction> deductTransactions =
                walletTransactionRepository.findAll().stream()
                        .filter(t -> t.getType() == WalletTransactionType.DEDUCT)
                        .toList();
        assertThat(deductTransactions).hasSize(1);
    }

    // threadCount개 스레드가 action을 동시에 실행하게 하고, 완전히 끝날 때까지 기다린다.
    // Future.get()으로 안 받으면 스레드 안 예외가 조용히 삼켜지므로 여기서 명시적으로 드러낸다.
    private void runConcurrently(int threadCount, Runnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                await(go);
                action.run();
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        for (Future<?> future : futures) {
            future.get();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    static class TestMeterRegistryConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
