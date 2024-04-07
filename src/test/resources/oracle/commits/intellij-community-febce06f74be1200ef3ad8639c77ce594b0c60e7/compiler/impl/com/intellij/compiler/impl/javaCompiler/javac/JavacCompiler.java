package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerIOUtil;
import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.ExternalCompiler;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.projectRoots.impl.MockJdkWrapper;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.rt.compiler.JavacRunner;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

public class JavacCompiler extends ExternalCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.javac.JavacCompiler");
  private final Project myProject;
  private final List<File> myTempFiles = new ArrayList<File>();
  @NonNls private static final String JAVAC_MAIN_CLASS_OLD = "sun.tools.javac.Main";
  @NonNls public static final String JAVAC_MAIN_CLASS = "com.sun.tools.javac.Main";

  public JavacCompiler(Project project) {
    myProject = project;
  }

  public boolean checkCompiler(final CompileScope scope) {
    final Module[] modules = scope.getAffectedModules();
    final Set<Sdk> checkedJdks = new HashSet<Sdk>();
    for (final Module module : modules) {
      final Sdk jdk  = ModuleRootManager.getInstance(module).getSdk();
      if (jdk == null) {
        continue;
      }
      if (checkedJdks.contains(jdk)) {
        continue;
      }
      final VirtualFile homeDirectory = jdk.getHomeDirectory();
      if (homeDirectory == null) {
        Messages.showMessageDialog(myProject, CompilerBundle.jdkHomeNotFoundMessage(jdk),
                                   CompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
        return false;
      }
      final SdkType sdkType = jdk.getSdkType();

      if (sdkType instanceof JavaSdkType){
        final String vmExecutablePath = ((JavaSdkType)sdkType).getVMExecutablePath(jdk);
        if (vmExecutablePath == null) {
          Messages.showMessageDialog(myProject,
                                     CompilerBundle.message("javac.error.vm.executable.missing", jdk.getName()),
                                     CompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
          return false;
        }
        final String toolsJarPath = ((JavaSdkType)sdkType).getToolsPath(jdk);
        if (toolsJarPath == null) {
          Messages.showMessageDialog(myProject,
                                     CompilerBundle.message("javac.error.tools.jar.missing", jdk.getName()), CompilerBundle.message("compiler.javac.name"),
                                     Messages.getErrorIcon());
          return false;
        }
        final String versionString = jdk.getVersionString();
        if (versionString == null) {
          Messages.showMessageDialog(myProject, CompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()),
                                     CompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
          return false;
        }

        if (CompilerUtil.isOfVersion(versionString, "1.0")) {
          Messages.showMessageDialog(myProject, CompilerBundle.message("javac.error.1_0_compilation.not.supported"), CompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
          return false;
        }
      }
      checkedJdks.add(jdk);
    }

    return true;
  }

  @NotNull
  @NonNls
  public String getId() // used for externalization
  {
    return "Javac";
  }

  @NotNull
  public String getPresentableName() {
    return CompilerBundle.message("compiler.javac.name");
  }

  @NotNull
  public Configurable createConfigurable() {
    return new JavacConfigurable(JavacSettings.getInstance(myProject));
  }

  public OutputParser createErrorParser(final String outputDir) {
    return new JavacOutputParser(myProject);
  }

  public OutputParser createOutputParser(final String outputDir) {
    return null;
  }

  @NotNull
  public String[] createStartupCommand(final ModuleChunk chunk, final CompileContext context, final String outputPath)
    throws IOException, IllegalArgumentException {

    final ArrayList<String> commandLine = new ArrayList<String>();

    final Exception[] ex = new Exception[]{null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          createStartupCommand(chunk, commandLine, outputPath);
        }
        catch (IllegalArgumentException e) {
          ex[0] = e;
        }
        catch (IOException e) {
          ex[0] = e;
        }
      }
    });
    if (ex[0] != null) {
      if (ex[0] instanceof IOException) {
        throw (IOException)ex[0];
      }
      else if (ex[0] instanceof IllegalArgumentException) {
        throw (IllegalArgumentException)ex[0];
      }
      else {
        LOG.error(ex[0]);
      }
    }
    return ArrayUtil.toStringArray(commandLine);
  }

  private void createStartupCommand(final ModuleChunk chunk, @NonNls final List<String> commandLine, final String outputPath) throws IOException {
    final Sdk jdk = getJdkForStartupCommand(chunk);
    final String versionString = jdk.getVersionString();
    if (versionString == null || "".equals(versionString) || !(jdk.getSdkType() instanceof JavaSdkType)) {
      throw new IllegalArgumentException(CompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()));
    }

    final JavaSdkType sdkType = (JavaSdkType)jdk.getSdkType();
    final String toolsJarPath = sdkType.getToolsPath(jdk);
    if (toolsJarPath == null) {
      throw new IllegalArgumentException(CompilerBundle.message("javac.error.tools.jar.missing", jdk.getName()));
    }

    final boolean isVersion1_0 = CompilerUtil.isOfVersion(versionString, "1.0");
    final boolean isVersion1_1 = CompilerUtil.isOfVersion(versionString, "1.1");
    final boolean isVersion1_2 = CompilerUtil.isOfVersion(versionString, "1.2");
    final boolean isVersion1_3 = CompilerUtil.isOfVersion(versionString, "1.3");
    final boolean isVersion1_4 = CompilerUtil.isOfVersion(versionString, "1.4");
    final boolean isVersion1_5 = CompilerUtil.isOfVersion(versionString, "1.5") || CompilerUtil.isOfVersion(versionString, "5.0");
    final boolean isVersion1_5_or_higher = isVersion1_5 || !(isVersion1_0 || isVersion1_1 || isVersion1_2 || isVersion1_3 || isVersion1_4);

    final JavacSettings javacSettings = JavacSettings.getInstance(myProject);

    final String vmExePath = sdkType.getVMExecutablePath(jdk);

    commandLine.add(vmExePath);
    if (isVersion1_1 || isVersion1_0) {
      commandLine.add("-mx" + javacSettings.MAXIMUM_HEAP_SIZE + "m");
    }
    else {
      commandLine.add("-Xmx" + javacSettings.MAXIMUM_HEAP_SIZE + "m");
    }

    final List<String> additionalOptions = new ArrayList<String>();
    StringTokenizer tokenizer = new StringTokenizer(javacSettings.getOptionsString(), " ");
    while (tokenizer.hasMoreTokens()) {
      @NonNls String token = tokenizer.nextToken();
      if (isVersion1_0) {
        if ("-deprecation".equals(token)) {
          continue; // not supported for this version
        }
      }
      if (isVersion1_0 || isVersion1_1 || isVersion1_2 || isVersion1_3 || isVersion1_4) {
        if ("-Xlint".equals(token)) {
          continue; // not supported in these versions
        }
      }
      if (token.startsWith("-J-")) {
        commandLine.add(token.substring("-J".length()));
      }
      else {
        additionalOptions.add(token);
      }
    }

    CompilerUtil.addLocaleOptions(commandLine, false);

    commandLine.add("-classpath");

    if (isVersion1_0) {
      commandLine.add(sdkType.getToolsPath(jdk)); //  do not use JavacRunner for jdk 1.0
    }
    else {
      commandLine.add(sdkType.getToolsPath(jdk) + File.pathSeparator + JavaSdkUtil.getIdeaRtJarPath());
      commandLine.add(JavacRunner.class.getName());
      commandLine.add("\"" + versionString + "\"");
    }

    if (isVersion1_2 || isVersion1_1 || isVersion1_0) {
      commandLine.add(JAVAC_MAIN_CLASS_OLD);
    }
    else {
      commandLine.add(JAVAC_MAIN_CLASS);
    }

    LanguageLevel languageLevel = chunk.getLanguageLevel();
    CompilerUtil.addSourceCommandLineSwitch(jdk, languageLevel, commandLine);

    commandLine.add("-verbose");

    final String cp = chunk.getCompilationClasspath();
    final String bootCp = chunk.getCompilationBootClasspath();

    final String classPath;
    if (isVersion1_0 || isVersion1_1) {
      classPath = bootCp + File.pathSeparator + cp;
    }
    else {
      classPath = cp;
      commandLine.add("-bootclasspath");
      addClassPathValue(jdk, false, commandLine, bootCp, "javac_bootcp");
    }

    commandLine.add("-classpath");
    addClassPathValue(jdk, isVersion1_0, commandLine, classPath, "javac_cp");

    if (!isVersion1_1 && !isVersion1_0) {
      commandLine.add("-sourcepath");
      // this way we tell the compiler that the sourcepath is "empty". However, javac thinks that sourcepath is 'new File("")'
      // this may cause problems if we have java code in IDEA working directory
      commandLine.add("\"\"");
    }

    commandLine.add("-d");
    commandLine.add(outputPath.replace('/', File.separatorChar));

    for (String option : additionalOptions) {
      commandLine.add(option);
    }

    final VirtualFile[] files = chunk.getFilesToCompile();

    if (isVersion1_0) {
      for (VirtualFile file : files) {
        String path = file.getPath();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Adding path for compilation " + path);
        }
        commandLine.add(CompilerUtil.quotePath(path));
      }
    }
    else {
      File sourcesFile = FileUtil.createTempFile("javac", ".tmp");
      sourcesFile.deleteOnExit();
      myTempFiles.add(sourcesFile);
      final PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(sourcesFile)));
      try {
        for (final VirtualFile file : files) {
          // Important: should use "/" slashes!
          // but not for JDK 1.5 - see SCR 36673
          final String path = isVersion1_5_or_higher ? file.getPath().replace('/', File.separatorChar) : file.getPath();
          if (LOG.isDebugEnabled()) {
            LOG.debug("Adding path for compilation " + path);
          }
          writer.println(isVersion1_1 ? path : CompilerUtil.quotePath(path));
        }
      }
      finally {
        writer.close();
      }
      commandLine.add("@" + sourcesFile.getAbsolutePath());
    }
  }

  private void addClassPathValue(final Sdk jdk,
                                 final boolean isVersion1_0,
                                 final List<String> commandLine,
                                 final String cpString,
                                 @NonNls final String tempFileName) throws IOException {
    // must include output path to classpath, otherwise javac will compile all dependent files no matter were they compiled before or not
    if (isVersion1_0) {
      commandLine.add(((JavaSdkType)jdk.getSdkType()).getToolsPath(jdk) + File.pathSeparator + cpString);
    }
    else {
      File cpFile = FileUtil.createTempFile(tempFileName, ".tmp");
      cpFile.deleteOnExit();
      myTempFiles.add(cpFile);
      final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cpFile)));
      try {
        CompilerIOUtil.writeString(cpString, out);
      }
      finally {
        out.close();
      }
      commandLine.add("@" + cpFile.getAbsolutePath());
    }
  }

  private Sdk getJdkForStartupCommand(final ModuleChunk chunk) {
    final Sdk jdk = chunk.getJdk();
    if (ApplicationManager.getApplication().isUnitTestMode() && JavacSettings.getInstance(myProject).isTestsUseExternalCompiler()) {
      final String jdkHomePath = CompilerConfigurationImpl.getTestsExternalCompilerHome();
      if (jdkHomePath == null) {
        throw new IllegalArgumentException("[TEST-MODE] Cannot determine home directory for JDK to use javac from");
      }
      // when running under Mock JDK use VM executable from the JDK on which the tests run
      return new MockJdkWrapper(jdkHomePath, jdk);
    }
    return jdk;
  }

  public void compileFinished() {
    FileUtil.asyncDelete(myTempFiles);
  }
}
