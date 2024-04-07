package com.intellij.jar;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.DummyCompileContext;
import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.components.*;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.deployment.ModuleLink;
import com.intellij.openapi.deployment.PackagingConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.wm.WindowManager;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * @author cdr
 */
@State(
  name = "BuildJarProjectSettings",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class BuildJarProjectSettings implements PersistentStateComponent<Element>, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.jar.BuildJarProjectSettings");

  public boolean BUILD_JARS_ON_MAKE = false;
  @NonNls private static final String MAIN_CLASS = Attributes.Name.MAIN_CLASS.toString();
  @NonNls private static final String JAR_EXTENSION = ".jar";
  @NonNls public static final String BUILD_JAR_PROJECT_SETTINGS_COMPONENT_NAME = "BuildJarProjectSettings";
  private final Project myProject;

  public static BuildJarProjectSettings getInstance(Project project) {
    return project.getComponent(BuildJarProjectSettings.class);
  }

  public BuildJarProjectSettings(Project project) {
    myProject = project;
  }

  public Element getState() {
    try {
      final Element e = new Element("state");
      DefaultJDOMExternalizer.writeExternal(this, e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public boolean isBuildJarOnMake() {
    return BUILD_JARS_ON_MAKE;
  }

  public void projectOpened() {
    if (BUILD_JARS_ON_MAKE) {
      CompilerManager compilerManager = CompilerManager.getInstance(myProject);
      compilerManager.addCompiler(JarCompiler.getInstance());
    }
  }

  public void projectClosed() {
  }

  @NonNls @NotNull
  public String getComponentName() {
    return BUILD_JAR_PROJECT_SETTINGS_COMPONENT_NAME;
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public void setBuildJarOnMake(final boolean buildJar) {
    if (buildJar != BUILD_JARS_ON_MAKE) {
      CompilerManager compilerManager = CompilerManager.getInstance(myProject);
      if (buildJar) {
        compilerManager.addCompiler(JarCompiler.getInstance());
      }
      else {
        compilerManager.removeCompiler(JarCompiler.getInstance());
      }
    }
    BUILD_JARS_ON_MAKE = buildJar;
  }

  public void buildJarsWithProgress() {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable(){
      public void run() {
        buildJars(ProgressManager.getInstance().getProgressIndicator());
      }
    }, IdeBundle.message("jar.build.progress.title"), true, myProject);
  }

  private void buildJars(final ProgressIndicator progressIndicator) {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    try {
      for (Module module : modules) {
        BuildJarSettings buildJarSettings = BuildJarSettings.getInstance(module);
        if (buildJarSettings == null || !buildJarSettings.isBuildJar()) continue;
        String presentableJarPath = "'" + FileUtil.toSystemDependentName(VfsUtil.urlToPath(buildJarSettings.getJarUrl() + "'"));
        if (progressIndicator != null) {
          progressIndicator.setText(IdeBundle.message("jar.build.progress", presentableJarPath));
        }
        buildJar(module, buildJarSettings,progressIndicator, null);
        WindowManager.getInstance().getStatusBar(myProject).setInfo(IdeBundle.message("jar.build.success.message", presentableJarPath));
      }
    }
    catch (ProcessCanceledException e) {
      WindowManager.getInstance().getStatusBar(myProject).setInfo(IdeBundle.message("jar.build.cancelled"));
    }
    catch (final IOException e) {
      ApplicationManager.getApplication().invokeLater(new Runnable(){
        public void run() {
          Messages.showErrorDialog(myProject, e.toString(), IdeBundle.message("jar.build.error.title"));
        }
      });
    }
  }

  static void buildJar(final Module module, final BuildJarSettings buildJarSettings, final ProgressIndicator progressIndicator,
                       @Nullable CompileContext context) throws IOException {
    String jarPath = buildJarSettings.getJarUrl();
    final File jarFile = new File(VfsUtil.urlToPath(jarPath));
    jarFile.delete();

    FileUtil.createParentDirs(jarFile);
    BuildRecipe buildRecipe = new ReadAction<BuildRecipe>() {
      protected void run(final Result<BuildRecipe> result) {
        result.setResult(getBuildRecipe(module, buildJarSettings));
      }
    }.execute().getResultObject();
    Manifest manifest = DeploymentUtil.getInstance().createManifest(buildRecipe);
    String mainClass = buildJarSettings.getMainClass();
    if (manifest != null && !Comparing.strEqual(mainClass, null)) {
      manifest.getMainAttributes().putValue(MAIN_CLASS,mainClass);
    }

    // write temp file and rename it to the jar to avoid deployment of incomplete jar. SCR #30303
    final File tempFile;
    try {
      tempFile = File.createTempFile("___"+ FileUtil.getNameWithoutExtension(jarFile), JAR_EXTENSION, jarFile.getParentFile());
    }
    catch (IOException e) {
      final String errorMessage =
          IdeBundle.message("error.message.jar.build.cannot.create.temporary.file.in.0", jarFile.getParentFile().getAbsolutePath());
      if (context != null) {
        context.addMessage(CompilerMessageCategory.ERROR, errorMessage, null, -1, -1);
      }
      else {
        Messages.showErrorDialog(module.getProject(), errorMessage, CommonBundle.getErrorTitle());
      }
      return;
    }
    final JarOutputStream jarOutputStream = manifest == null ?
                                            new JarOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile))) :
                                            new JarOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)), manifest);

    final Set<String> tempWrittenRelativePaths = new THashSet<String>();
    final BuildRecipe dependencies = DeploymentUtil.getInstance().createBuildRecipe();
    try {
      buildRecipe.visitInstructionsWithExceptions(new BuildInstructionVisitor() {
        public boolean visitInstruction(BuildInstruction instruction) throws IOException {
          ProgressManager.getInstance().checkCanceled();
          if (instruction instanceof FileCopyInstruction) {
            FileCopyInstruction fileCopyInstruction = (FileCopyInstruction)instruction;
            File file = fileCopyInstruction.getFile();
            if (file == null || !file.exists()) return true;
            String presentablePath = FileUtil.toSystemDependentName(file.getPath());
            if (progressIndicator != null) {
              progressIndicator.setText2(IdeBundle.message("jar.build.processing.file.progress", presentablePath));
            }
          }
          //todo[nik] use IncrementalPackagingCompiler instead
          instruction.addFilesToJar(DummyCompileContext.getInstance(), tempFile, jarOutputStream, dependencies, tempWrittenRelativePaths, null);
          return true;
        }
      }, false);
      buildRecipe.visitInstructionsWithExceptions(new BuildInstructionVisitor() {
        public boolean visitJarAndCopyBuildInstruction(final JarAndCopyBuildInstruction instruction) throws Exception {
          instruction.deleteTemporaryJars();
          return true;
        }
      }, false);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }
    finally {
      jarOutputStream.close();
      try {
        FileUtil.rename(tempFile, jarFile);
      }
      catch (IOException e) {
        ApplicationManager.getApplication().invokeLater(new Runnable(){
          public void run() {
            String message = IdeBundle.message("jar.build.cannot.overwrite.error", FileUtil.toSystemDependentName(jarFile.getPath()),
                                               FileUtil.toSystemDependentName(tempFile.getPath()));
            Messages.showErrorDialog(module.getProject(), message, IdeBundle.message("jar.build.error.title"));
          }
        });
      }
    }
  }

  static BuildRecipe getBuildRecipe(final Module module, final BuildJarSettings buildJarSettings) {
    final DummyCompileContext compileContext = DummyCompileContext.getInstance();
    PackagingConfiguration packagingConfiguration = buildJarSettings.getPackagingConfiguration();
    BuildRecipe buildRecipe = DeploymentUtil.getInstance().createBuildRecipe();
    LibraryLink[] libraries = packagingConfiguration.getContainingLibraries();
    for (LibraryLink libraryLink : libraries) {
      DeploymentUtil.getInstance().addLibraryLink(compileContext, buildRecipe, libraryLink, module, null);
    }
    ModuleLink[] modules = packagingConfiguration.getContainingModules();
    DeploymentUtil.getInstance().addJavaModuleOutputs(module, modules, buildRecipe, compileContext, null, IdeBundle.message("jar.build.module.presentable.name", module.getName()));
    return buildRecipe;
  }
}
