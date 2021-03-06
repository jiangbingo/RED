/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.testdata.model.table.keywords;

import org.rf.ide.core.testdata.model.ATags;
import org.rf.ide.core.testdata.model.ModelType;
import org.rf.ide.core.testdata.text.read.IRobotTokenType;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;

public class KeywordTags extends ATags<UserKeyword> {

    private static final long serialVersionUID = 3856438108905759777L;

    public KeywordTags(RobotToken declaration) {
        super(declaration);
    }

    @Override
    public ModelType getModelType() {
        return ModelType.USER_KEYWORD_TAGS;
    }

    @Override
    public IRobotTokenType getTagType() {
        return RobotTokenType.KEYWORD_SETTING_TAGS_TAG_NAME;
    }

    @Override
    public IRobotTokenType getDeclarationTagType() {
        return RobotTokenType.KEYWORD_SETTING_TAGS;
    }
    
    public KeywordTags copy() {
        final KeywordTags keywordTags = new KeywordTags(this.getDeclaration().copyWithoutPosition());
        for (final RobotToken tag : getTags()) {
            keywordTags.addTag(tag.copyWithoutPosition());
        }
        for (final RobotToken commentToken : getComment()) {
            keywordTags.addCommentPart(commentToken.copyWithoutPosition());
        }
        return keywordTags;
    }
}
