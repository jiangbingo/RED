/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.project;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.robotframework.ide.eclipse.main.plugin.model.RobotProject;

/**
 * @author mmarzec
 */
public class LibrariesSourcesCollector {

    private RobotProject robotProject;

    private Set<String> pythonpathLocations = new HashSet<>();

    private Set<String> classpathLocations = new HashSet<>();

    public LibrariesSourcesCollector(final RobotProject robotProject) {
        this.robotProject = robotProject;
    }

    public void collectPythonAndJavaLibrariesSources(final boolean shouldCollectRecursively) throws CoreException {

        if (shouldCollectRecursively) {
            collectLocationsWithPythonAndJavaMembersRecursively(robotProject.getProject().members());
        } else {
            collectOnlyParentLocationsWithPythonAndJavaMembers(robotProject.getProject().members());
        }

        final IPath projectLocation = robotProject.getProject().getLocation();
        if(projectLocation != null) {
            pythonpathLocations.add(projectLocation.toOSString());
        }
        pythonpathLocations.addAll(robotProject.getPythonpath());
        
        classpathLocations.addAll(robotProject.getClasspath());
    }

    private void collectLocationsWithPythonAndJavaMembersRecursively(final IResource[] members) throws CoreException {
        if (members != null) {
            for (int i = 0; i < members.length; i++) {
                final IResource resource = members[i];
                if (resource.getType() == IResource.FILE) {
                    checkFileExtensionAndAddToProperLocations(resource);
                } else if (resource.getType() == IResource.FOLDER) {
                    collectLocationsWithPythonAndJavaMembersRecursively(((IFolder) resource).members());
                }
            }
        }
    }

    private void collectOnlyParentLocationsWithPythonAndJavaMembers(final IResource[] members) throws CoreException {
        if (members != null) {
            for (int i = 0; i < members.length; i++) {
                final IResource resource = members[i];
                if (resource.getType() == IResource.FOLDER) {
                    final IResource[] folderMembers = ((IFolder) resource).members();
                    for (int j = 0; j < folderMembers.length; j++) {
                        final IResource folderMember = folderMembers[j];
                        if (folderMember.getType() == IResource.FILE) {
                            checkFileExtensionAndAddToProperLocations(folderMember);
                        }
                    }
                }
            }
        }
    }
    
    public void checkFileExtensionAndAddToProperLocations(final IResource resource) {
        final String fileExtension = resource.getFileExtension();
        if (fileExtension != null) {
            if (isPythonMember(fileExtension)) {
                addPythonPathLocation(resource);
            } else if (isJavaMember(fileExtension)) {
                addClassPathLocation(resource);
            }
        }
    }

    private boolean isPythonMember(final String fileExtension) {
        return fileExtension.equals("py");
    }

    private boolean isJavaMember(final String fileExtension) {
        return fileExtension.equals("jar");
    }

    private void addPythonPathLocation(final IResource resource) {
        final IPath fileLocation = resource.getLocation();
        if (fileLocation != null) {
            pythonpathLocations.add(fileLocation.toFile().getParent());
        }
    }

    private void addClassPathLocation(final IResource resource) {
        final IPath fileLocation = resource.getLocation();
        if (fileLocation != null) {
            classpathLocations.add(fileLocation.toOSString());
        }
    }

    public Set<String> getPythonpathLocations() {
        return pythonpathLocations;
    }

    public Set<String> getClasspathLocations() {
        return classpathLocations;
    }

}
