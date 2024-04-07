/**
 * created at Jan 3, 2002
 * @author Jeka
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;
import java.util.Collection;
import java.util.List;

public class CompilerUtil {
  public static String quotePath(String path) {
    if(path != null && path.indexOf(' ') != -1) {
      path = path.replaceAll("\\\\", "\\\\\\\\");
      path = '"' + path + '"';
    }
    return path;
  }

  public static final FileFilter CLASS_FILES_FILTER = new FileFilter() {
    public boolean accept(File pathname) {
      if (pathname.isDirectory()) {
        return true;
      }
      final int dotIndex = pathname.getName().lastIndexOf('.');
      if (dotIndex > 0) {
        String extension = pathname.getName().substring(dotIndex);
        //noinspection HardCodedStringLiteral
        if (extension.equalsIgnoreCase(".class")) {
          return true;
        }
      }
      return false;
    }
  };
  public static void collectFiles(Collection<File> container, File rootDir, FileFilter fileFilter) {
    final File[] files = rootDir.listFiles(fileFilter);
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.isDirectory()) {
        collectFiles(container, file, fileFilter);
      }
      else {
        container.add(file);
      }
    }
  }

  public static void refreshPaths(final String[] paths) {
    if (paths.length == 0) {
      return;
    }

    final LocalFileSystem fs = LocalFileSystem.getInstance();
    for (String path : paths) {
      final VirtualFile file = fs.refreshAndFindFileByPath(path.replace(File.separatorChar, '/'));
      if (file != null) {
        file.refresh(false, false);
      }
    }
  }


  /**
   * must not be called inside ReadAction
   * @param files
   */
  public static void refreshIOFiles(@NotNull final Collection<File> files) {
    if (files.isEmpty()) {
      return;
    }

    for (File file1 : files) {
      final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
      if (file != null) {
        file.refresh(false, false);
      }
    }
  }

  public static void refreshIODirectories(@NotNull final Collection<File> files) {
    if (files.isEmpty()) {
      return;
    }

    for (File file1 : files) {
      final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
      if (file != null) {
        file.refresh(false, true);
      }
    }
  }

  public static void refreshIOFile(final File file) {
    final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    if (vFile != null) {
      vFile.refresh(false, false);
    }
  }

  public static void addLocaleOptions(final List<String> commandLine, final boolean launcherUsed) {
    // need to specify default encoding so that javac outputs messages in 'correct' language
    //noinspection HardCodedStringLiteral
    commandLine.add((launcherUsed? "-J" : "") + "-D" + CharsetToolkit.FILE_ENCODING_PROPERTY + "=" + CharsetToolkit.getDefaultSystemCharset().name());
    // javac's VM should use the same default locale that IDEA uses in order for javac to print messages in 'correct' language
    //noinspection HardCodedStringLiteral
    final String lang = System.getProperty("user.language");
    if (lang != null) {
      //noinspection HardCodedStringLiteral
      commandLine.add((launcherUsed? "-J" : "") + "-Duser.language=" + lang);
    }
    //noinspection HardCodedStringLiteral
    final String country = System.getProperty("user.country");
    if (country != null) {
      //noinspection HardCodedStringLiteral
      commandLine.add((launcherUsed? "-J" : "") + "-Duser.country=" + country);
    }
    //noinspection HardCodedStringLiteral
    final String region = System.getProperty("user.region");
    if (region != null) {
      //noinspection HardCodedStringLiteral
      commandLine.add((launcherUsed? "-J" : "") + "-Duser.region=" + region);
    }
  }

  public static void addSourceCommandLineSwitch(final Sdk jdk, LanguageLevel chunkLanguageLevel, @NonNls final List<String> commandLine) {
    final String versionString = jdk.getVersionString();
    if (versionString == null || "".equals(versionString)) {
      throw new IllegalArgumentException(CompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()));
    }

    final LanguageLevel applicableLanguageLevel = getApplicableLanguageLevel(versionString, chunkLanguageLevel);
    if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_6)) {
      commandLine.add("-source");
      commandLine.add("1.6");
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_5)) {
      commandLine.add("-source");
      commandLine.add("1.5");
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_4)) {
      commandLine.add("-source");
      commandLine.add("1.4");
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_3)) {
      if (!(isOfVersion(versionString, "1.3") || isOfVersion(versionString, "1.2") || isOfVersion(versionString, "1.1"))) {
        //noinspection HardCodedStringLiteral
        commandLine.add("-source");
        commandLine.add("1.3");
      }
    }
  }

  @NotNull
  public static LanguageLevel getApplicableLanguageLevel(String versionString, @NotNull LanguageLevel languageLevel) {
    final boolean is5OrNewer = isOfVersion(versionString, "1.5")
                                 || isOfVersion(versionString, "5.0")
                                 || isOfVersion(versionString, "1.6.")
                                 || isOfVersion(versionString, "1.7.");
    if (LanguageLevel.JDK_1_5.equals(languageLevel) && !is5OrNewer) {
      languageLevel = LanguageLevel.JDK_1_4;
    }

    if (LanguageLevel.JDK_1_4.equals(languageLevel) && !isOfVersion(versionString, "1.4") && !is5OrNewer) {
      languageLevel = LanguageLevel.JDK_1_3;
    }

    return languageLevel;
  }

  public static boolean isOfVersion(String versionString, String checkedVersion) {
    return versionString.contains(checkedVersion);
  }
}
