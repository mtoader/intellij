/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.buildmodifier;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.CharStreams;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.google.idea.common.formatter.FormatUtils.FileContentsProvider;
import com.google.idea.common.formatter.FormatUtils.Replacements;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * Formats BUILD files using 'buildifier'
 */
public class BuildFileFormatter {

  private static final Logger logger = Logger.getInstance(BuildFileFormatter.class);

  @Nullable
  private static ProcessBuilder getBuildifierProcessBuilder(Project project, BlazeFileType blazeFileType) {
    ProcessBuilder processBuilder = new ProcessBuilder();

    for (BuildifierBinaryProvider provider : BuildifierBinaryProvider.EP_NAME.getExtensions()) {
      File file = provider.getBuildifierBinary(project);
      if (file != null) {
        processBuilder.command(file.getAbsolutePath(), fileTypeArg(blazeFileType));
        processBuilder.directory(provider.getBuildifierExecutionRoot(project));
        return processBuilder;
      }
    }

    return null;
  }

  /**
   * Calls buildifier for a given text and list of line ranges, and returns the formatted text, or
   * null if the formatting failed.
   */
  @Nullable
  public static Replacements getReplacements(
      BlazeFileType fileType, FileContentsProvider fileContents, Collection<TextRange> ranges) {
    ProcessBuilder processBuilder = getBuildifierProcessBuilder(fileContents.getProject(), fileType);
    if (processBuilder == null) {
      return null;
    }
    String text = fileContents.getFileContentsIfUnchanged();
    if (text == null) {
      return null;
    }
    Replacements output = new Replacements();
    try {
      for (TextRange range : ranges) {
        String input = range.substring(text);
        String result = formatText(processBuilder, input);
        if (result == null) {
          return null;
        }
        output.addReplacement(range, input, result);
      }
      return output;

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException e) {
      logger.warn(e);
    }
    return null;
  }

  /**
   * Passes the input text to buildifier, returning the formatted output text, or null if formatting
   * failed.
   */
  @Nullable
  private static String formatText(ProcessBuilder processBuilder, String inputText)
  throws InterruptedException, IOException {
    Process process = processBuilder.start();
    process.getOutputStream().write(inputText.getBytes(UTF_8));
    process.getOutputStream().close();

    BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8));
    BufferedReader stdError =
        new BufferedReader(new InputStreamReader(process.getErrorStream(), UTF_8));
    String formattedText = CharStreams.toString(reader);
    String formatterError = CharStreams.toString(stdError);
    process.waitFor();
    return process.exitValue() != 0 ? null : formattedText;
  }

  /**
   * Passes the input text to buildifier, returning the formatted output text, or null if formatting
   * failed.
   */
  @Nullable
  static public String formatText(Project project, BlazeFileType fileType, String inputText)
  throws InterruptedException, IOException {
    ProcessBuilder processBuilder = getBuildifierProcessBuilder(project, fileType);
    return processBuilder != null ? formatText(processBuilder, inputText) : null;
  }

  private static String fileTypeArg(BlazeFileType fileType) {
    return fileType == BlazeFileType.SkylarkExtension ? "--type=bzl" : "--type=build";
  }
}
