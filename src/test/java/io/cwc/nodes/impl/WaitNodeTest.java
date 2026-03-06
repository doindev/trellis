package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class WaitNodeTest {

    private WaitNode node;

    @BeforeEach
    void setUp() {
        node = new WaitNode();
    }

    // ── Time Interval mode ──

    @Test
    void timeIntervalReturnsWaitingResult() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "timeInterval",
                "amount", 5,
                "unit", "seconds"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getWaitConfig()).isNotNull();
        assertThat(result.getWaitConfig().getWaitType()).isEqualTo("timeInterval");
    }

    @Test
    void timeIntervalResumeAtIsInTheFuture() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "timeInterval",
                "amount", 10,
                "unit", "minutes"
        );

        Instant before = Instant.now();
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getWaitConfig().getResumeAt()).isAfter(before);
        // Should be approximately 10 minutes in the future
        assertThat(result.getWaitConfig().getResumeAt()).isAfter(before.plus(9, ChronoUnit.MINUTES));
    }

    @Test
    void timeIntervalDefaultsToOneSecond() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap("resume", "timeInterval");

        Instant before = Instant.now();
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getWaitConfig()).isNotNull();
        assertThat(result.getWaitConfig().getWaitType()).isEqualTo("timeInterval");
        assertThat(result.getWaitConfig().getResumeAt()).isAfter(before);
    }

    @Test
    void timeIntervalHoursUnit() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "timeInterval",
                "amount", 2,
                "unit", "hours"
        );

        Instant before = Instant.now();
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getWaitConfig().getResumeAt()).isAfter(before.plus(1, ChronoUnit.HOURS));
    }

    @Test
    void timeIntervalDaysUnit() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "timeInterval",
                "amount", 1,
                "unit", "days"
        );

        Instant before = Instant.now();
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getWaitConfig().getResumeAt()).isAfter(before.plus(23, ChronoUnit.HOURS));
    }

    @Test
    void timeIntervalContinueExecutionIsFalse() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "timeInterval",
                "amount", 1,
                "unit", "seconds"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.isContinueExecution()).isFalse();
    }

    // ── Specific Time mode ──

    @Test
    void specificTimeFutureReturnsWaiting() {
        Instant futureTime = Instant.now().plus(1, ChronoUnit.HOURS);
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "specificTime",
                "dateTime", futureTime.toString()
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getWaitConfig()).isNotNull();
        assertThat(result.getWaitConfig().getWaitType()).isEqualTo("specificTime");
        assertThat(result.getWaitConfig().getResumeAt()).isEqualTo(futureTime);
    }

    @Test
    void specificTimePastReturnsSuccessWithInputData() {
        Instant pastTime = Instant.now().minus(1, ChronoUnit.HOURS);
        List<Map<String, Object>> input = items(Map.of("name", "alice"));
        Map<String, Object> params = mutableMap(
                "resume", "specificTime",
                "dateTime", pastTime.toString()
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        // Past time: should pass through input data immediately
        assertThat(result.getWaitConfig()).isNull();
        assertThat(result.isContinueExecution()).isTrue();
        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "alice");
    }

    @Test
    void specificTimeEmptyDateTimeReturnsError() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "specificTime",
                "dateTime", ""
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getMessage()).containsIgnoringCase("no date/time");
    }

    @Test
    void specificTimeMissingDateTimeReturnsError() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "specificTime"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNotNull();
    }

    @Test
    void specificTimeInvalidFormatReturnsError() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "specificTime",
                "dateTime", "not-a-date"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getMessage()).containsIgnoringCase("invalid date/time format");
    }

    @Test
    void specificTimeContinueExecutionIsFalse() {
        Instant futureTime = Instant.now().plus(1, ChronoUnit.DAYS);
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "specificTime",
                "dateTime", futureTime.toString()
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.isContinueExecution()).isFalse();
    }

    // ── Webhook mode ──

    @Test
    void webhookReturnsWaiting() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "webhook",
                "httpMethod", "GET",
                "limitWaitTime", false
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getWaitConfig()).isNotNull();
        assertThat(result.getWaitConfig().getWaitType()).isEqualTo("webhook");
    }

    @Test
    void webhookWithLimitWaitTimeSetsResumeAt() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "webhook",
                "httpMethod", "POST",
                "limitWaitTime", true,
                "maxWaitAmount", 30,
                "maxWaitUnit", "minutes"
        );

        Instant before = Instant.now();
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getWaitConfig()).isNotNull();
        assertThat(result.getWaitConfig().getWaitType()).isEqualTo("webhook");
        assertThat(result.getWaitConfig().getResumeAt()).isNotNull();
        assertThat(result.getWaitConfig().getResumeAt()).isAfter(before);
    }

    @Test
    void webhookWithoutLimitWaitTimeHasNullResumeAt() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "webhook",
                "httpMethod", "GET",
                "limitWaitTime", false
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getWaitConfig().getResumeAt()).isNull();
    }

    @Test
    void webhookContinueExecutionIsFalse() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "webhook",
                "limitWaitTime", false
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.isContinueExecution()).isFalse();
    }

    // ── Unknown mode ──

    @Test
    void unknownResumeModeReturnsError() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "resume", "unknownMode"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getMessage()).containsIgnoringCase("unknown");
    }

    // ── Node metadata ──

    @Test
    void hasOneInput() {
        assertThat(node.getInputs()).hasSize(1);
        assertThat(node.getInputs().get(0).getName()).isEqualTo("main");
    }

    @Test
    void hasOneOutput() {
        assertThat(node.getOutputs()).hasSize(1);
        assertThat(node.getOutputs().get(0).getName()).isEqualTo("main");
    }

    @Test
    void hasParameters() {
        assertThat(node.getParameters()).isNotEmpty();
        assertThat(node.getParameters().get(0).getName()).isEqualTo("resume");
    }
}
