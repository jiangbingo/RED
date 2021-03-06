/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.model.cmd.cases;

import static com.google.common.collect.Iterables.any;

import java.util.Arrays;
import java.util.List;

import org.rf.ide.core.testdata.model.table.TestCaseTable;
import org.robotframework.ide.eclipse.main.plugin.model.RobotCase;
import org.robotframework.ide.eclipse.main.plugin.model.RobotCasesSection;
import org.robotframework.ide.eclipse.main.plugin.model.RobotModelEvents;
import org.robotframework.ide.eclipse.main.plugin.model.cmd.NamesGenerator;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.EditorCommand;
import org.robotframework.services.event.RedEventBroker;

import com.google.common.base.Predicate;

public class InsertCasesCommand extends EditorCommand {

    private final RobotCasesSection casesSection;
    private final int index;
    private final List<RobotCase> casesToInsert;

    public InsertCasesCommand(final RobotCasesSection casesSection, final RobotCase[] casesToInsert) {
        this(casesSection, -1, casesToInsert);
    }

    public InsertCasesCommand(final RobotCasesSection casesSection, final int index, final RobotCase[] casesToInsert) {
        this.casesSection = casesSection;
        this.index = index;
        this.casesToInsert = Arrays.asList(casesToInsert);
    }

    @Override
    public void execute() throws CommandExecutionException {
        final TestCaseTable testCaseTable = casesSection.getLinkedElement();

        int counter = index;
        for (final RobotCase testCase : casesToInsert) {
            testCase.setParent(casesSection);
            testCase.getLinkedElement().setParent(testCaseTable);

            if (nameChangeIsRequired(testCase)) {
                final String newName = NamesGenerator.generateUniqueName(casesSection, testCase.getName());
                testCase.getLinkedElement().getTestName().setText(newName);
            }

            if (counter == -1) {
                casesSection.getChildren().add(testCase);
                testCaseTable.addTest(testCase.getLinkedElement());
            } else {
                casesSection.getChildren().add(counter, testCase);
                testCaseTable.addTest(testCase.getLinkedElement(), counter);
                counter++;
            }
        }

        if (!casesToInsert.isEmpty()) {
            RedEventBroker.using(eventBroker)
                    .additionallyBinding(RobotModelEvents.ADDITIONAL_DATA).to(casesToInsert)
                    .send(RobotModelEvents.ROBOT_CASE_ADDED, casesSection);
        }
    }

    private boolean nameChangeIsRequired(final RobotCase testCase) {
        return any(casesSection.getChildren(), new Predicate<RobotCase>() {
            @Override
            public boolean apply(final RobotCase theCase) {
                return theCase.getName().equalsIgnoreCase(testCase.getName());
            }
        });
    }

    @Override
    public List<EditorCommand> getUndoCommands() {
        return newUndoCommands(new DeleteCasesCommand(casesToInsert));
    }
}
