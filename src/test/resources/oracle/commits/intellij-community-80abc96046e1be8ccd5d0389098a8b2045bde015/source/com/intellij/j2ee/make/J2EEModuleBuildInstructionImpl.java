package com.intellij.j2ee.make;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Degenerator;
import com.intellij.util.PathUtil;
import com.intellij.util.io.ZipUtil;
import com.intellij.j2ee.make.impl.MakeUtilImpl;
import gnu.trove.THashSet;

import java.io.*;
import java.util.Set;
import java.util.Collection;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class J2EEModuleBuildInstructionImpl extends BuildInstructionBase implements J2EEModuleBuildInstruction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.make.J2EEModuleBuildInstructionImpl");

  private final ModuleBuildProperties myBuildProperties;

  public J2EEModuleBuildInstructionImpl(ModuleBuildProperties moduleToBuild, String outputRelativePath) {
    super(outputRelativePath, moduleToBuild.getModule());
    myBuildProperties = moduleToBuild;
    LOG.assertTrue(!isExternalDependencyInstruction());
  }

  public void addFilesToExploded(final CompileContext context,
                                 final File outputDir,
                                 final Set<String> writtenPaths,
                                 final FileFilter fileFilter) throws IOException {
    //todo optmization: cache created directory and issue single FileCopy on it
    final File target = MakeUtil.canonicalRelativePath(outputDir, getOutputRelativePath());
    final Ref<Boolean> externalDependencyFound = new Ref<Boolean>(Boolean.FALSE);
    final BuildRecipe buildRecipe = getChildInstructions(context);
    try {
      if (myBuildProperties.isExplodedEnabled()) {
        File fromFile = new File(myBuildProperties.getExplodedPath());
        MakeUtil.getInstance().copyFile(fromFile, target, context, writtenPaths, fileFilter);
        // copy dependencies
        buildRecipe.visitInstructionsWithExceptions(new BuildInstructionVisitor() {
          public boolean visitInstruction(BuildInstruction instruction) throws Exception {
            if (instruction.isExternalDependencyInstruction()) {
              instruction.addFilesToExploded(context, target, writtenPaths, fileFilter);
              externalDependencyFound.set(Boolean.TRUE);
            }
            return true;
          }
        }, false);
      }
      else {
        buildRecipe.visitInstructionsWithExceptions(new BuildInstructionVisitor() {
          public boolean visitInstruction(BuildInstruction instruction) throws Exception {
            instruction.addFilesToExploded(context, target, writtenPaths, fileFilter);
            if (instruction.isExternalDependencyInstruction()) {
              externalDependencyFound.set(Boolean.TRUE);
            }
            return true;
          }
        }, false);
      }
      if (externalDependencyFound.get().booleanValue()) {
        MakeUtilImpl.writeManifest(buildRecipe, context, target);
      }
    }
    catch (Exception e) {
      if (e instanceof IOException) throw (IOException)e;
      if (e instanceof RuntimeException) throw (RuntimeException)e;
      Degenerator.unableToDegenerateMarker();
    }
  }

  public boolean accept(BuildInstructionVisitor visitor) throws Exception {
    return visitor.visitJ2EEModuleBuildInstruction(this);
  }

  public void addFilesToJar(final CompileContext context,
                            final File jarFile,
                            final JarOutputStream outputStream,
                            BuildRecipe dependencies,
                            final Set<String> writtenRelativePaths,
                            final FileFilter fileFilter) throws IOException {
    // create temp jars, and add these into upper level jar
    // todo optimization: cache created jars
    final File tempFile;
    final BuildRecipe childDependencies = new BuildRecipeImpl();
    if (myBuildProperties.isJarEnabled()) {
      tempFile = new File(myBuildProperties.getJarPath());
      final BuildRecipe childModuleRecipe = getChildInstructions(context);
      childModuleRecipe.visitInstructions(new BuildInstructionVisitor() {
        public boolean visitInstruction(BuildInstruction instruction) throws RuntimeException {
          if (instruction.isExternalDependencyInstruction()) {
            childDependencies.addInstruction(instruction);
          }
          return true;
        }
      }, false);
    }
    else {
      String moduleName = ModuleUtil.getModuleNameInReadAction(getModule());
      tempFile = createTempFile(moduleName,".tmp");
      makeJar(context, tempFile, childDependencies, fileFilter);
      childDependencies.visitInstructions(new BuildInstructionVisitor() {
        public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws Exception {
          File file = new File(PathUtil.getCanonicalPath(MakeUtil.appendToPath(tempFile.getPath(), instruction.getOutputRelativePath())));
          addFileToDelete(file);
          return true;
        }
      }, false);
    }
    ZipUtil.addFileToZip(outputStream, tempFile, getOutputRelativePath(), writtenRelativePaths, fileFilter);
    try {
      childDependencies.visitInstructionsWithExceptions(new BuildInstructionVisitor() {
        public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws Exception {
          File file = instruction.getFile();
          String dependencyRelativePath = PathUtil.getCanonicalPath(MakeUtil.appendToPath(getOutputRelativePath(), instruction.getOutputRelativePath()));

          ZipUtil.addFileToZip(outputStream, file, dependencyRelativePath, writtenRelativePaths, fileFilter);
          return true;
        }

        public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws Exception {
          if (instruction.getJarFile() == null) {
            File tempJar = File.createTempFile("___",".tmp");
            addFileToDelete(tempJar);
            instruction.makeJar(context, tempJar, fileFilter);
          }
          File jarFile = instruction.getJarFile();
          String dependencyRelativePath = PathUtil.getCanonicalPath(MakeUtil.appendToPath(getOutputRelativePath(), instruction.getOutputRelativePath()));

          ZipUtil.addFileToZip(outputStream, jarFile, dependencyRelativePath, writtenRelativePaths, fileFilter);
          return true;
        }
      }, false);
    }
    catch (Exception e) {
      if (e instanceof IOException) throw (IOException)e;
      if (e instanceof RuntimeException) throw (RuntimeException)e;
      Degenerator.unableToDegenerateMarker();
    }
  }

  // return jarFile and possible jars linked via manifest
  public void makeJar(final CompileContext context,
                      final File jarFile,
                      final BuildRecipe dependencies,
                      final FileFilter fileFilter) throws IOException {
    final BuildRecipe buildRecipe = getChildInstructions(context);
    final Manifest manifest = MakeUtil.getInstance().createManifest(buildRecipe);
    if (manifest == null) {
      context.addMessage(CompilerMessageCategory.WARNING, "Using user supplied manifest.mf",null,-1,-1);
    }
    FileUtil.createParentDirs(jarFile);
    final JarOutputStream jarOutputStream = manifest == null ?
                                            new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarFile))) :
                                            new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarFile)), manifest);

    final Set<String> tempWrittenRelativePaths = new THashSet<String>();
    try {
      buildRecipe.visitInstructionsWithExceptions(new BuildInstructionVisitor() {
        public boolean visitInstruction(BuildInstruction instruction) throws IOException {
          instruction.addFilesToJar(context, jarFile, jarOutputStream, dependencies, tempWrittenRelativePaths, fileFilter);
          return true;
        }
      }, false);
    }
    catch (Exception e) {
      if (e instanceof IOException) throw (IOException)e;
      if (e instanceof RuntimeException) throw (RuntimeException)e;
      Degenerator.unableToDegenerateMarker();
    }
    finally {
      jarOutputStream.close();
    }
  }

  public BuildRecipe getChildInstructions(CompileContext context) {
    return ModuleBuilder.getInstance(getModule()).getModuleBuildInstructions(context);
  }

  public ModuleBuildProperties getBuildProperties() {
    return myBuildProperties;
  }

  public String toString() {
    String s = "J2EE build instruction: ";
    s += ModuleUtil.getModuleNameInReadAction(getModule());
    s += "->"+getOutputRelativePath();
    return s;
  }

  public File findFileByRelativePath(String relativePath) {
    if (!relativePath.startsWith(getOutputRelativePath())) return null;
    final String pathFromFile = relativePath.substring(getOutputRelativePath().length());
    final Ref<File> file = new Ref<File>();
    final BuildRecipe buildRecipe = getChildInstructions(null);
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitInstruction(BuildInstruction instruction) throws RuntimeException {
        final File found = instruction.findFileByRelativePath(pathFromFile);
        if (found != null) {
          file.set(found);
          return false;
        }
        return true;
      }
    }, false);
    return file.get();
  }

  public void collectFilesToDelete(final Collection<File> filesToDelete) {
    super.collectFilesToDelete(filesToDelete);
    BuildRecipe childInstructions = getChildInstructions(null);
    childInstructions.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitInstruction(BuildInstruction instruction) throws Exception {
        ((BuildInstructionBase)instruction).collectFilesToDelete(filesToDelete);
        return true;
      }
    }, false);
  }
}
