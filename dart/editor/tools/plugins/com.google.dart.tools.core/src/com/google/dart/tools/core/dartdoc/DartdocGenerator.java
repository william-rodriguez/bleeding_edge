/*
 * Copyright 2012 Dart project authors.
 *
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.dart.tools.core.dartdoc;

import com.google.dart.tools.core.DartCore;
import com.google.dart.tools.core.MessageConsole;
import com.google.dart.tools.core.dart2js.Dart2JSCompiler;
import com.google.dart.tools.core.dart2js.ProcessRunner;
import com.google.dart.tools.core.model.DartLibrary;
import com.google.dart.tools.core.model.DartSdkManager;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Launch the dartdoc process and collect stdout, stderr, and exit code information.
 *
 * @see Dart2JSCompiler
 */
public class DartdocGenerator {

  public static class CompilationResult {
    private ProcessRunner runner;
    private IPath outputPath;

    CompilationResult(ProcessRunner runner, IPath outputPath) {
      this.runner = runner;
      this.outputPath = outputPath;
    }

    public String getAllOutput() {
      StringBuilder builder = new StringBuilder();

      if (!getStdOut().isEmpty()) {
        builder.append(getStdOut().trim() + "\n");
      }

      if (!getStdErr().isEmpty()) {
        builder.append(getStdErr());
      }

      return builder.toString().trim();
    }

    public int getExitCode() {
      return runner.getExitCode();
    }

    public IPath getOutputPath() {
      return outputPath;
    }

    public String getStdErr() {
      return runner.getStdErr();
    }

    public String getStdOut() {
      return runner.getStdOut();
    }

    @Override
    public String toString() {
      return "dartdoc result=" + getExitCode();
    }
  }

  /**
   * A static utility method to handle the common use case for calling the dartdoc script. Compile
   * the given dart library, optionally poll the given monitor to check for user cancellation, and
   * write any output to the given console.
   *
   * @param library
   * @param monitor
   * @param console
   * @throws OperationCanceledException
   */
  public static CompilationResult generateDartdoc(DartLibrary library, IProgressMonitor monitor,
      final MessageConsole console) throws CoreException {
    long startTime = System.currentTimeMillis();

    final IPath inputPath = library.getCorrespondingResource().getLocation();
    final IPath outputPath = getDocsPath(inputPath);
    final IPath indexPath = getDocsIndexPath(inputPath);

    DartdocGenerator generator = new DartdocGenerator();

    console.clear();
    console.println("Running dartdoc...");

    try {
      CompilationResult result = generator.dartdoc(inputPath, outputPath, monitor);

      refreshResources(library.getCorrespondingResource());

      displayCompilationResult(generator, result, indexPath, startTime, console);
      return result;
    } catch (IOException ioe) {
      throw new CoreException(new Status(IStatus.ERROR, DartCore.PLUGIN_ID, ioe.toString(), ioe));
    }
  }

  /**
   * Answer the Dartdoc <code>index.html</code> path for the specified source.
   *
   * @param source the application source file (not <code>null</code>)
   * @return the application file path (may not exist)
   */
  public static IPath getDocsIndexPath(IPath libraryPath) {
    return getDocsPath(libraryPath).append("index.html");
  }

  /**
   * Answer the Dartdoc directory path for the specified source.
   *
   * @param source the application source file (not <code>null</code>)
   * @return the application file path (may not exist)
   */
  public static IPath getDocsPath(IPath libraryPath) {
    return libraryPath.removeLastSegments(1).append("docs");
  }

  private static void displayCompilationResult(DartdocGenerator compiler, CompilationResult result,
      IPath indexPath, long startTime, MessageConsole console) {
    StringBuilder builder = new StringBuilder();

    if (!result.getStdOut().isEmpty()) {
      builder.append(result.getStdOut().trim() + "\n");
    }

    if (!result.getStdErr().isEmpty()) {
      builder.append(result.getStdErr().trim() + "\n");
    }

    if (result.getExitCode() == 0) {
      long elapsed = System.currentTimeMillis() - startTime;

      // Trim to 1/10th of a second.
      elapsed = (elapsed / 100) * 100;

      File outputFile = indexPath.toFile();

      String message = "Generated dartdoc in " + (elapsed / 1000.0) + " seconds";

      builder.append(NLS.bind("Wrote {0} [{1}]\n", outputFile.getPath(), message));
    }

    console.print(builder.toString());
  }

  /**
   * Dartdoc creates java.io.Files; we need to tell the workspace about the new / changed resources.
   *
   * @param correspondingResource
   * @throws CoreException
   */
  private static void refreshResources(IResource resource) throws CoreException {
    IContainer container;
    if (resource == null) {
      return;
    }

    if (resource instanceof IContainer) {
      container = (IContainer) resource;
    } else {
      container = resource.getParent();
    }

    container.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
  }

  /**
   * Create a new DartdocGenerator.
   */
  public DartdocGenerator() {
  }

  /**
   * Run dartdoc as a process to compile the given input file to the given output file. If an
   * IProgressMonitor is passed in, it is polled to see if the user cancelled the compile operation.
   * The progress monitor is not used for any other purpose.
   *
   * @param inputPath
   * @param outputPath
   * @param monitor
   * @return
   * @throws IOException
   * @throws OperationCanceledException if the user cancelled the operation
   */
  public CompilationResult dartdoc(IPath inputPath, IPath outputPath, IProgressMonitor monitor)
      throws IOException {
    ProcessBuilder builder = new ProcessBuilder();

    List<String> args = new ArrayList<String>();

    args.add(DartSdkManager.getManager().getSdk().getVmExecutable().getPath());
    args.add("--new_gen_heap_size=256");
    args.addAll(getCompilerArguments(inputPath, outputPath));

    builder.command(args);
    builder.directory(DartSdkManager.getManager().getSdk().getPackageDirectory());
    builder.redirectErrorStream(true);

    ProcessRunner runner = new ProcessRunner(builder);

    runner.runSync(monitor);

    return new CompilationResult(runner, outputPath);
  }

  public String getName() {
    return "dartdoc";
  }

  public boolean isAvailable() {
    return DartSdkManager.getManager().hasSdk();
  }

  private List<String> getCompilerArguments(IPath inputPath, IPath outputPath) {
    List<String> args = new ArrayList<String>();

    args.add("dartdoc/bin/dartdoc.dart");

    args.add("--mode=static");
    args.add("--link-api");
    args.add("--verbose");

    args.add("--out=" + outputPath.toOSString());
    args.add(inputPath.toOSString());

    return args;
  }

}
