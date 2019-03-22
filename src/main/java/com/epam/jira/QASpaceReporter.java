package com.epam.jira;

import com.epam.jira.core.TestResultProcessor;
import cucumber.api.TestCase;
import cucumber.api.event.*;
import gherkin.pickles.PickleTag;
import org.apache.commons.text.StringEscapeUtils;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.*;

public class QASpaceReporter implements EventListener {

    private boolean listenedTest;
    private Pattern tagPattern = Pattern.compile(TAG_FIND_EXPRESSION);

    private static final String TRIGGER_TAG = "@JIRATestKey";
    private static final String TAG_FIND_EXPRESSION = String.format("(?<=(%s\\()).*(?=(\\)))", TRIGGER_TAG);
    private static final HashMap<String, String> MIME_TYPES_EXTENSIONS = new HashMap<>();

    static {
        MIME_TYPES_EXTENSIONS.put("image/bmp", "bmp");
        MIME_TYPES_EXTENSIONS.put("image/gif", "gif");
        MIME_TYPES_EXTENSIONS.put("image/jpeg", "jpg");
        MIME_TYPES_EXTENSIONS.put("image/png", "png");
        MIME_TYPES_EXTENSIONS.put("image/svg+xml", "svg");
        MIME_TYPES_EXTENSIONS.put("video/ogg", "ogg");
        MIME_TYPES_EXTENSIONS.put("text/plain", "txt");
    }


    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseFinished.class, getTestCaseFinishedHandler());
        publisher.registerHandlerFor(TestCaseStarted.class, getTestCaseStartedHandler());
        publisher.registerHandlerFor(TestRunFinished.class, getTestRunFinishedHandler());
        publisher.registerHandlerFor(EmbedEvent.class, getEmbedEventHandler());
        publisher.registerHandlerFor(WriteEvent.class, getWriteEventHandler());

    }

    private void handleStartOfTestCase(TestCaseStarted event) {
        TestCase testCase = event.testCase;
        if (isJIRATestKeyPresent(testCase)) {
            listenedTest = true;
            TestResultProcessor.startJiraAnnotatedTest(getJIRAKey(testCase));
        }
    }

    private void handleTestCaseFinished(TestCaseFinished event) {
        if (listenedTest) {
            TestResultProcessor.setStatus(event.result.getStatus().firstLetterCapitalizedName());
            TestResultProcessor.setTime(durationToString(event.result.getDuration()));
            if (event.result.getError() != null) {
                TestResultProcessor.addException(event.result.getError());
            }
            listenedTest = false;
        }
    }

    private void handleTestRunFinished(TestRunFinished event) {
        TestResultProcessor.saveResults();
    }

    private void handleEmbedEvent(EmbedEvent event) {
        if (listenedTest) {
            try {
                String filePath = String.format("attachment_%s.%s", LocalDateTime.now().toString().replace(":", "-"), MIME_TYPES_EXTENSIONS.get(event.mimeType));
                Files.write(Paths.get(filePath), event.data);
                sendTempFileProcessor(filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleWriteEvent(WriteEvent event) {
        if (listenedTest) {
            try {
                String filePath = String.format("attachment_%s.txt", LocalDateTime.now().toString().replace(":", "-"));
                Files.write(Paths.get(filePath), StringEscapeUtils.escapeJson(event.text).getBytes());
                sendTempFileProcessor(filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendTempFileProcessor(String filePath) {
        File file = new File(filePath);
        TestResultProcessor.addAttachment(file);
        file.delete();
    }

    private String getJIRAKey(TestCase testCase) {
        return testCase.getTags().stream().filter(tag -> isStringContainsTag(tag.getName())).findFirst()
                .map(this::extractKey).orElse("");
    }

    private boolean isJIRATestKeyPresent(TestCase testCase) {
        return testCase.getTags().stream().anyMatch(tag -> isStringContainsTag(tag.getName()));
    }

    private String extractKey(PickleTag tag) {
        String tagString = tag.getName();
        String result = null;
        Matcher matcher = tagPattern.matcher(tagString);
        if (matcher.find()) {
            result = tagString.substring(matcher.start(), matcher.end());
        }
        return result;
    }


    private boolean isStringContainsTag(String string) {
        return string.contains(TRIGGER_TAG);
    }

    private String durationToString(long duration) {
        return String.valueOf(MINUTES.convert(duration, NANOSECONDS)) +
                "m " +
                SECONDS.convert(duration, NANOSECONDS) +
                "." +
                MILLISECONDS.convert(duration, NANOSECONDS) +
                "s";
    }

    private EventHandler<TestCaseStarted> getTestCaseStartedHandler() {
        return this::handleStartOfTestCase;
    }

    private EventHandler<TestCaseFinished> getTestCaseFinishedHandler() {
        return this::handleTestCaseFinished;
    }

    private EventHandler<TestRunFinished> getTestRunFinishedHandler() {
        return this::handleTestRunFinished;
    }

    private EventHandler<EmbedEvent> getEmbedEventHandler() {
        return this::handleEmbedEvent;
    }

    private EventHandler<WriteEvent> getWriteEventHandler() {
        return this::handleWriteEvent;
    }
}