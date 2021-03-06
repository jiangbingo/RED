/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.executor;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.rf.ide.core.executor.RobotRuntimeEnvironment.RobotEnvironmentDetailedException;
import org.rf.ide.core.executor.RobotRuntimeEnvironment.RobotEnvironmentException;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

/**
 * @author Michal Anglart
 *
 */
class RobotCommandDirectExecutor implements RobotCommandExecutor {

    private static final TypeReference<Map<String, Object>> STRING_TO_OBJECT_MAPPING_TYPE =
            new TypeReference<Map<String, Object>>() { };

    private static final TypeReference<List<String>> STRING_LIST_TYPE =
            new TypeReference<List<String>>() { };

    private final String interpreterPath;

    private final SuiteExecutor interpreterType;

    RobotCommandDirectExecutor(final String interpreterPath, final SuiteExecutor interpreterType) {
        this.interpreterPath = interpreterPath;
        this.interpreterType = interpreterType;
    }

    @Override
    public Map<String, Object> getVariables(final String filePath, final List<String> fileArguments) {
        try {
            final String normalizedPath = filePath.replace('\\', '/');

            final File scriptFile = RobotRuntimeEnvironment.copyScriptFile("red_variables.py");
            final List<String> cmdLine = newArrayList(interpreterPath, scriptFile.getAbsolutePath(), "-variables",
                    normalizedPath);
            cmdLine.addAll(fileArguments);

            final StringBuilder jsonEncodedOutput = new StringBuilder();
            RobotRuntimeEnvironment.runExternalProcess(cmdLine, line -> jsonEncodedOutput.append(line));

            final String resultVars = jsonEncodedOutput.toString().trim();
            final Map<String, Object> variables = new ObjectMapper().readValue(resultVars,
                    STRING_TO_OBJECT_MAPPING_TYPE);
            return Maps.newLinkedHashMap(variables);
        } catch (final IOException e) {
            return Maps.newLinkedHashMap();
        }

    }

    @Override
    public Map<String, Object> getGlobalVariables() {
        try {
            final File scriptFile = RobotRuntimeEnvironment.copyScriptFile("red_variables.py");
            final List<String> cmdLine = newArrayList(interpreterPath, scriptFile.getAbsolutePath(), "-global");

            final StringBuilder jsonEncodedOutput = new StringBuilder();
            RobotRuntimeEnvironment.runExternalProcess(cmdLine, line -> jsonEncodedOutput.append(line));

            final String resultVars = jsonEncodedOutput.toString().trim();
            final Map<String, Object> variables = new ObjectMapper().readValue(resultVars,
                    STRING_TO_OBJECT_MAPPING_TYPE);
            return Maps.newLinkedHashMap(variables);
        } catch (final IOException e) {
            return Maps.newLinkedHashMap();
        }
    }

    @Override
    public List<String> getStandardLibrariesNames() {
        try {
            final File scriptFile = RobotRuntimeEnvironment.copyScriptFile("red_libraries.py");
            final List<String> cmdLine = newArrayList(interpreterPath, scriptFile.getAbsolutePath(), "-names");

            final List<String> stdLibs = new ArrayList<>();
            RobotRuntimeEnvironment.runExternalProcess(cmdLine, line -> stdLibs.add(line.trim()));

            return stdLibs;
        } catch (final IOException e) {
            return newArrayList();
        }
    }

    @Override
    public String getStandardLibraryPath(final String libraryName) {
        try {
            final File scriptFile = RobotRuntimeEnvironment.copyScriptFile("red_libraries.py");
            final List<String> cmdLine = newArrayList(interpreterPath, scriptFile.getAbsolutePath(), "-path",
                    libraryName);

            final StringBuilder path = new StringBuilder();
            RobotRuntimeEnvironment.runExternalProcess(cmdLine, line -> path.append(line));

            return path.toString().trim();
        } catch (final IOException e) {
            return null;
        }
    }

    @Override
    public String getRobotVersion() {
        try {
            final List<String> cmdLine = newArrayList(interpreterPath, "-m", "robot.run", "--version");

            final StringBuilder versionOutput = new StringBuilder();
            RobotRuntimeEnvironment.runExternalProcess(cmdLine, line -> versionOutput.append(line));

            final String version = versionOutput.toString();
            return version.startsWith("Robot Framework") ? version.trim() : null;
        } catch (final IOException e) {
            return null;
        }
    }

    @Override
    public void createLibdocForStdLibrary(final String resultFilePath, final String libName, final String libPath) {
        try {
            final File scriptFile = RobotRuntimeEnvironment.copyScriptFile("red_libraries.py");
            final List<String> cmdLine = newArrayList(interpreterPath, scriptFile.getAbsolutePath(), "-libdoc",
                    libName);

            final byte[] decodedFileContent = runLibdoc(libName, cmdLine);
            writeLibdocToFile(resultFilePath, decodedFileContent);
        } catch (final IOException e) {
            // simply libdoc will not be generated
        }
    }

    @Override
    public void createLibdocForThirdPartyLibrary(final String resultFilePath, final String libName,
            final String libPath,
            final EnvironmentSearchPaths additionalPaths) {
        final List<String> additions = newArrayList(libPath);
        additions.addAll(additionalPaths.getPythonPaths());
        additions.addAll(additionalPaths.getClassPaths());
        if (interpreterType == SuiteExecutor.Jython) {
            additions.addAll(RedSystemProperties.getPythonPaths());
        }
        
        try {
            final File scriptFile = RobotRuntimeEnvironment.copyScriptFile("red_libraries.py");
            final List<String> cmdLine = newArrayList(interpreterPath, scriptFile.getAbsolutePath(), "-libdoc",
                    libName);
            cmdLine.addAll(additions.stream().map(RobotRuntimeEnvironment::wrapArgumentIfNeeded).collect(toList()));

            final byte[] decodedFileContent = runLibdoc(libName, cmdLine);
            writeLibdocToFile(resultFilePath, decodedFileContent);
        } catch (final IOException e) {
            // simply libdoc will not be generated
        }
    }

    private byte[] runLibdoc(final String libName, final List<String> cmdLine) {
        try {
            final List<String> lines = newArrayList();
            RobotRuntimeEnvironment.runExternalProcess(cmdLine, line -> lines.add(line));

            // when properly finished there is a path to the file in first line and encoded content
            // in second
            if (lines.size() != 2) {
                throw new RobotEnvironmentDetailedException(Joiner.on('\n').join(lines),
                        "Unable to generate library specification file for library '" + libName + "'");
            } else {
                final String base64EncodedLibfileContent = lines.get(1);
                return Base64.getDecoder().decode(base64EncodedLibfileContent);
            }
        } catch (final IOException e) {
            throw new RobotEnvironmentDetailedException(e.getMessage(),
                    "Unable to generate library specification file for library '" + libName + "'", e);
        }
    }

    private void writeLibdocToFile(final String resultFilePath, final byte[] docededFileContent) throws IOException {
        final File libdocFile = new File(resultFilePath);
        if (!libdocFile.exists()) {
            libdocFile.createNewFile();
        }
        Files.write(docededFileContent, libdocFile);
    }

    @Override
    public List<File> getModulesSearchPaths() {
        try {
            final File scriptFile = RobotRuntimeEnvironment.copyScriptFile("red_modules.py");
            final List<String> cmdLine = newArrayList(interpreterPath, scriptFile.getAbsolutePath(), "-pythonpath");

            final StringBuilder jsonEncodedOutput = new StringBuilder();
            final int returnCode = RobotRuntimeEnvironment.runExternalProcess(cmdLine,
                    line -> jsonEncodedOutput.append(line));

            if (returnCode != 0) {
                throw new RobotEnvironmentException("Unable to obtain modules search paths");
            }
            final List<String> pathsFromJson = new ObjectMapper().readValue(jsonEncodedOutput.toString(),
                    STRING_LIST_TYPE);
            return pathsFromJson.stream()
                    .filter(input -> !"".equals(input) && !".".equals(input))
                    .map(path -> new File(path))
                    .collect(Collectors.<File> toList());
        } catch (final IOException e) {
            throw new RobotEnvironmentException("Unable to obtain modules search paths", e);
        }
    }

    @Override
    public Optional<File> getModulePath(final String moduleName, final EnvironmentSearchPaths additionalPaths) {
        try {
            final File scriptFile = RobotRuntimeEnvironment.copyScriptFile("red_modules.py");
            
            final List<String> cmdLine = newArrayList(interpreterPath);
            if (interpreterType == SuiteExecutor.Jython) {
                cmdLine.add("-J-cp");
                cmdLine.add(Joiner.on(RedSystemProperties.getPathsSeparator()).join(additionalPaths.getClassPaths()));
            }
            cmdLine.add(scriptFile.getAbsolutePath());
            cmdLine.add("-modulename");
            cmdLine.add(moduleName);
            cmdLine.add(RobotRuntimeEnvironment.wrapArgumentIfNeeded(Joiner.on(";").join(additionalPaths.getPythonPaths())));

            final List<String> lines = new ArrayList<>();
            RobotRuntimeEnvironment.runExternalProcess(cmdLine, line -> lines.add(line));
            if (lines.size() == 1) {
                // there should be a single line with path only
                return Optional.of(new File(lines.get(0).toString()));
            } else {
                final String indent = Strings.repeat(" ", 12);
                final String exception = indent + Joiner.on("\n" + indent).join(lines);
                throw new RobotEnvironmentException(
                        "RED python session problem. Following exception has been thrown by python service:\n"
                                + exception);
            }
        } catch (final IOException e) {
            throw new RobotEnvironmentException("Unable to find path of '" + moduleName + "' module", e);
        }
    }
    
    @Override
    public Boolean isVirtualenv() {
        try {
            final File scriptFile = RobotRuntimeEnvironment.copyScriptFile("red_virtualenv_check.py");
            final List<String> cmdLine = newArrayList(interpreterPath, scriptFile.getAbsolutePath());

            final StringBuilder result = new StringBuilder();
            RobotRuntimeEnvironment.runExternalProcess(cmdLine, line -> result.append(line));

            return Boolean.parseBoolean(result.toString().toLowerCase().trim());
        } catch (final IOException e) {
            return false;
        }
    }
}
