package com.intellij.psi.impl.file;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

public class PsiDirectoryImpl extends PsiElementBase implements PsiDirectory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiDirectoryImpl");

  private final PsiManagerImpl myManager;
  private VirtualFile myFile;

  public PsiDirectoryImpl(PsiManagerImpl manager, VirtualFile file) {
    myManager = manager;
    myFile = file;
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myFile;
  }

  public boolean isDirectory() {
    return true;
  }

  public boolean isValid() {
    return myFile.isValid();
  }

  @NotNull
  public Language getLanguage() {
    return Language.ANY;
  }

  public PsiManager getManager() {
    return myManager;
  }

  @NotNull
  public String getName() {
    return myFile.getName();
  }

  @NotNull
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);

    /*
    final String oldName = myFile.getName();
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
    event.setElement(this);
    event.setPropertyName(PsiTreeChangeEvent.PROP_DIRECTORY_NAME);
    event.setOldValue(oldName);
    myManager.beforePropertyChange(event);
    */

    try {
      myFile.rename(myManager, name);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString());
    }

    /*
    PsiUndoableAction undoableAction = new PsiUndoableAction(){
      public void undo() throws IncorrectOperationException {
        if (!PsiDirectoryImpl.this.isValid()){
          throw new IncorrectOperationException();
        }
        setName(oldName);
      }
    };
    */

    /*
    event = new PsiTreeChangeEventImpl(myManager);
    event.setElement(this);
    event.setPropertyName(PsiTreeChangeEvent.PROP_DIRECTORY_NAME);
    event.setOldValue(oldName);
    event.setNewValue(name);
    event.setUndoableAction(undoableAction);
    myManager.propertyChanged(event);
    */
    return this;
  }

  public void checkSetName(String name) throws IncorrectOperationException {
    //CheckUtil.checkIsIdentifier(name);
    CheckUtil.checkWritable(this);
    VirtualFile parentFile = myFile.getParent();
    if (parentFile == null) {
      throw new IncorrectOperationException(VfsBundle.message("cannot.rename.root.directory"));
    }
    VirtualFile child = parentFile.findChild(name);
    if (child != null && !child.equals(myFile)) {
      throw new IncorrectOperationException(VfsBundle.message("file.already.exists.error", child.getPresentableUrl()));
    }
  }

  public PsiDirectory getParentDirectory() {
    VirtualFile parentFile = myFile.getParent();
    if (parentFile == null) return null;
    return myManager.findDirectory(parentFile);
  }

  @NotNull
  public PsiDirectory[] getSubdirectories() {
    VirtualFile[] files = myFile.getChildren();
    ArrayList<PsiDirectory> dirs = new ArrayList<PsiDirectory>();
    for (VirtualFile file : files) {
      PsiDirectory dir = myManager.findDirectory(file);
      if (dir != null) {
        dirs.add(dir);
      }
    }
    return dirs.toArray(new PsiDirectory[dirs.size()]);
  }

  @NotNull
  public PsiFile[] getFiles() {
    LOG.assertTrue(myFile.isValid());
    VirtualFile[] files = myFile.getChildren();
    ArrayList<PsiFile> psiFiles = new ArrayList<PsiFile>();
    for (VirtualFile file : files) {
      PsiFile psiFile = myManager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }
    return psiFiles.toArray(new PsiFile[psiFiles.size()]);
  }

  public PsiDirectory findSubdirectory(@NotNull String name) {
    VirtualFile childVFile = myFile.findChild(name);
    if (childVFile == null) return null;
    return myManager.findDirectory(childVFile);
  }

  public PsiFile findFile(@NotNull String name) {
    VirtualFile childVFile = myFile.findChild(name);
    if (childVFile == null) return null;
    return myManager.findFile(childVFile);
  }

  public boolean processChildren(PsiElementProcessor<PsiFileSystemItem> processor) {
    LOG.assertTrue(isValid());

    for (VirtualFile vFile : myFile.getChildren()) {
      if (vFile.isDirectory()) {
        PsiDirectory dir = myManager.findDirectory(vFile);
        if (dir != null) {
          if(!processor.execute(dir)) return false;
        }
      }
      else {
        PsiFile file = myManager.findFile(vFile);
        if (file != null) {
          if(!processor.execute(file)) return false;
        }
      }
    }
    return true;
  }

  @NotNull
  public PsiElement[] getChildren() {
    LOG.assertTrue(isValid());

    VirtualFile[] files = myFile.getChildren();
    final ArrayList<PsiElement> children = new ArrayList<PsiElement>(files.length);
    processChildren(new PsiElementProcessor<PsiFileSystemItem>() {
      public boolean execute(final PsiFileSystemItem element) {
        children.add(element);
        return true;
      }
    });

    return children.toArray(new PsiElement[children.size()]);
  }

  public PsiDirectory getParent() {
    return getParentDirectory();
  }

  public PsiFile getContainingFile() {
    return null;
  }

  public TextRange getTextRange() {
    return null;
  }

  public int getStartOffsetInParent() {
    return -1;
  }

  public int getTextLength() {
    return -1;
  }

  public PsiElement findElementAt(int offset) {
    return null;
  }

  public int getTextOffset() {
    return -1;
  }

  public String getText() {
    return ""; // TODO throw new InsupportedOperationException()
  }

  @NotNull
  public char[] textToCharArray() {
    return ArrayUtil.EMPTY_CHAR_ARRAY; // TODO throw new InsupportedOperationException()
  }

  public boolean textMatches(@NotNull CharSequence text) {
    return false;
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  public final boolean isWritable() {
    return myFile.isWritable();
  }

  public boolean isPhysical() {
    return !(myFile.getFileSystem() instanceof DummyFileSystem);
  }

  /**
   * @not_implemented
   */
  public PsiElement copy() {
    LOG.error("not implemented");
    return null;
  }


  @NotNull
  public PsiDirectory createSubdirectory(@NotNull String name) throws IncorrectOperationException {
    checkCreateSubdirectory(name);

    try {
      VirtualFile file = getVirtualFile().createChildDirectory(myManager, name);
      return myManager.findDirectory(file);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString());
    }
  }

  public void checkCreateSubdirectory(@NotNull String name) throws IncorrectOperationException {
    // TODO : another check?
    //CheckUtil.checkIsIdentifier(name);
    VirtualFile existingFile = getVirtualFile().findChild(name);
    if (existingFile != null) {
      throw new IncorrectOperationException(VfsBundle.message("file.already.exists.error", existingFile.getPresentableUrl()));
    }
    CheckUtil.checkWritable(this);
  }

  @NotNull
  public PsiFile createFile(@NotNull String name) throws IncorrectOperationException {
    checkCreateFile(name);

    try {
      VirtualFile vFile = getVirtualFile().createChildData(myManager, name);
      return myManager.findFile(vFile);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString());
    }
  }

  @NotNull
  public PsiFile copyFileFrom(@NotNull String newName, @NotNull PsiFile originalFile) throws IncorrectOperationException {
    checkCreateFile(newName);

    final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(originalFile);
    if (document != null) {
      FileDocumentManager.getInstance().saveDocument(document);
    }

    final VirtualFile parent = getVirtualFile();
    try {
      final VirtualFile vFile = originalFile.getVirtualFile();
      if (vFile == null) throw new IncorrectOperationException("Cannot copy nonphysical file");
      VirtualFile copyVFile;
      if (parent.getFileSystem() == vFile.getFileSystem()) {
        copyVFile = vFile.copy(this, parent, newName);
      } else {
        copyVFile = VfsUtil.copyFile(this, vFile, parent, newName);
      }
      final PsiFile copyPsi = myManager.findFile(copyVFile);
      LOG.assertTrue(copyPsi != null);
      if (copyPsi instanceof PsiFileImpl) {
        ChangeUtil.encodeInformation((TreeElement)SourceTreeToPsiMap.psiElementToTree(copyPsi));
        PsiUtil.updatePackageStatement(copyPsi);
        ChangeUtil.decodeInformation((TreeElement)SourceTreeToPsiMap.psiElementToTree(copyPsi));
      }
      return copyPsi;
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString(),e);
    }

  }

  public void checkCreateFile(@NotNull String name) throws IncorrectOperationException {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType type = fileTypeManager.getFileTypeByFileName(name);
    /* [dsl] now it is possible to create a Java file outside source path.
        if (type == FileType.JAVA) {
          if (getPackage() == null){
            throw new IncorrectOperationException("Cannot create java-files outside sourcepath");
          }
        }
        else
    */
    if (type == StdFileTypes.CLASS) {
      throw new IncorrectOperationException("Cannot create class-file");
    }

    VirtualFile existingFile = getVirtualFile().findChild(name);
    if (existingFile != null) {
      throw new IncorrectOperationException(VfsBundle.message("file.already.exists.error", existingFile.getPresentableUrl()));
    }
    CheckUtil.checkWritable(this);
  }


  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    checkAdd(element);
    if (element instanceof PsiDirectory) {
      LOG.error("not implemented");
      return null;
    }
    else if (element instanceof PsiFile) {
      PsiFile originalFile = (PsiFile)element;

      try {
        VirtualFile newVFile;
        final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myManager.getProject());
        if (originalFile instanceof PsiFileImpl) {
          newVFile = myFile.createChildData(myManager, originalFile.getName());
          String text = originalFile.getText();
          final PsiFile psiFile = getManager().findFile(newVFile);
          final Document document = psiFile == null ? null : psiDocumentManager.getDocument(psiFile);
          final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
          if (document != null) {
            document.setText(text);
            fileDocumentManager.saveDocument(document);
          } else {
            String lineSeparator = fileDocumentManager.getLineSeparator(newVFile, getProject());
            if (!lineSeparator.equals("\n")) {
              text = StringUtil.convertLineSeparators(text, lineSeparator);
            }

            final Writer writer = LoadTextUtil.getWriter(getProject(), newVFile, myManager, text, -1);
            try {
              writer.write(text);
            }
            finally {
              writer.close();
            }
          }
        }
        else {
          byte[] storedContents = ((PsiBinaryFileImpl)originalFile).getStoredContents();
          if (storedContents != null) {
            newVFile = myFile.createChildData(myManager, originalFile.getName());
            newVFile.setBinaryContent(storedContents);
          }
          else {
            newVFile = VfsUtil.copyFile(null, originalFile.getVirtualFile(), myFile);
          }
        }
        psiDocumentManager.commitAllDocuments();

        PsiFile newFile = myManager.findFile(newVFile);
        if (newFile instanceof PsiFileImpl) {
          ChangeUtil.encodeInformation((TreeElement)SourceTreeToPsiMap.psiElementToTree(newFile));
          PsiUtil.updatePackageStatement(newFile);
          ChangeUtil.decodeInformation((TreeElement)SourceTreeToPsiMap.psiElementToTree(newFile));
        }

        return newFile;
      }
      catch (IOException e) {
        throw new IncorrectOperationException(e.toString(),e);
      }
    }
    else if (element instanceof PsiClass) {
      final String name = ((PsiClass)element).getName();
      if (name != null) {
        final PsiClass newClass = JavaDirectoryService.getInstance().createClass(this, name);
        return newClass.replace(element);
      } else {
        LOG.error("not implemented");
        return null;
      }
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    if (element instanceof PsiDirectory) {
      String name = ((PsiDirectory)element).getName();
      PsiDirectory[] subpackages = getSubdirectories();
      for (PsiDirectory dir : subpackages) {
        if (Comparing.strEqual(dir.getName(),name)) {
          throw new IncorrectOperationException(VfsBundle.message("dir.already.exists.error", dir.getVirtualFile().getPresentableUrl()));
        }
      }
    }
    else if (element instanceof PsiFile) {
      String name = ((PsiFile)element).getName();
      PsiFile[] files = getFiles();
      for (PsiFile file : files) {
        if (Comparing.strEqual(file.getName(),name)) {
          throw new IncorrectOperationException(VfsBundle.message("file.already.exists.error", file.getVirtualFile().getPresentableUrl()));
        }
      }
    }
    else if (element instanceof PsiClass) {
      if (element.getParent() instanceof PsiFile) {
        JavaDirectoryServiceImpl.checkCreateClassOrInterface(this, ((PsiClass)element).getName());
      }
      else {
        LOG.error("not implemented");
      }
    }
    else {
      throw new IncorrectOperationException();
    }
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void delete() throws IncorrectOperationException {
    checkDelete();
    //PsiDirectory parent = getParentDirectory();

    /*
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
    event.setParent(parent);
    event.setChild(this);
    myManager.beforeChildRemoval(event);
    */

    try {
      myFile.delete(myManager);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString(),e);
    }

    /*
    //TODO : allow undo
    PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
    treeEvent.setParent(parent);
    treeEvent.setChild(this);
    treeEvent.setUndoableAction(null);
    myManager.childRemoved(treeEvent);
    */
  }

  public void checkDelete() throws IncorrectOperationException {
    CheckUtil.checkDelete(myFile);
  }

  /**
   * @not_implemented
   */
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    LOG.error("not implemented");
    return null;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitDirectory(this);
  }

  public String toString() {
    return "PsiDirectory:" + myFile.getPresentableUrl();
  }

  public ASTNode getNode() {
    return null;
  }

  public boolean canNavigate() {
    return EditSourceUtil.canNavigate(this);
  }

  public boolean canNavigateToSource() {
    return false;
  }

  public void navigate(boolean requestFocus) {
    final ProjectView projectView = ProjectView.getInstance(getProject());
    projectView.changeView(ProjectViewPane.ID);
    projectView.getProjectViewPaneById(ProjectViewPane.ID).select(this, getVirtualFile(), requestFocus);
    ToolWindowManager.getInstance(getProject()).getToolWindow(ToolWindowId.PROJECT_VIEW).activate(null);
  }

  public FileStatus getFileStatus() {
    return myFile != null ? FileStatusManager.getInstance(getProject()).getStatus(myFile) : FileStatus.NOT_CHANGED;
  }

  protected Icon getElementIcon(final int flags) {
    return Icons.DIRECTORY_CLOSED_ICON;
  }
}

