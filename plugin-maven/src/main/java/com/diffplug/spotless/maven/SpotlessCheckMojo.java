/*
 * Copyright 2016-2022 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.spotless.maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.diffplug.spotless.DirtyState;
import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.Lint;
import com.diffplug.spotless.extra.integration.DiffMessageFormatter;
import com.diffplug.spotless.maven.incremental.UpToDateChecker;

/**
 * Performs code formatting analysis and prints all violations to the console.
 * Fails the build if violations are discovered.
 */
@Mojo(name = AbstractSpotlessMojo.GOAL_CHECK, defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class SpotlessCheckMojo extends AbstractSpotlessMojo {

	@Override
	protected void process(Iterable<File> files, Formatter formatter, UpToDateChecker upToDateChecker) throws MojoExecutionException {
		List<File> problemFiles = new ArrayList<>();
		TreeMap<File, List<Lint>> allLints = new TreeMap<>();
		for (File file : files) {
			if (upToDateChecker.isUpToDate(file.toPath())) {
				if (getLog().isDebugEnabled()) {
					getLog().debug("Spotless will not check an up-to-date file: " + file);
				}
				continue;
			}

			try {
				DirtyState.Calculation calculation = DirtyState.of(formatter, file);
				DirtyState dirtyState = calculation.calculateDirtyState();
				List<Lint> lints = calculation.calculateLintAgainstDirtyState(dirtyState);

				if (!lints.isEmpty()) {
					allLints.put(file, lints);
				}
				if (!dirtyState.isClean()) {
					problemFiles.add(file);
				}
				if (lints.isEmpty() && dirtyState.isClean()) {
					upToDateChecker.setUpToDate(file.toPath());
				}
			} catch (IOException e) {
				throw new MojoExecutionException("Unable to format file " + file, e);
			}
		}

		if (!problemFiles.isEmpty()) {
			throw new MojoExecutionException(DiffMessageFormatter.builder()
					.runToFix("Run 'mvn spotless:apply' to fix these violations.")
					.formatter(baseDir.toPath(), formatter)
					.problemFiles(problemFiles)
					.getMessage());
		}
		if (!allLints.isEmpty()) {
			allLints.forEach((file, lints) -> {
				for (Lint lint : lints) {
					System.err.println(file.getAbsolutePath() + ":" + lint.toString());
				}
			});
			throw new MojoExecutionException("'mvn spotless:apply' cannot fix these violations.");
		}
	}
}
