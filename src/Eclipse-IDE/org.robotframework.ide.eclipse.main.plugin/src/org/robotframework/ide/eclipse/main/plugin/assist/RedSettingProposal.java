/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.assist;

import org.eclipse.jface.resource.ImageDescriptor;
import org.robotframework.ide.eclipse.main.plugin.RedImages;
import org.robotframework.ide.eclipse.main.plugin.assist.RedSettingProposals.SettingTarget;

class RedSettingProposal extends BaseAssistProposal {

    private final SettingTarget target;

    RedSettingProposal(final String settingName, final SettingTarget target, final ProposalMatch match) {
        super(settingName, match);
        this.target = target;
    }

    @Override
    public ImageDescriptor getImage() {
        return RedImages.getRobotSettingImage();
    }

    @Override
    public boolean hasDescription() {
        return true;
    }

    @Override
    public String getDescription() {
        return RedSettingProposals.getSettingDescription(target, content, "");
    }
}
