/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync.deps;

import static java.util.function.Predicate.not;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.java.JavaArtifactInfo;
import java.util.Optional;

/**
 * Updates the project proto with the android resources packages extracted by the aspect in a
 * dependencies build.
 */
public class AddAndroidResPackages implements ProjectProtoUpdateOperation {

  private final BuiltDependenciesSupplier builtDepsSupplier;

  public AddAndroidResPackages(BuiltDependenciesSupplier builtDepsSupplier) {
    this.builtDepsSupplier = builtDepsSupplier;
  }

  @Override
  public void update(ProjectProtoUpdate update) throws BuildException {
    update
        .workspaceModule()
        .addAllAndroidSourcePackages(
            builtDepsSupplier.get().stream()
                .map(TargetBuildInfo::javaInfo)
                .flatMap(Optional::stream)
                .map(JavaArtifactInfo::androidResourcesPackage)
                .filter(not(Strings::isNullOrEmpty))
                .distinct()
                .collect(ImmutableList.toImmutableList()));
  }
}