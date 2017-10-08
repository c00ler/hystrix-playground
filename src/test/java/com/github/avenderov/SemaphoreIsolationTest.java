package com.github.avenderov;

import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SemaphoreIsolationTest {

    @Test
    void shouldUseFallbackWhenSemaphoreIsUsed() throws InterruptedException {
        final int iterations = 10;

        final Collection<String> results = Collections.synchronizedList(new ArrayList<>(iterations));
        final Collection<Thread> threads = new ArrayList<>(iterations);

        for (final int i : IntStream.range(0, iterations).toArray()) {
            final Thread thread = new Thread(() -> {
                final DelayedReturnCommand command = new DelayedReturnCommand("test-" + i, 1L, false);
                results.add(command.execute());
            });
            thread.setName("Thread-" + i);
            thread.start();
            threads.add(thread);
        }

        for (final Thread thread : threads) {
            thread.join();
        }

        assertThat(results).filteredOn(r -> Objects.equals(r, "fallback")).hasSize(iterations - 2);
    }

    private static class DelayedReturnCommand extends HystrixCommand<String> {

        private static final Logger LOGGER = LoggerFactory.getLogger(DelayedReturnCommand.class);

        private static final Setter CACHED_SETTER = Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey(SemaphoreIsolationTest.class.getSimpleName()))
                .andCommandKey(HystrixCommandKey.Factory.asKey("GetValue"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE)
                        .withExecutionIsolationSemaphoreMaxConcurrentRequests(2)
                        .withExecutionTimeoutEnabled(false));

        private final String value;

        private final Long delay;

        private final boolean shouldThrow;

        DelayedReturnCommand(final String value) {
            this(value, null, false);
        }

        DelayedReturnCommand(final String value, @Nullable final Long delay, final boolean shouldThrow) {
            super(CACHED_SETTER);

            this.value = value;
            this.delay = delay;
            this.shouldThrow = shouldThrow;
        }

        @Override
        protected String run() throws Exception {
            LOGGER.info("Running main logic...");

            if (delay != null) {
                LOGGER.info("Sleeping for {} seconds...", delay);
                Uninterruptibles.sleepUninterruptibly(delay, TimeUnit.SECONDS);
            }

            if (shouldThrow) {
                LOGGER.info("Throwing exception...", delay);
                throw new RuntimeException("Boom!");
            }

            return value;
        }

        @Override
        protected String getFallback() {
            LOGGER.info("Running fallback logic...");
            return "fallback";
        }

    }

}
