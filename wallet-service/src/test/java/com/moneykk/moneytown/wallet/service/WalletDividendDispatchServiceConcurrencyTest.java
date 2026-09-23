package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.consumer.dto.DividendPayoutDispatchPayload;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
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

// 락을 먼저 잡고 멱등키 체크를 나중에 해서 advisory lock 없이도 안전한지 실제 Postgres로 검증한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@Import({JpaAuditingConfig.class, WalletDividendDispatchService.class,
        WalletDividendDispatchServiceConcurrencyTest.TestMeterRegistryConfig.class})
class WalletDividendDispatchServiceConcurrencyTest {

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
    private WalletTransactionRepository walletTransactionRepository;
    @Autowired
    private WalletDividendDispatchService walletDividendDispatchService;

    @Test
    void processDividendDispatch_concurrentDuplicateRequests_appliesDepositOnlyOnce() throws Exception {
        UUID investorId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        UUID settlementBatchId = UUID.randomUUID();
        long amount = 10_000L;

        walletRepository.saveAndFlush(new Wallet(investorId));

        EventEnvelope<DividendPayoutDispatchPayload> event = EventEnvelope.of(
                "DividendPayoutDispatchRequested", payoutId.toString(), null, "corr-1",
                new DividendPayoutDispatchPayload(payoutId, settlementBatchId, investorId, amount));

        // 같은 배당 지급 요청 메시지가 두 번 동시에 들어온 상황(at-least-once 재수신 등)을 재현
        runConcurrently(2, () -> walletDividendDispatchService.processDividendDispatch(event));

        Wallet resultWallet = walletRepository.findByUserId(investorId).orElseThrow();
        assertThat(resultWallet.getBalance()).isEqualTo(amount); // 두 번 적용됐다면 2배가 됐을 것

        List<WalletTransaction> dividendTransactions =
                walletTransactionRepository.findAll().stream()
                        .filter(t -> t.getType() == WalletTransactionType.DIVIDEND)
                        .toList();
        assertThat(dividendTransactions).hasSize(1);
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
