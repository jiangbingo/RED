/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.testdata.model.table.setting.views;

import java.util.List;

import org.rf.ide.core.testdata.model.table.setting.TestSetup;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;

public class TestSetupView extends TestSetup {

    private final List<TestSetup> setups;

    public TestSetupView(final List<TestSetup> setups) {
        super(setups.get(0).getDeclaration());
        this.setups = setups;
        // join setup for this view
        final TestSetup setup = new TestSetup(getDeclaration());
        OneSettingJoinerHelper.joinKeywordBase(setup, setups);
        copyWithoutJoinIfNeededExecution(setup);
    }

    private void copyWithoutJoinIfNeededExecution(final TestSetup setup) {
        super.setKeywordName(setup.getKeywordName());
        for (final RobotToken arg : setup.getArguments()) {
            super.addArgument(arg);
        }

        for (final RobotToken commentText : setup.getComment()) {
            super.addCommentPart(commentText);
        }
    }

    @Override
    public void setKeywordName(final String keywordName) {
        joinIfNeeded();
        super.setKeywordName(keywordName);
    }

    @Override
    public void setKeywordName(final RobotToken keywordName) {
        joinIfNeeded();
        super.setKeywordName(keywordName);
    }

    @Override
    public void addArgument(final String argument) {
        joinIfNeeded();
        super.addArgument(argument);
    }

    @Override
    public void addArgument(final RobotToken argument) {
        joinIfNeeded();
        super.addArgument(argument);
    }

    @Override
    public void setArgument(final int index, final String argument) {
        joinIfNeeded();
        super.setArgument(index, argument);
    }

    @Override
    public void setArgument(final int index, final RobotToken argument) {
        joinIfNeeded();
        super.setArgument(index, argument);
    }

    @Override
    public void setComment(final String comment) {
        joinIfNeeded();
        super.setComment(comment);
    }

    @Override
    public void setComment(final RobotToken rt) {
        joinIfNeeded();
        super.setComment(rt);
    }

    @Override
    public void addCommentPart(final RobotToken rt) {
        joinIfNeeded();
        super.addCommentPart(rt);
    }

    private synchronized void joinIfNeeded() {
        if (setups.size() > 1) {
            TestSetup joined = new TestSetup(getDeclaration());
            OneSettingJoinerHelper.joinKeywordBase(joined, setups);
            setups.clear();
            setups.add(joined);
        }
    }
}