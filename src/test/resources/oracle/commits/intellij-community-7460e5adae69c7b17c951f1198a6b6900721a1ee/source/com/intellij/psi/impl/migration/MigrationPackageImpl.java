package com.intellij.psi.impl.migration;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiPackageImpl;

/**
 * @author dsl
 */
public class MigrationPackageImpl extends PsiPackageImpl implements PsiPackage {
  private final PsiMigrationImpl myMigration;

  public MigrationPackageImpl(PsiMigrationImpl migration, String qualifiedName) {
    super(migration.getManager(), qualifiedName);
    myMigration = migration;
  }

  public String toString() {
    return "MigrationPackage: " + getQualifiedName();
  }

  public boolean isWritable() {
    return false;
  }

  public boolean isValid() {
    return myMigration.isValid();
  }

  public String getText() {
    return null;
  }

  public void handleQualifiedNameChange(String newQualifiedName) {
    throw new UnsupportedOperationException();
  }

  public VirtualFile[] occursInPackagePrefixes() {
    return VirtualFile.EMPTY_ARRAY;
  }
}
