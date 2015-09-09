/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.core.testData.model.table.setting.mapping.suite;

import java.util.Stack;

import org.robotframework.ide.core.testData.model.FilePosition;
import org.robotframework.ide.core.testData.model.RobotFileOutput;
import org.robotframework.ide.core.testData.model.table.SettingTable;
import org.robotframework.ide.core.testData.model.table.mapping.ElementsUtility;
import org.robotframework.ide.core.testData.model.table.mapping.IParsingMapper;
import org.robotframework.ide.core.testData.model.table.setting.SuiteSetup;
import org.robotframework.ide.core.testData.text.read.ParsingState;
import org.robotframework.ide.core.testData.text.read.RobotLine;
import org.robotframework.ide.core.testData.text.read.recognizer.RobotToken;
import org.robotframework.ide.core.testData.text.read.recognizer.RobotTokenType;

import com.google.common.annotations.VisibleForTesting;


public class SuiteSetupMapper implements IParsingMapper {

    private final ElementsUtility utility;


    public SuiteSetupMapper() {
        this.utility = new ElementsUtility();
    }


    @Override
    public RobotToken map(RobotLine currentLine,
            Stack<ParsingState> processingState,
            RobotFileOutput robotFileOutput, RobotToken rt, FilePosition fp,
            String text) {
        rt.setType(RobotTokenType.SETTING_SUITE_SETUP_DECLARATION);
        rt.setText(new StringBuilder(text));

        SettingTable setting = robotFileOutput.getFileModel().getSettingTable();
        SuiteSetup setup = new SuiteSetup(rt);
        setting.addSuiteSetup(setup);
        processingState.push(ParsingState.SETTING_SUITE_SETUP);

        return rt;
    }


    @Override
    public boolean checkIfCanBeMapped(RobotFileOutput robotFileOutput,
            RobotLine currentLine, RobotToken rt, String text,
            Stack<ParsingState> processingState) {
        boolean result = false;
        if (rt.getTypes().contains(
                RobotTokenType.SETTING_SUITE_SETUP_DECLARATION)) {
            if (utility.isTheFirstColumn(currentLine, rt)) {
                if (isIncludedInSettingTable(currentLine, processingState)) {
                    result = true;
                } else {
                    // FIXME: it is in wrong place means no settings table
                    // declaration
                }
            } else {
                // FIXME: wrong place | | Library or | Library | Library X |
                // case.
            }
        }
        return result;
    }


    @VisibleForTesting
    protected boolean isIncludedInSettingTable(final RobotLine line,
            final Stack<ParsingState> processingState) {
        boolean result;
        if (!processingState.isEmpty()) {
            result = (processingState.get(processingState.size() - 1) == ParsingState.SETTING_TABLE_INSIDE);
        } else {
            result = false;
        }

        return result;
    }
}
