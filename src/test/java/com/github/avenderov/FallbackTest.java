package com.github.avenderov;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackTest {

    @Test
    void shouldUseFallback() {
        final AlwaysFailingCommand command = new AlwaysFailingCommand();
        final String result = command.execute();

        assertThat(result).isEqualTo("fallback");
    }

    private static class AlwaysFailingCommand extends HystrixCommand<String> {

        private static final Logger LOGGER = LoggerFactory.getLogger(AlwaysFailingCommand.class);

        AlwaysFailingCommand() {
            super(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
        }

        @Override
        protected String run() throws Exception {
            LOGGER.info("Running main logic...");
            throw new RuntimeException("expected");
        }

        @Override
        protected String getFallback() {
            LOGGER.info("Running fallback logic...");
            return "fallback";
        }

    }

}
