/*
 * Copyright 2017 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.executor;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.rf.ide.core.executor.RunCommandLineCallBuilder.RunCommandLine;

public class RunCommandLineCallBuilderTest {

    @Test
    public void testSimpleCall_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .build();
        assertThat(cmdLine.getCommandLine()).hasSize(7).containsSubsequence("/x/y/z/python", "-m", "robot.run",
                "--listener", "--argumentfile");
    }

    @Test
    public void testSimpleCall_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .build();
        assertThat(cmdLine.getCommandLine()).hasSize(5).containsSubsequence("/x/y/z/python", "-m", "robot.run",
                "--listener");
    }

    @Test
    public void testCallWithProject_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .withProject(new File("project"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(8).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                "--argumentfile");
        assertThat(commandLine[commandLine.length - 1]).endsWith("project");
    }

    @Test
    public void testCallWithProject_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final File projectFile = new File("project");
        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .withProject(projectFile)
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(6).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener");
        assertThat(commandLine[commandLine.length - 1]).isEqualTo(projectFile.getAbsolutePath());
    }

    @Test
    public void testCallWithAdditionalDataPaths_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final File f1 = new File("a");
        final File f2 = new File("b");
        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .withAdditionalProjectsLocations(newArrayList(f1, f2))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(9).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                "--argumentfile", f1.getAbsolutePath(), f2.getAbsolutePath());
    }

    @Test
    public void testCallWithAdditionalDataPaths_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final File f1 = new File("a");
        final File f2 = new File("b");
        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .withAdditionalProjectsLocations(newArrayList(f1, f2))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(7).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                f1.getAbsolutePath(), f2.getAbsolutePath());
    }

    @Test
    public void testCallWrappedWithOtherExecutable_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .withExecutableFile("exec")
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(8).containsSubsequence("exec", "/x/y/z/python", "-m", "robot.run", "--listener",
                "--argumentfile");
    }

    @Test
    public void testCallWrappedWithOtherExecutable_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .withExecutableFile("exec")
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(6).containsSubsequence("exec", "/x/y/z/python", "-m", "robot.run",
                "--listener");
    }

    @Test
    public void testCallWrappedWithOtherExecutableAndArguments_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .withExecutableFile("exec")
                .addUserArgumentsForExecutableFile("args")
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(9).containsSubsequence("exec", "args", "/x/y/z/python", "-m", "robot.run",
                "--listener", "--argumentfile");
    }

    @Test
    public void testCallWrappedWithOtherExecutableAndArguments_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .withExecutableFile("exec")
                .addUserArgumentsForExecutableFile("args")
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(7).containsSubsequence("exec", "args", "/x/y/z/python", "-m", "robot.run",
                "--listener");
    }

    @Test
    public void testCallNotWrappedWithExecutableButWithArgumentsHaveNothing_withRuntimeEnvironment_argsFile()
            throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .addUserArgumentsForExecutableFile("args")
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(7).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                "--argumentfile");
    }

    @Test
    public void testCallNotWrappedWithExecutableButWithArgumentsHaveNothing_withRuntimeEnvironment_argsInline()
            throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .addUserArgumentsForExecutableFile("args")
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(5).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener");
    }

    @Test
    public void testCallWrappedWithOtherExecutableAndArguments_withRuntimeEnvironment_withSequenceRobotCommandArg()
            throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .withExecutableFile("exec")
                .addUserArgumentsForExecutableFile("args")
                .useSingleRobotCommandLineArg(false)
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(7).containsSubsequence("exec", "args", "/x/y/z/python", "-m", "robot.run",
                "--listener");
    }

    @Test
    public void testCallWrappedWithOtherExecutableAndArguments_withRuntimeEnvironment_withSingleRobotCommandArg()
            throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .withExecutableFile("exec")
                .addUserArgumentsForExecutableFile("args")
                .useSingleRobotCommandLineArg(true)
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(3).containsSubsequence("exec", "args");
        assertThat(commandLine[2]).containsSequence("/x/y/z/python", "-m", "robot.run", "--listener");
    }

    @Test
    public void testCallWrappedWithOtherExecutableAndSeveralArguments_withRuntimeEnvironment_withSequenceRobotCommandArg()
            throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .withExecutableFile("exec")
                .addUserArgumentsForExecutableFile("a1 -a2 \"a3 a4\" -a5 a6 a7")
                .useSingleRobotCommandLineArg(false)
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(cmdLine.getArgumentFile().isPresent()).isFalse();
        assertThat(commandLine).hasSize(12).containsSubsequence("exec", "a1", "-a2", "a3 a4", "-a5", "a6", "a7",
                "/x/y/z/python", "-m", "robot.run", "--listener");
    }

    @Test
    public void testCallWithInterpreterArgumentsAdded_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .addUserArgumentsForInterpreter("args")
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(8).containsSubsequence("/x/y/z/python", "args", "-m", "robot.run", "--listener",
                "--argumentfile");
    }

    @Test
    public void testCallWithInterpreterArgumentsAdded_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .addUserArgumentsForInterpreter("args")
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(cmdLine.getArgumentFile().isPresent()).isFalse();
        assertThat(commandLine).hasSize(6).containsSubsequence("/x/y/z/python", "args", "-m", "robot.run",
                "--listener");
    }

    @Test
    public void testCallWithSeveralInterpreterArgumentsAdded_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .addUserArgumentsForInterpreter("a1 -a2 \"a3 a4\" -a5 a6 a7")
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(cmdLine.getArgumentFile().isPresent()).isFalse();
        assertThat(commandLine).hasSize(11).containsSubsequence("/x/y/z/python", "a1", "-a2", "a3 a4", "-a5", "a6",
                "a7", "-m", "robot.run", "--listener");
    }

    @Test
    public void testCallWithClassPathForJython_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Jython, "/x/y/z/jython");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .addLocationsToClassPath(newArrayList("cp/path"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(9).containsSubsequence("/x/y/z/jython", "-J-cp", "cp/path", "-m", "robot.run",
                "--listener", "--argumentfile");
    }

    @Test
    public void testCallWithClassPathForJython_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Jython, "/x/y/z/jython");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .addLocationsToClassPath(newArrayList("cp/path"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(cmdLine.getArgumentFile().isPresent()).isFalse();
        assertThat(commandLine).hasSize(7).containsSubsequence("/x/y/z/jython", "-J-cp", "cp/path", "-m", "robot.run",
                "--listener");
    }

    @Test
    public void testCallWithSuitesInArgumentFile_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .suitesToRun(newArrayList("s1", "s2"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(7).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                "--argumentfile");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--suite s1");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--suite s2");
    }

    @Test
    public void testCallWithSuitesInArgumentFile_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .suitesToRun(newArrayList("s1", "s2"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(cmdLine.getArgumentFile().isPresent()).isFalse();
        assertThat(commandLine).hasSize(9).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener", "-s",
                "s1", "-s", "s2");
    }

    @Test
    public void testCallWithTestsInArgumentFile_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .testsToRun(newArrayList("t1", "t2"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(7).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                "--argumentfile");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--test t1");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--test t2");
    }

    @Test
    public void testCallWithTestsInArgumentFile_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .testsToRun(newArrayList("t1", "t2"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(cmdLine.getArgumentFile().isPresent()).isFalse();
        assertThat(commandLine).hasSize(9).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener", "-t",
                "t1", "-t", "t2");
    }

    @Test
    public void testCallWithIncludedTagsInArgumentFile_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .includeTags(newArrayList("tag1", "tag2"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(7).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                "--argumentfile");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--include tag1");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--include tag2");
    }

    @Test
    public void testCallWithIncludedTagsInArgumentFile_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .includeTags(newArrayList("tag1", "tag2"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(cmdLine.getArgumentFile().isPresent()).isFalse();
        assertThat(commandLine).hasSize(9).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener", "-i",
                "tag1", "-i", "tag2");
    }

    @Test
    public void testCallWithExcludedTagsInArgumentFile_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .excludeTags(newArrayList("tag1", "tag2"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(7).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                "--argumentfile");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--exclude tag1");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--exclude tag2");
    }

    @Test
    public void testCallWithExcludedTagsInArgumentFile_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .excludeTags(newArrayList("tag1", "tag2"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(cmdLine.getArgumentFile().isPresent()).isFalse();
        assertThat(commandLine).hasSize(9).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener", "-e",
                "tag1", "-e", "tag2");
    }

    @Test
    public void testCallWithVarFilesInArgumentFile_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .addVariableFiles(newArrayList("var1.py", "var2.py"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(7).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                "--argumentfile");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--variablefile var1.py");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--variablefile var2.py");
    }

    @Test
    public void testCallWithVarFilesInArgumentFile_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .addVariableFiles(newArrayList("var1.py", "var2.py"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(cmdLine.getArgumentFile().isPresent()).isFalse();
        assertThat(commandLine).hasSize(9).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener", "-V",
                "var1.py", "-V", "var2.py");
    }

    @Test
    public void testCallWithPythonpathInArgumentFile_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .addLocationsToPythonPath(newArrayList("path1", "path2"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(7).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                "--argumentfile");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--pythonpath path1:path2");
    }

    @Test
    public void testCallWithPythonpathInArgumentFile_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .addLocationsToPythonPath(newArrayList("path1", "path2"))
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(cmdLine.getArgumentFile().isPresent()).isFalse();
        assertThat(commandLine).hasSize(7).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener", "-P",
                "path1:path2");
    }

    @Test
    public void testCallWithUserArgumentsInArgumentFile_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .addUserArgumentsForRobot("--arg val1 -X \"val 2\" --other --other2")
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(7).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                "--argumentfile");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--arg    val1");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("-X       val 2");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--other");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--other2");
    }

    @Test
    public void testCallWithUserArgumentsInArgumentFile_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .addUserArgumentsForRobot("--arg val1 -X \"val 2\" --other --other2")
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(cmdLine.getArgumentFile().isPresent()).isFalse();
        assertThat(commandLine).hasSize(11).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                "--arg", "val1", "-X", "val 2", "--other", "--other2");
    }

    @Test
    public void testCallForDryrunInArgumentFile_withRuntimeEnvironment_argsFile() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(true)
                .enableDryRun()
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(commandLine).hasSize(7).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                "--argumentfile");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--prerunmodifier");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--runemptysuite");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--dryrun");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--output         NONE");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--report         NONE");
        assertThat(cmdLine.getArgumentFile().get().generateContent()).contains("--log            NONE");
    }

    @Test
    public void testCallForDryrunInArgumentFile_withRuntimeEnvironment_argsInline() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .enableDryRun()
                .build();
        final String[] commandLine = cmdLine.getCommandLine();
        assertThat(cmdLine.getArgumentFile().isPresent()).isFalse();
        assertThat(commandLine).hasSize(15).containsSubsequence("/x/y/z/python", "-m", "robot.run", "--listener",
                "--prerunmodifier", "--runemptysuite", "--dryrun", "--output", "NONE", "--report", "NONE", "--log",
                "NONE");
    }

    @Test
    public void testWrappingArgumentsContainingSpaces() throws IOException {
        final RobotRuntimeEnvironment env = prepareEnvironment(SuiteExecutor.Python, "/x/y/z/python");

        final RunCommandLine cmdLine = RunCommandLineCallBuilder.forEnvironment(env, 12345)
                .useArgumentFile(false)
                .withExecutableFile("exec")
                .addUserArgumentsForExecutableFile("--a1 \"a b c\"")
                .addUserArgumentsForInterpreter("-a2 \"x y z\"")
                .addUserArgumentsForRobot("--log \"path to file.log\"")
                .build();
        final String[] commandLine = cmdLine.getCommandLineWithWrappedArguments();
        assertThat(cmdLine.getArgumentFile().isPresent()).isFalse();
        assertThat(commandLine).hasSize(12).containsSubsequence("exec", "--a1", "\"a b c\"", "/x/y/z/python", "-a2",
                "\"x y z\"", "-m", "robot.run", "--listener", "--log", "\"path to file.log\"");
    }

    private static RobotRuntimeEnvironment prepareEnvironment(final SuiteExecutor executor,
            final String interpreterPath) {
        final RobotRuntimeEnvironment env = mock(RobotRuntimeEnvironment.class);
        when(env.getInterpreter()).thenReturn(executor);
        when(env.getPythonExecutablePath()).thenReturn(interpreterPath);
        return env;
    }

}
