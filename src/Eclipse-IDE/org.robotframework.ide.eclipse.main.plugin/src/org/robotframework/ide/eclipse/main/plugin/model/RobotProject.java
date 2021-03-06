/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.model;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newLinkedHashMap;
import static com.google.common.collect.Sets.newLinkedHashSet;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import org.rf.ide.core.dryrun.RobotDryRunKeywordSource;
import org.rf.ide.core.executor.RobotRuntimeEnvironment;
import org.rf.ide.core.project.ImportSearchPaths.PathsProvider;
import org.rf.ide.core.project.RobotProjectConfig;
import org.rf.ide.core.project.RobotProjectConfig.LibraryType;
import org.rf.ide.core.project.RobotProjectConfig.ReferencedLibrary;
import org.rf.ide.core.project.RobotProjectConfig.ReferencedVariableFile;
import org.rf.ide.core.project.RobotProjectConfig.RemoteLocation;
import org.rf.ide.core.project.RobotProjectConfig.SearchPath;
import org.rf.ide.core.project.RobotProjectConfigReader.CannotReadProjectConfigurationException;
import org.rf.ide.core.testdata.RobotParser;
import org.rf.ide.core.testdata.model.RobotExpressions;
import org.rf.ide.core.testdata.model.RobotProjectHolder;
import org.robotframework.ide.eclipse.main.plugin.RedPlugin;
import org.robotframework.ide.eclipse.main.plugin.RedWorkspace;
import org.robotframework.ide.eclipse.main.plugin.project.LibrariesWatchHandler;
import org.robotframework.ide.eclipse.main.plugin.project.RedEclipseProjectConfig;
import org.robotframework.ide.eclipse.main.plugin.project.RedEclipseProjectConfig.PathResolvingException;
import org.robotframework.ide.eclipse.main.plugin.project.RedEclipseProjectConfigReader;
import org.robotframework.ide.eclipse.main.plugin.project.editor.RedProjectEditor;
import org.robotframework.ide.eclipse.main.plugin.project.editor.RedProjectEditorInput;
import org.robotframework.ide.eclipse.main.plugin.project.library.LibrarySpecification;
import org.robotframework.ide.eclipse.main.plugin.project.library.LibrarySpecificationReader;
import org.robotframework.ide.eclipse.main.plugin.project.library.LibrarySpecificationReader.CannotReadLibrarySpecificationException;
import org.robotframework.red.swt.SwtThread;
import org.robotframework.red.swt.SwtThread.Evaluation;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;

public class RobotProject extends RobotContainer {

    private RobotProjectHolder projectHolder;

    private Map<String, LibrarySpecification> stdLibsSpecs;
    private Map<ReferencedLibrary, LibrarySpecification> refLibsSpecs;
    private List<ReferencedVariableFile> referencedVariableFiles;

    private RobotProjectConfig configuration;

    private final LibrariesWatchHandler librariesWatchHandler;

    private final Map<String, RobotDryRunKeywordSource> kwSources = new ConcurrentHashMap<>();

    RobotProject(final RobotModel model, final IProject project) {
        super(model, project);
        librariesWatchHandler = new LibrariesWatchHandler(this);
    }

    public synchronized RobotProjectHolder getRobotProjectHolder() {
        if (projectHolder == null) {
            projectHolder = new RobotProjectHolder(getRuntimeEnvironment());
        }
        projectHolder.configure(getRobotProjectConfig(), getProject().getLocation().toFile());
        return projectHolder;
    }

    public RobotParser getEagerRobotParser() {
        return RobotParser.createEager(getRobotProjectHolder(), createPathsProvider());
    }

    public RobotParser getRobotParser() {
        return RobotParser.create(getRobotProjectHolder(), createPathsProvider());
    }

    public IProject getProject() {
        return (IProject) container;
    }

    public String getVersion() {
        readProjectConfigurationIfNeeded();
        final RobotRuntimeEnvironment env = getRuntimeEnvironment();
        return env == null ? "???" : env.getVersion();
    }

    public Collection<LibrarySpecification> getLibrariesSpecifications() {
        final List<LibrarySpecification> specifications = newArrayList();
        specifications.addAll(getStandardLibraries().values());
        specifications.addAll(getReferencedLibraries().values());
        return newArrayList(filter(specifications, Predicates.notNull()));
    }

    public synchronized boolean hasStandardLibraries() {
        readProjectConfigurationIfNeeded();
        if (stdLibsSpecs != null && !stdLibsSpecs.isEmpty()) {
            return true;
        }
        return configuration != null;
    }

    public synchronized Map<String, LibrarySpecification> getStandardLibraries() {
        if (stdLibsSpecs != null) {
            return stdLibsSpecs;
        }
        readProjectConfigurationIfNeeded();
        final RobotRuntimeEnvironment env = getRuntimeEnvironment();
        if (env == null || configuration == null) {
            return newLinkedHashMap();
        }
        stdLibsSpecs = newLinkedHashMap();
        for (final String stdLib : env.getStandardLibrariesNames()) {
            stdLibsSpecs.put(stdLib, stdLibToSpec(getProject()).apply(stdLib));
        }
        for (final RemoteLocation location : configuration.getRemoteLocations()) {
            stdLibsSpecs.put("Remote " + location.getUri(), remoteLibToSpec(getProject()).apply(location));
        }
        return stdLibsSpecs;
    }

    @VisibleForTesting
    public void setStandardLibraries(final Map<String, LibrarySpecification> libs) {
        stdLibsSpecs = libs;
    }

    public synchronized boolean hasReferencedLibraries() {
        readProjectConfigurationIfNeeded();
        if (refLibsSpecs != null && !refLibsSpecs.isEmpty()) {
            return true;
        }
        return configuration != null && configuration.hasReferencedLibraries();
    }

    public synchronized Map<ReferencedLibrary, LibrarySpecification> getReferencedLibraries() {
        if (refLibsSpecs != null) {
            return refLibsSpecs;
        }
        readProjectConfigurationIfNeeded();
        if (configuration == null) {
            return newLinkedHashMap();
        }

        refLibsSpecs = newLinkedHashMap();
        for (final ReferencedLibrary library : configuration.getLibraries()) {
            final LibrarySpecification spec = reflibToSpec(getProject()).apply(library);
            librariesWatchHandler.registerLibrary(library, spec);
            if(librariesWatchHandler.isLibSpecDirty(spec)) {
                spec.setIsModified(true);
            }
            refLibsSpecs.put(library, spec);
        }
        removeUnusedLibspecFiles(refLibsSpecs);

        return refLibsSpecs;
    }

    @VisibleForTesting
    public void setReferencedLibraries(final Map<ReferencedLibrary, LibrarySpecification> libs) {
        refLibsSpecs = libs;
    }

    public synchronized void unregisterWatchingOnReferencedLibraries(final List<ReferencedLibrary> libraries) {
        librariesWatchHandler.unregisterLibraries(libraries);
    }

    public void clearDirtyLibSpecs(final Collection<LibrarySpecification> libSpecs) {
        librariesWatchHandler.removeDirtySpecs(libSpecs);
    }

    private static Function<String, LibrarySpecification> stdLibToSpec(final IProject project) {
        return new Function<String, LibrarySpecification>() {

            @Override
            public LibrarySpecification apply(final String libraryName) {
                try {
                    final IFile file = LibspecsFolder.get(project).getSpecFile(libraryName);
                    return LibrarySpecificationReader.readStandardLibrarySpecification(file, libraryName);
                } catch (final CannotReadLibrarySpecificationException e) {
                    return null;
                }
            }
        };
    }

    private static Function<RemoteLocation, LibrarySpecification> remoteLibToSpec(final IProject project) {
        return new Function<RemoteLocation, LibrarySpecification>() {

            @Override
            public LibrarySpecification apply(final RemoteLocation remoteLocation) {
                try {
                    final IFile file = LibspecsFolder.get(project).getSpecFile(remoteLocation.createLibspecFileName());
                    return LibrarySpecificationReader.readRemoteSpecification(file, remoteLocation);
                } catch (final CannotReadLibrarySpecificationException e) {
                    return null;
                }
            }
        };
    }

    private static Function<ReferencedLibrary, LibrarySpecification> reflibToSpec(final IProject project) {
        return new Function<ReferencedLibrary, LibrarySpecification>() {

            @Override
            public LibrarySpecification apply(final ReferencedLibrary lib) {
                try {
                    final IPath path = Path.fromPortableString(lib.getPath());
                    final IResource libspec = project.getParent().findMember(path);

                    final IFile fileToRead;
                    if (lib.provideType() == LibraryType.VIRTUAL && libspec != null && libspec.exists()) {
                        fileToRead = (IFile) libspec;
                    } else {
                        fileToRead = LibspecsFolder.get(project).getSpecFile(lib.getName());
                    }
                    return LibrarySpecificationReader.readReferencedSpecification(fileToRead, lib);
                } catch (final CannotReadLibrarySpecificationException e) {
                    return null;
                }
            }
        };
    }

    private synchronized RobotProjectConfig readProjectConfigurationIfNeeded() {
        if (configuration == null) {
            try {
                configuration = new RedEclipseProjectConfigReader().readConfiguration(getProject());
            } catch (final CannotReadProjectConfigurationException e) {
                // oh well...
            }
        }
        return configuration;
    }

    public synchronized RobotProjectConfig getRobotProjectConfig() {
        readProjectConfigurationIfNeeded();
        return configuration;
    }

    /**
     * Returns the configuration model from opened editor.
     *
     * @return opened configuration
     */
    public RobotProjectConfig getOpenedProjectConfig() {
        final RedProjectEditorInput redProjectInput = findEditorInputIfAlreadyOpened();
        if (redProjectInput != null) {
            return redProjectInput.getProjectConfiguration();
        } else {
            return null;
        }
    }

    private RedProjectEditorInput findEditorInputIfAlreadyOpened() {
        return SwtThread.syncEval(new Evaluation<RedProjectEditorInput>() {
            @Override
            public RedProjectEditorInput runCalculation() {
                final IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if (window == null) {
                    // in the meantime window could be destroyed actually..
                    return null;
                }
                final IWorkbenchPage page = window.getActivePage();
                final FileEditorInput input = new FileEditorInput(getConfigurationFile());
                final IEditorPart editor = page.findEditor(input);
                return editor instanceof RedProjectEditor ? ((RedProjectEditor) editor).getRedProjectEditorInput()
                        : null;
            }
        });
    }

    public void clearCachedData() {
        if (projectHolder != null) {
            projectHolder.clearModelFiles();
        }
    }

    /**
     * Clearing should be done when user changed his/hers execution environment (python+robot)
     */
    public synchronized void clearAll() {
        projectHolder = null;
        clearConfiguration();
        clearKwSources();
    }

    public synchronized void clearConfiguration() {
        configuration = null;
        referencedVariableFiles = null;
        stdLibsSpecs = null;
        refLibsSpecs = null;
    }

    public synchronized void clearKwSources() {
        kwSources.clear();
    }

    public synchronized RobotRuntimeEnvironment getRuntimeEnvironment() {
        readProjectConfigurationIfNeeded();
        if (configuration == null || configuration.usesPreferences()) {
            return RedPlugin.getDefault().getActiveRobotInstallation();
        }
        return RedPlugin.getDefault().getRobotInstallation(configuration.providePythonLocation(),
                configuration.providePythonInterpreter());
    }

    public IFile getConfigurationFile() {
        return getProject().getFile(RobotProjectConfig.FILENAME);
    }

    public IFile getFile(final String filename) {
        return getProject().getFile(filename);
    }

    public PathsProvider createPathsProvider() {
        return new ProjectPathsProvider();
    }

    public void setModuleSearchPaths(final List<File> paths) {
        getRobotProjectHolder().setModuleSearchPaths(paths);
    }

    public synchronized List<File> getModuleSearchPaths() {
        return getRobotProjectHolder().getModuleSearchPaths();
    }

    public synchronized List<String> getPythonpath() {
        readProjectConfigurationIfNeeded();
        if (configuration != null) {
            final Set<String> pp = newLinkedHashSet();
            for (final ReferencedLibrary lib : configuration.getLibraries()) {
                if (lib.provideType() == LibraryType.PYTHON) {
                    final String path = RedWorkspace.Paths.toAbsoluteFromWorkspaceRelativeIfPossible(
                            new Path(lib.getPath())).toOSString();
                    pp.add(path);
                }
            }
            final RedEclipseProjectConfig redConfig = new RedEclipseProjectConfig(configuration);
            for (final SearchPath searchPath : configuration.getPythonPath()) {
                try {
                    final String path = redConfig.toAbsolutePath(searchPath, getProject()).getPath();
                    pp.add(path);
                } catch (final PathResolvingException e) {
                    // we don't want to add syntax-problematic paths
                }
            }
            return newArrayList(pp);
        }
        return newArrayList();
    }

    public synchronized List<String> getClasspath() {
        readProjectConfigurationIfNeeded();
        if (configuration != null) {
            final Set<String> cp = newLinkedHashSet();
            cp.add(".");
            for (final ReferencedLibrary lib : configuration.getLibraries()) {
                if (lib.provideType() == LibraryType.JAVA) {
                    final IPath absPath = RedWorkspace.Paths
                            .toAbsoluteFromWorkspaceRelativeIfPossible(new Path(lib.getPath()));
                    cp.add(absPath.toOSString());
                }
            }
            for (final SearchPath searchPath : configuration.getClassPath()) {
                try {
                    final String path = new RedEclipseProjectConfig(configuration)
                            .toAbsolutePath(searchPath, getProject()).getPath();
                    cp.add(path);
                } catch (final PathResolvingException e) {
                    // we don't want to add syntax-problematic paths
                }
            }
            return newArrayList(cp);
        }
        return newArrayList(".");
    }

    public synchronized boolean isStandardLibrary(final LibrarySpecification spec) {
        final Map<String, LibrarySpecification> stdLibs = getStandardLibraries();
        return isLibraryFrom(spec, stdLibs == null ? null : stdLibs.values());
    }

    public synchronized boolean isReferencedLibrary(final LibrarySpecification spec) {
        final Map<ReferencedLibrary, LibrarySpecification> refLibs = getReferencedLibraries();
        return isLibraryFrom(spec, refLibs == null ? null : refLibs.values());
    }

    private boolean isLibraryFrom(final LibrarySpecification spec, final Collection<LibrarySpecification> libs) {
        if (libs == null) {
            return false;
        }
        for (final LibrarySpecification librarySpecification : libs) {
            if (librarySpecification == spec) {
                return true;
            }
        }
        return false;
    }

    public synchronized String getPythonLibraryPath(final String libName) {
        readProjectConfigurationIfNeeded();
        if (configuration != null) {
            for (final ReferencedLibrary lib : configuration.getLibraries()) {
                if (lib.provideType() == LibraryType.PYTHON && lib.getName().equals(libName)) {
                    return RedWorkspace.Paths.toAbsoluteFromWorkspaceRelativeIfPossible(
                            new Path(lib.getPath()).append(lib.getName() + ".py")).toPortableString();
                }
            }
        }
        return "";
    }

    public List<String> getVariableFilePaths() {
        readProjectConfigurationIfNeeded();

        final List<String> list = new ArrayList<>();
        if (configuration != null) {
            for (final ReferencedVariableFile variableFile : configuration.getReferencedVariableFiles()) {
                final String path = RedWorkspace.Paths
                        .toAbsoluteFromWorkspaceRelativeIfPossible(new Path(variableFile.getPath())).toOSString();
                final List<String> args = variableFile.getArguments();
                final String arguments = args == null || args.isEmpty() ? "" : ":" + Joiner.on(":").join(args);
                list.add(path + arguments);
            }
        }
        return list;
    }

    @VisibleForTesting
    public void setReferencedVariablesFiles(final List<ReferencedVariableFile> varFiles) {
        this.referencedVariableFiles = varFiles;
    }

    public synchronized List<ReferencedVariableFile> getVariablesFromReferencedFiles() {
        if(referencedVariableFiles != null) {
            return referencedVariableFiles;
        }
        readProjectConfigurationIfNeeded();
        if (configuration != null) {
            referencedVariableFiles = newArrayList();
            for (final ReferencedVariableFile variableFile : configuration.getReferencedVariableFiles()) {
                IPath path = new Path(variableFile.getPath());
                if (!path.isAbsolute()) {
                    final IResource targetFile = getProject().getWorkspace().getRoot().findMember(path);
                    if (targetFile != null && targetFile.exists()) {
                        path = targetFile.getLocation();
                    }
                }

                final Map<String, Object> varsMap = getRuntimeEnvironment()
                        .getVariablesFromFile(path.toPortableString(), variableFile.getArguments());
                if (varsMap != null && !varsMap.isEmpty()) {
                    variableFile.setVariables(varsMap);
                    referencedVariableFiles.add(variableFile);
                }
            }
            return referencedVariableFiles;
        }
        return newArrayList();
    }

    public String resolve(final String expression) {
        return RobotExpressions.resolve(getRobotProjectHolder().getVariableMappings(), expression);
    }

    private void removeUnusedLibspecFiles(final Map<ReferencedLibrary, LibrarySpecification> refLibsSpecs) {
        if(!librariesWatchHandler.getRemovedSpecs().isEmpty()) {
            for (final LibrarySpecification removedSpec : librariesWatchHandler.getRemovedSpecs()) {
                if(!refLibsSpecs.containsValue(removedSpec)) {
                    final IFile libspecFile = removedSpec.getSourceFile();
                    if (libspecFile != null) {
                        final IPath libspecFileLocation = libspecFile.getLocation();
                        if (libspecFileLocation != null) {
                            libspecFileLocation.toFile().delete();
                        }
                    }
                }
            }
            librariesWatchHandler.clearRemovedSpecs();
            getRuntimeEnvironment().resetCommandExecutors();    //needed when user will add a library again after removal
        }
    }

    public void addKeywordSource(final RobotDryRunKeywordSource keywordSource) {
        final String qualifiedKwName = keywordSource.getLibraryName() + "." + keywordSource.getName();
        kwSources.put(qualifiedKwName, keywordSource);
    }

    public synchronized Optional<RobotDryRunKeywordSource> getKeywordSource(final String qualifiedKwName) {
        return Optional.ofNullable(kwSources.get(qualifiedKwName));
    }

    private class ProjectPathsProvider implements PathsProvider {

        @Override
        public List<File> providePythonModulesSearchPaths() {
            return getModuleSearchPaths();
        }

        @Override
        public List<File> provideUserSearchPaths() {
            final RobotProjectConfig configuration = getRobotProjectConfig();
            if (configuration == null) {
                return new ArrayList<>();
            }
            final List<File> paths = new ArrayList<>();
            final RedEclipseProjectConfig redConfig = new RedEclipseProjectConfig(configuration);
            for (final SearchPath searchPath : configuration.getPythonPath()) {
                try {
                    final File searchPathParent = redConfig.toAbsolutePath(searchPath, getProject());
                    paths.add(searchPathParent);
                } catch (final PathResolvingException e) {
                    continue;
                }
            }
            return paths;
        }
    }
}
