/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.testdata.mapping.keywords;

import java.util.List;
import java.util.Stack;

import org.rf.ide.core.testdata.mapping.table.IParsingMapper;
import org.rf.ide.core.testdata.mapping.table.ParsingStateHelper;
import org.rf.ide.core.testdata.model.FilePosition;
import org.rf.ide.core.testdata.model.RobotFileOutput;
import org.rf.ide.core.testdata.model.table.RobotExecutableRow;
import org.rf.ide.core.testdata.model.table.keywords.UserKeyword;
import org.rf.ide.core.testdata.text.read.IRobotTokenType;
import org.rf.ide.core.testdata.text.read.ParsingState;
import org.rf.ide.core.testdata.text.read.RobotLine;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;
import org.rf.ide.core.testdata.text.read.recognizer.executables.RobotSpecialTokens;

public class KeywordExecutableRowArgumentMapper implements IParsingMapper {

    private final ParsingStateHelper stateHelper;

    private final KeywordFinder keywordFinder;

    private final RobotSpecialTokens specialTokensRecognizer;

    public KeywordExecutableRowArgumentMapper() {
        this.stateHelper = new ParsingStateHelper();
        this.keywordFinder = new KeywordFinder();
        this.specialTokensRecognizer = new RobotSpecialTokens();
    }

    @Override
    public RobotToken map(RobotLine currentLine, Stack<ParsingState> processingState, RobotFileOutput robotFileOutput,
            RobotToken rt, FilePosition fp, String text) {
        UserKeyword keyword = keywordFinder.findOrCreateNearestKeyword(currentLine, processingState, robotFileOutput,
                rt, fp);
        List<IRobotTokenType> types = rt.getTypes();
        types.add(0, RobotTokenType.KEYWORD_ACTION_ARGUMENT);

        List<RobotToken> specialTokens = specialTokensRecognizer.recognize(fp, text);
        for (RobotToken token : specialTokens) {
            types.addAll(token.getTypes());
        }

        List<RobotExecutableRow<UserKeyword>> keywordExecutionRows = keyword.getKeywordExecutionRows();
        RobotExecutableRow<UserKeyword> robotExecutableRow = keywordExecutionRows.get(keywordExecutionRows.size() - 1);

        boolean commentContinue = false;
        if (!robotExecutableRow.getComment().isEmpty()) {
            int lineNumber = robotExecutableRow.getComment()
                    .get(robotExecutableRow.getComment().size() - 1)
                    .getLineNumber();
            commentContinue = (lineNumber == rt.getLineNumber());
        }

        if (text.startsWith("#") || commentContinue
                || RobotExecutableRow.isTsvComment(text, robotFileOutput.getFileFormat())) {
            types.remove(RobotTokenType.KEYWORD_ACTION_NAME);
            types.remove(RobotTokenType.KEYWORD_ACTION_ARGUMENT);
            rt.getTypes().add(0, RobotTokenType.START_HASH_COMMENT);
            robotExecutableRow.addCommentPart(rt);
        } else {
            if (robotExecutableRow.getAction().getFilePosition().isNotSet()) {
                types.remove(RobotTokenType.KEYWORD_ACTION_ARGUMENT);
                types.add(0, RobotTokenType.KEYWORD_ACTION_NAME);
                robotExecutableRow.setAction(rt);
            } else {
                robotExecutableRow.addArgument(rt);
            }
        }

        processingState.push(ParsingState.KEYWORD_INSIDE_ACTION_ARGUMENT);
        return rt;
    }

    @Override
    public boolean checkIfCanBeMapped(RobotFileOutput robotFileOutput, RobotLine currentLine, RobotToken rt,
            String text, Stack<ParsingState> processingState) {
        boolean result = false;
        ParsingState state = stateHelper.getCurrentStatus(processingState);
        result = (state == ParsingState.KEYWORD_INSIDE_ACTION || state == ParsingState.KEYWORD_INSIDE_ACTION_ARGUMENT);

        return result;
    }
}
