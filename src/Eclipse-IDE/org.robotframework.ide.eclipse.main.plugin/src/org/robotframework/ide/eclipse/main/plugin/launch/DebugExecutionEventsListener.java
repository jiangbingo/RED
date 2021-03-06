/*
 * Copyright 2017 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.launch;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.rf.ide.core.execution.LogLevel;
import org.rf.ide.core.execution.RobotDefaultAgentEventListener;
import org.rf.ide.core.execution.Status;
import org.rf.ide.core.execution.context.KeywordPosition;
import org.rf.ide.core.execution.context.RobotDebugExecutionContext;
import org.rf.ide.core.execution.server.AgentClient;
import org.rf.ide.core.execution.server.response.ContinueExecution;
import org.rf.ide.core.execution.server.response.EvaluateCondition;
import org.rf.ide.core.execution.server.response.ServerResponse.ResponseException;
import org.rf.ide.core.execution.server.response.StopExecution;
import org.rf.ide.core.testdata.RobotParser;
import org.robotframework.ide.eclipse.main.plugin.RedPlugin;
import org.robotframework.ide.eclipse.main.plugin.debug.model.RobotDebugTarget;
import org.robotframework.ide.eclipse.main.plugin.debug.utils.KeywordContext;
import org.robotframework.ide.eclipse.main.plugin.debug.utils.KeywordExecutionManager;
import org.robotframework.ide.eclipse.main.plugin.model.RobotSuiteFile;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.RobotFormEditor;

public class DebugExecutionEventsListener extends RobotDefaultAgentEventListener {

    private AgentClient client;

    private final RobotDebugTarget debugTarget;

    private final RobotDebugExecutionContext executionContext;

    private final KeywordExecutionManager keywordExecutionManager;

    private boolean isStopping;

    private boolean isBreakpointConditionFulfilled;

    public DebugExecutionEventsListener(final RobotDebugTarget debugTarget, final List<IResource> resourcesUnderDebug) {
        this.debugTarget = debugTarget;
        this.executionContext = new RobotDebugExecutionContext();
        this.keywordExecutionManager = new KeywordExecutionManager(resourcesUnderDebug);
    }

    @Override
    public void setClient(final AgentClient client) {
        this.client = client;
        this.debugTarget.setClient(client);
    }

    public void terminated() {
        debugTarget.terminated();
    }

    @Override
    public void handleAgentInitializing() {
        debugTarget.started();
    }

    @Override
    public void handleSuiteStarted(final String suiteName, final File suiteFilePath) {
        final IPath suitePath = Path.fromOSString(suiteFilePath.getAbsolutePath());

        final IFile currentSuiteFile = keywordExecutionManager.extractCurrentSuite(suitePath);
        if (currentSuiteFile != null) {
            final RobotSuiteFile robotSuiteFile = RedPlugin.getModelManager().createSuiteFile(currentSuiteFile);
            final RobotParser robotParser = robotSuiteFile.getProject().getEagerRobotParser();
            executionContext.startSuite(robotParser.parse(currentSuiteFile.getLocation().toFile()).get(0), robotParser);
        }
    }

    @Override
    public void handleSuiteEnded(final String suiteName, final int elapsedTime, final Status status,
            final String errorMessage) {
        debugTarget.clearStackFrames();
        executionContext.endSuite();
    }

    @Override
    public void handleTestStarted(final String testCaseName, final String testCaseLongName) {
        executionContext.startTest(testCaseName);
    }

    @Override
    public void handleTestEnded(final String testCaseName, final String testCaseLongName, final int elapsedTime,
            final Status status, final String errorMessage) {
        executionContext.endTest();
    }

    @Override
    public void handleKeywordStarted(final String name, final String type, final List<String> args) {
        prepareKeywordStart(name, type, args);

        executionContext.startKeyword(name, type, args);

        String executedFileName = keywordExecutionManager.getCurrentSuiteName();

        final KeywordPosition keywordPosition = executionContext.findKeywordPosition();
        final int keywordLineNumber = keywordPosition.getLineNumber();
        final String currentResourceFile = keywordExecutionManager
                .extractCurrentResourceFile(keywordPosition.getFilePath());
        if (currentResourceFile != null) {
            executedFileName = new File(currentResourceFile).getName();
        }

        if (shouldStopExecution(executedFileName, keywordLineNumber)) {
            activateSourcePageInActiveEditor();
            isStopping = true;
            resetSteppingState();
            resetStackFramesState();
        } else {
            isStopping = false;
        }

        final KeywordContext newKeywordContext = new KeywordContext(
                keywordExecutionManager.extractExecutedFileNameWithParentFolderInfo(executedFileName),
                keywordLineNumber, null);
        debugTarget.getCurrentKeywordsContext().put(name, newKeywordContext);
    }

    private void activateSourcePageInActiveEditor() {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        workbench.getDisplay()
                .syncExec(() -> RobotFormEditor.activateSourcePageInActiveEditor(workbench.getActiveWorkbenchWindow()));
    }

    private void prepareKeywordStart(final String name, final String type, final List<String> args) {
        if (executionContext.isSuiteSetupTeardownKeyword(type) && !executionContext.isInSuite()
                && keywordExecutionManager.getCurrentSuiteLocation() != null) {
            handleInitFile();
        } else if (executionContext.isTestCaseTeardownKeyword(type)) {
            debugTarget.clearStackFrames();
        }

        if (keywordExecutionManager.getCurrentSuiteFile() == null) {
            final String message = String.format(
                    "Invalid execution context: suite='%s', name='%s', type='%s', args='%s'",
                    keywordExecutionManager.getCurrentSuiteName(), name, type, args);
            showError("Debug Execution Context Error", message);
            throw new RobotAgentEventsListenerException(message);
        }
    }

    private void handleInitFile() {
        final IFile currentInitFile = keywordExecutionManager.getCurrentInitFile();
        if (currentInitFile == null) {
            tryToFindInitSuiteFile();
        } else {
            switchSuite(currentInitFile.getParent(), currentInitFile);
        }
    }

    private void tryToFindInitSuiteFile() {
        final IContainer suiteContainer = ResourcesPlugin.getWorkspace()
                .getRoot()
                .getContainerForLocation(keywordExecutionManager.getCurrentSuiteLocation());
        if (suiteContainer != null
                && (suiteContainer.getType() == IResource.FOLDER || suiteContainer.getType() == IResource.PROJECT)) {
            final Optional<IFile> initFile = findInitSuiteFile(suiteContainer);
            if (initFile.isPresent()) {
                keywordExecutionManager.setCurrentInitFile(initFile.get());
                switchSuite(suiteContainer, initFile.get());
            }
        }
    }

    private Optional<IFile> findInitSuiteFile(final IContainer suiteContainer) {
        for (final String initFileName : Arrays.asList("__init__.robot", "__init__.txt", "__init__.tsv")) {
            final IResource member = suiteContainer.findMember(initFileName);
            if (member != null && member.getType() == IResource.FILE) {
                return Optional.of((IFile) member);
            }
        }
        return Optional.empty();
    }

    private void switchSuite(final IContainer suiteContainer, final IFile suiteFile) {
        keywordExecutionManager.setCurrentSuiteParent(suiteContainer);
        keywordExecutionManager.setCurrentSuiteName(suiteFile.getName());
        keywordExecutionManager.setCurrentSuiteFile(suiteFile);
        final RobotSuiteFile robotSuiteFile = RedPlugin.getModelManager().createSuiteFile(suiteFile);
        final RobotParser robotParser = robotSuiteFile.getProject().getEagerRobotParser();
        executionContext.startSuite(robotParser.parse(suiteFile.getLocation().toFile()).get(0), robotParser);
    }

    private void showError(final String title, final String message) {
        final Display display = PlatformUI.getWorkbench().getDisplay();
        display.syncExec(() -> MessageDialog.openError(display.getActiveShell(), title, message));
    }

    private boolean shouldStopExecution(final String executedFileName, final int keywordLineNumber) {
        final boolean hasBreakpoint = keywordExecutionManager.hasBreakpointAtCurrentKeywordPosition(executedFileName,
                keywordLineNumber, debugTarget);
        return hasBreakpoint || (keywordLineNumber >= 0 && debugTarget.getRobotThread().isStepping()
                && !debugTarget.hasStepOver() && !debugTarget.hasStepReturn());
    }

    private void resetSteppingState() {
        if (debugTarget.getRobotThread().isStepping()) {
            debugTarget.getRobotThread().setSteppingOver(false);
            debugTarget.getRobotThread().setSteppingReturn(false);
        }
    }

    private void resetStackFramesState() {
        debugTarget.setHasStackFramesCreated(false);
    }

    @Override
    public void handleKeywordEnded(final String name, final String type) {
        debugTarget.getCurrentKeywordsContext().remove(name);
        executionContext.endKeyword(type);
    }

    @Override
    public void handleResourceImport(final File resourceFilePath) {
        executionContext.resourceImport(resourceFilePath);
    }

    @Override
    public void handleGlobalVariables(final Map<String, String> globalVars) {
        debugTarget.getRobotVariablesManager().setGlobalVariables(globalVars);
    }

    @Override
    public void handleVariables(final Map<String, Object> vars) {
        debugTarget.getLastKeywordFromCurrentContext().setVariables(vars);
        debugTarget.getRobotVariablesManager().sortVariablesNames(vars);
    }

    @Override
    public void handleLogMessage(final String msg, final LogLevel level, final String timestamp) {
    }

    @Override
    public void handleOutputFile(final File outputFilepath) {
    }

    @Override
    public void handleCheckCondition() {
        try {
            if (keywordExecutionManager.hasBreakpointCondition()) {
                client.send(new EvaluateCondition(keywordExecutionManager.getBreakpointConditionCall()));
            } else if (isStopping) {
                client.send(new StopExecution());
            } else {
                client.send(new ContinueExecution());
            }
        } catch (ResponseException | IOException e) {
            throw new RobotAgentEventsListenerException("Unable to send response to client", e);
        }
    }

    @Override
    public void handleConditionError(final String error) {
        isBreakpointConditionFulfilled = true;
        showError("Conditional Breakpoint Error", "Reason:\n" + error);
    }

    @Override
    public void handleConditionResult(final boolean result) {
        isBreakpointConditionFulfilled = result;
    }

    @Override
    public void handleConditionChecked() {
        try {
            if (isStopping && isBreakpointConditionFulfilled) {
                client.send(new StopExecution());
            } else {
                client.send(new ContinueExecution());
            }
            isBreakpointConditionFulfilled = false;
            keywordExecutionManager.resetBreakpointCondition();
        } catch (ResponseException | IOException e) {
            throw new RobotAgentEventsListenerException("Unable to send response to client", e);
        }
    }

    @Override
    public void handleClosed() {
        debugTarget.terminated();
    }

    @Override
    public void handlePaused() {
        debugTarget.suspended(DebugEvent.CLIENT_REQUEST);
    }
}
