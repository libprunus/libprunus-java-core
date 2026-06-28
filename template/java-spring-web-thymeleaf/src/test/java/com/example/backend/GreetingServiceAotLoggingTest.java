package com.example.backend;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class GreetingServiceAotLoggingTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger serviceLogger;

    @BeforeEach
    void attachAppender() {
        serviceLogger = (Logger) LoggerFactory.getLogger(GreetingService.class);
        serviceLogger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        serviceLogger.detachAppender(appender);
        serviceLogger.setLevel(null);
    }

    @Test
    void rewrittenMethodEmitsEntryAndExitRecordsWithoutAlteringReturnValue() {
        String greeting = new GreetingService().greet("prunus");

        assertThat(greeting).isEqualTo("Hello, prunus!");
        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(2);
        assertThat(events).allMatch(event -> event.getLevel() == Level.INFO);
    }
}
