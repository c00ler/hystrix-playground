package com.github.avenderov;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FullThreadPoolTest {

    private static final int DEFAULT_POOL_SIZE = 10;

    private final List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

    @BeforeAll
    static void beforeAll() {
        final AbstractConfiguration configuration = ConfigurationManager.getConfigInstance();
        configuration.setProperty("hystrix.command.default.execution.timeout.enabled", false);
        configuration.setProperty("hystrix.threadpool.default.coreSize", DEFAULT_POOL_SIZE);
    }

    @Test
    void shouldThrowIfPoolIsFull() {
        for (final Integer i : IntStream.range(0, DEFAULT_POOL_SIZE + 1).toArray()) {
            new Thread(() -> {
                final DelayedReturnCommand command = new DelayedReturnCommand("Thread-" + i, 5L);
                try {
                    command.execute();
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }).start();
        }

        await().atMost(5, TimeUnit.SECONDS).until(() -> exceptions.size() > 0);

        final Exception exception = Iterables.getOnlyElement(exceptions);

        assertThat(exception).isInstanceOf(HystrixRuntimeException.class);
        assertThat(((HystrixRuntimeException) exception).getFailureType())
                .isEqualTo(HystrixRuntimeException.FailureType.REJECTED_THREAD_EXECUTION);
    }

    private static class DelayedReturnCommand extends HystrixCommand<String> {

        private final String value;

        private final Long delay;

        DelayedReturnCommand(final String value, final Long delay) {
            super(HystrixCommandGroupKey.Factory.asKey(FullThreadPoolTest.class.getSimpleName()));
            this.value = value;
            this.delay = delay;
        }

        protected String run() throws Exception {
            Uninterruptibles.sleepUninterruptibly(delay, TimeUnit.SECONDS);
            return value;
        }

    }

}
