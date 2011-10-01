/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.testfixtures;

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.initialization.DefaultProjectDescriptorRegistry;
import org.gradle.invocation.DefaultGradle;
import org.gradle.testfixtures.internal.GlobalTestServices;
import org.gradle.testfixtures.internal.TestTopLevelBuildServiceRegistry;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;

/**
 * <p>Creates dummy instances of {@link org.gradle.api.Project} which you can use in testing custom task and plugin
 * implementations.</p>
 *
 * <p>To create a project instance:</p>
 *
 * <ol>
 *
 * <li>Create a {@code ProjectBuilder} instance by calling {@link #builder()}.</li>
 *
 * <li>Optionally, configure the builder.</li>
 *
 * <li>Call {@link #build()} to create the {@code Project} instance.</li>
 *
 * </ol>
 *
 * <p>You can reuse a builder to create multiple {@code Project} instances.</p>
 */
public class ProjectBuilder {
    private static final GlobalTestServices GLOBAL_SERVICES = new GlobalTestServices();
    private static final AsmBackedClassGenerator CLASS_GENERATOR = new AsmBackedClassGenerator();
    private File projectDir;
    private String name = "test";
    private Project parent;

    /**
     * Creates a project builder.
     *
     * @return The builder
     */
    public static ProjectBuilder builder() {
        return new ProjectBuilder();
    }

    /**
     * Specifies the project directory for the project to build.
     *
     * @param dir The project directory
     * @return The builder
     */
    public ProjectBuilder withProjectDir(File dir) {
        projectDir = dir;
        return this;
    }

    /**
     * Specifies the name for the project
     *
     * @param name project name
     * @return The builder
     */
    public ProjectBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Specifies the parent project. Use it to create multi-module projects.
     *
     * @param parent parent project
     * @return The builder
     */
    public ProjectBuilder withParent(Project parent) {
        this.parent = parent;
        return this;
    }

    /**
     * Creates the project.
     *
     * @return The project
     */
    public Project build() {
        if (parent != null) {
            return createChildProject();
        }
        return createProject();
    }

    private Project createChildProject() {
        ProjectInternal parentProject = (ProjectInternal) parent;
        DefaultProject project = CLASS_GENERATOR.newInstance(
                DefaultProject.class,
                name,
                parentProject,
                (projectDir != null) ? projectDir.getAbsoluteFile() : new File(parentProject.getProjectDir(), name),
                new StringScriptSource("test build file", null),
                parentProject.getGradle(),
                parentProject.getGradle().getServices()
        );
        parentProject.addChildProject(project);
        parentProject.getProjectRegistry().addProject(project);
        return project;
    }

    private Project createProject() {
        prepareProjectDir();

        final File homeDir = new File(projectDir, "gradleHome");

        StartParameter startParameter = new StartParameter();
        startParameter.setGradleUserHomeDir(new File(projectDir, "userHome"));

        ServiceRegistryFactory topLevelRegistry = new TestTopLevelBuildServiceRegistry(GLOBAL_SERVICES, startParameter, homeDir);
        GradleInternal gradle = new DefaultGradle(null, startParameter, topLevelRegistry);

        DefaultProjectDescriptor projectDescriptor = new DefaultProjectDescriptor(null, name, projectDir, new DefaultProjectDescriptorRegistry());
        ProjectInternal project = topLevelRegistry.get(IProjectFactory.class).createProject(projectDescriptor, null, gradle);

        gradle.setRootProject(project);
        gradle.setDefaultProject(project);

        return project;
    }

    private void prepareProjectDir() {
        if (projectDir == null) {
            try {
                projectDir = GFileUtils.canonicalise(File.createTempFile("gradle", "projectDir"));
                projectDir.delete();
                projectDir.mkdir();
                projectDir.deleteOnExit();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            projectDir = GFileUtils.canonicalise(projectDir);
        }
    }
}
