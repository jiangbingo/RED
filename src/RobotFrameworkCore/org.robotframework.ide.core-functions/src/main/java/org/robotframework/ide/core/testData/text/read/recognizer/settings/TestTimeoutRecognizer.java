/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.core.testData.text.read.recognizer.settings;

import java.util.regex.Pattern;

import org.robotframework.ide.core.testData.text.read.recognizer.ATokenRecognizer;
import org.robotframework.ide.core.testData.text.read.recognizer.RobotTokenType;


public class TestTimeoutRecognizer extends ATokenRecognizer {

    public static final Pattern EXPECTED = Pattern.compile("[ ]?("
            + createUpperLowerCaseWord("Test") + "[\\s]+"
            + createUpperLowerCaseWord("Timeout") + "[\\s]*:" + "|"
            + createUpperLowerCaseWord("Test") + "[\\s]+"
            + createUpperLowerCaseWord("Timeout") + ")");


    public TestTimeoutRecognizer() {
        super(EXPECTED, RobotTokenType.SETTING_TEST_TIMEOUT_DECLARATION);
    }
}
