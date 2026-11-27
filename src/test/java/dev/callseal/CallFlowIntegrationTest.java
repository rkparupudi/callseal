package dev.callseal;

import dev.callseal.audit.AuditLog;
import dev.callseal.common.ApiException;
import dev.callseal.intent.IntentDtos.CreateIntentRequest;
import dev.callseal.intent.IntentDtos.CreateIntentResponse;
import dev.callseal.intent.IntentService;
import dev.callseal.intent.IntentState;
import dev.callseal.intent.Purpose;
import dev.callseal.verdict.VerdictService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Needs Docker running: Testcontainers starts Redis and PostgreSQL. */
@SpringBootTest
@Testcontainers
class CallFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    IntentService intents;
    @Autowired
    VerdictService verdicts;
    @Autowired
    AuditLog audit;

    private static String newCustomer() {
        return "cust-" + UUID.randomUUID();
    }

    private static CreateIntentRequest request(String customer) {
        return new CreateIntentRequest(customer, "+919800000001", Purpose.EMI_REMINDER, "agent-1");
    }

    @Test
    void sameIdempotencyKeyReturnsSameCall() {
        String customer = newCustomer();
        CreateIntentResponse first = intents.create("demo-bank", "key-" + customer, request(customer));
        CreateIntentResponse retry = intents.create("demo-bank", "key-" + customer, request(customer));

        assertThat(retry.intentId()).isEqualTo(first.intentId());
        assertThat(retry.spokenCode()).isEqualTo(first.spokenCode());
    }

    @Test
    void onlyOneOpenCallPerCustomer() {
        String customer = newCustomer();
        intents.create("demo-bank", "a-" + customer, request(customer));

        assertThatThrownBy(() -> intents.create("demo-bank", "b-" + customer, request(customer)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void concurrentVerdictsOnlyOneWins() throws Exception {
        String customer = newCustomer();
        String intentId = intents.create("demo-bank", "c-" + customer, request(customer)).intentId();

        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int n = 0; n < attempts; n++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    verdicts.record(intentId, customer, VerdictService.Verdict.GENUINE);
                    return true;
                } catch (ApiException e) {
                    return false;
                }
            }));
        }
        start.countDown(); // release all threads at once

        long wins = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) {
                wins++;
            }
        }
        pool.shutdown();

        assertThat(wins).isEqualTo(1);
    }

    @Test
    void auditChainStaysValidAcrossTheFlow() {
        String customer = newCustomer();
        String intentId = intents.create("demo-bank", "d-" + customer, request(customer)).intentId();

        assertThat(verdicts.record(intentId, customer, VerdictService.Verdict.REPORTED))
                .isEqualTo(IntentState.REPORTED);

        AuditLog.VerifyResult result = audit.verify("demo-bank");
        assertThat(result.valid()).isTrue();
        assertThat(result.events()).isGreaterThanOrEqualTo(2);
    }
}
