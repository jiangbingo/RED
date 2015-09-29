/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.texteditor.contentAssist;

import java.util.Arrays;

import org.eclipse.swt.graphics.Image;
import org.robotframework.ide.eclipse.main.plugin.assist.RedKeywordProposal;
import org.robotframework.red.graphics.ImagesManager;

import com.google.common.base.Joiner;

public class ContentAssistKeywordContext {

    private final RedKeywordProposal proposal;

    public ContentAssistKeywordContext(final RedKeywordProposal proposal) {
        this.proposal = proposal;
    }

    public String getLibName() {
        return proposal.getLabelDecoration().substring(2);
    }
    
    public String getArguments() {
        return proposal.getArgumentsLabel();
    }

    public String getDescription() {
        final String separator = System.lineSeparator();

        final String name = "Name: " + proposal.getLabel();
        final String source = "Source: " + proposal.getSourceName();
        final String args = "Arguments: " + proposal.getArgumentsLabel();
        final String doc = System.lineSeparator() + proposal.getDocumentation();
        return Joiner.on(separator).join(Arrays.asList(name, source, args, doc));
    }
    
    public Image getImage() {
        return ImagesManager.getImage(proposal.getImage());
    }
}
