/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.tableeditor.cases;

import java.util.ArrayList;
import java.util.List;

import org.robotframework.ide.eclipse.main.plugin.model.RobotCase;
import org.robotframework.ide.eclipse.main.plugin.model.RobotElement;
import org.robotframework.ide.eclipse.main.plugin.model.cmd.cases.SetCaseNameCommand;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.EditorCommand;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.code.KeywordCallsTableValuesChangingCommandsCollector;

/**
 * @author Michal Anglart
 *
 */
public class CasesTableValuesChangingCommandsCollector {

    public List<? extends EditorCommand> collectForRemoval(final RobotElement element, final int column,
            final int numberOfColumns) {
        return collect(element, null, column, numberOfColumns);
    }

    public List<? extends EditorCommand> collectForChange(final RobotElement element, final String newValue,
            final int column, final int numberOfColumns) {
        return collect(element, newValue, column, numberOfColumns);
    }

    private List<? extends EditorCommand> collect(final RobotElement element, final String value, final int column,
            final int numberOfColumns) {
        final List<EditorCommand> commands = new ArrayList<>();

        if (element instanceof RobotCase) {
            final RobotCase testCase = (RobotCase) element;
            if (column == 0) {
                commands.add(new SetCaseNameCommand(testCase, value));
            }
        } else {
            final List<? extends EditorCommand> callCommands = new KeywordCallsTableValuesChangingCommandsCollector()
                    .collect(element, value, column, numberOfColumns);
            commands.addAll(callCommands);
        }
        return commands;
    }
}
