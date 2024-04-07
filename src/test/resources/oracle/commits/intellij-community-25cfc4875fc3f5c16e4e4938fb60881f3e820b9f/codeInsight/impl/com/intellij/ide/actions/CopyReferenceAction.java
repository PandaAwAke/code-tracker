/**
 * @author Alexey
 */
package com.intellij.ide.actions;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LogicalRoot;
import com.intellij.util.LogicalRootsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class CopyReferenceAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CopyReferenceAction");

  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    boolean enabled = isEnabled(dataContext);
    e.getPresentation().setEnabled(enabled);
  }

  private static boolean isEnabled(final DataContext dataContext) {
    Editor editor = DataKeys.EDITOR.getData(dataContext);
    PsiElement element = getElementToCopy(editor, dataContext);
    PsiElement member = getMember(element);
    return member != null;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = DataKeys.EDITOR.getData(dataContext);
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    PsiElement element = getElementToCopy(editor, dataContext);

    PsiElement member = getMember(element);
    if (member == null) return;

    doCopy(member, project);
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    if (editor != null) {
      PsiElement toHighlight = HighlightUsagesHandler.getNameIdentifier(element);
      if (toHighlight == null) toHighlight = element;
      highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{toHighlight}, attributes, true, null);
    }
  }

  @Nullable
  private static PsiElement getElementToCopy(final Editor editor, final DataContext dataContext) {
    PsiElement element = null;
    if (editor != null) {
      PsiReference reference = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());
      if (reference != null) {
        element = reference.getElement();
      }
    }

    if (element == null) {
      element = DataKeys.PSI_ELEMENT.getData(dataContext);
    }
    if (element != null && !(element instanceof PsiMember) && element.getParent() instanceof PsiMember) {
      element = element.getParent();
    }
    return element;
  }

  @Nullable
  private static PsiElement getMember(final PsiElement element) {
    if (element instanceof PsiMember || element instanceof PsiFile) return element;
    if (element instanceof PsiReference) {
      PsiElement resolved = ((PsiReference)element).resolve();
      if (resolved instanceof PsiMember) return resolved;
    }
    if (!(element instanceof PsiIdentifier)) return null;
    final PsiElement parent = element.getParent();
    PsiMember member = null;
    if (parent instanceof PsiJavaCodeReferenceElement) {
      PsiElement resolved = ((PsiJavaCodeReferenceElement)parent).resolve();
      if (resolved instanceof PsiMember) {
        member = (PsiMember)resolved;
      }
    }
    else if (parent instanceof PsiMember) {
      member = (PsiMember)parent;
    }
    else {
      //todo show error
      //return;
    }
    return member;
  }

  public static void doCopy(final PsiElement element, final Project project) {
    String fqn = elementToFqn(element);

    CopyPasteManager.getInstance().setContents(new MyTransferable(fqn));

    final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(project);
    statusBar.setInfo(IdeBundle.message("message.reference.to.fqn.has.been.copied", fqn));
  }

  private static void insert(final String fqn, final PsiMember element, final Editor editor) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(editor.getProject());
    documentManager.commitDocument(editor.getDocument());
    final PsiFile file = documentManager.getPsiFile(editor.getDocument());
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;

    final Project project = editor.getProject();
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              Document document = editor.getDocument();
              documentManager.doPostponedOperationsAndUnblockDocument(document);
              documentManager.commitDocument(document);
              EditorModificationUtil.deleteSelectedText(editor);
              doInsert(fqn, element, editor, project);
            }
            catch (IncorrectOperationException e1) {
              LOG.error(e1);
            }
          }
        });
      }
    }, IdeBundle.message("command.pasting.reference"), null);
  }

  private static void doInsert(String fqn,
                               PsiMember targetElement,
                               final Editor editor,
                               final Project project) throws IncorrectOperationException {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = editor.getDocument();

    final PsiFile file = documentManager.getPsiFile(document);

    final int offset = editor.getCaretModel().getOffset();
    PsiElement elementAtCaret = file.findElementAt(offset);
    if (elementAtCaret == null) return;

    fqn = fqn.replace('#', '.');
    String toInsert;
    String suffix = "";
    PsiElement elementToInsert = targetElement;
    if (targetElement instanceof PsiMethod && PsiUtil.isInsideJavadocComment(elementAtCaret)) {
      // use fqn#methodName(ParamType)
      PsiMethod method = (PsiMethod)targetElement;
      PsiClass aClass = method.getContainingClass();
      String className = aClass == null ? "" : aClass.getQualifiedName();
      toInsert = className == null ? "" : className;
      if (toInsert.length() != 0) toInsert += "#";
      toInsert += method.getName() + "(";
      PsiParameter[] parameters = method.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        if (i != 0) toInsert += ", ";
        toInsert += parameter.getType().getCanonicalText();
      }
      toInsert += ")";
    }
    else if (targetElement == null ||
             PsiTreeUtil.getNonStrictParentOfType(elementAtCaret, PsiLiteralExpression.class, PsiComment.class) != null ||
             PsiTreeUtil.getNonStrictParentOfType(elementAtCaret, PsiJavaFile.class) == null) {
      toInsert = fqn;
    }
    else {
      toInsert = targetElement.getName();
      if (targetElement instanceof PsiMethod) {
        suffix = "()";
        if (((PsiMethod)targetElement).isConstructor()) {
          targetElement = targetElement.getContainingClass();
        }
      }
      else if (targetElement instanceof PsiClass && isAfterNew(file, elementAtCaret)) {
        // pasting reference to default constructor of the class after new
        suffix = "()";
      }
      final PsiElementFactory factory = PsiManager.getInstance(project).getElementFactory();
      final PsiExpression expression = factory.createExpressionFromText(toInsert + suffix, elementAtCaret);
      final PsiReferenceExpression referenceExpression = expression instanceof PsiMethodCallExpression
                                                         ? ((PsiMethodCallExpression)expression).getMethodExpression()
                                                         : expression instanceof PsiReferenceExpression
                                                           ? (PsiReferenceExpression)expression
                                                           : null;
      if (referenceExpression == null) {
        toInsert = fqn;
      }
      else if (referenceExpression.advancedResolve(true).getElement() != targetElement) {
        try {
          referenceExpression.bindToElement(targetElement);
        }
        catch (IncorrectOperationException e) {
          // failed to bind
        }
        if (referenceExpression.advancedResolve(true).getElement() != targetElement) {
          toInsert = fqn;
        }
      }
    }
    if (toInsert == null) toInsert = "";

    document.insertString(offset, toInsert+suffix);
    documentManager.commitAllDocuments();
    int endOffset = offset + toInsert.length() + suffix.length();
    RangeMarker rangeMarker = document.createRangeMarker(endOffset, endOffset);
    elementAtCaret = file.findElementAt(offset);

    if (elementAtCaret != null && elementAtCaret.isValid()) {
      shortenReference(elementAtCaret, targetElement);
    }
    CodeInsightUtil.forcePsiPostprocessAndRestoreElement(file);
    CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);

    int caretOffset = rangeMarker.getEndOffset();
    if (elementToInsert instanceof PsiMethod && ((PsiMethod)elementToInsert).getParameterList().getParametersCount() != 0 && StringUtil.endsWithChar(suffix,')')) {
      caretOffset --;
    }
    editor.getCaretModel().moveToOffset(caretOffset);
  }

  private static boolean isAfterNew(PsiFile file, PsiElement elementAtCaret) {
    PsiElement prevSibling = elementAtCaret.getPrevSibling();
    if (prevSibling == null) return false;
    int offset = prevSibling.getTextRange().getStartOffset();
    PsiElement prevElement = file.findElementAt(offset);
    return PsiTreeUtil.getParentOfType(prevElement, PsiNewExpression.class) != null;
  }

  private static void shortenReference(PsiElement element, PsiMember elementToInsert) throws IncorrectOperationException {
    while (element.getParent() instanceof PsiJavaCodeReferenceElement) {
      element = element.getParent();
      if (element == null) return;
    }
    if (element instanceof PsiJavaCodeReferenceElement && elementToInsert != null) {
      try {
        element = ((PsiJavaCodeReferenceElement)element).bindToElement(elementToInsert);
      }
      catch (IncorrectOperationException e) {
        // failed to bind
      }
    }
    final JavaCodeStyleManager codeStyleManagerEx = JavaCodeStyleManager.getInstance(element.getProject());
    codeStyleManagerEx.shortenClassReferences(element, JavaCodeStyleManager.UNCOMPLETE_CODE);
  }

  public static class MyPasteProvider implements PasteProvider {
    public void performPaste(DataContext dataContext) {
      final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      final Editor editor = DataKeys.EDITOR.getData(dataContext);
      if (project == null || editor == null) return;

      final String fqn = getCopiedFqn();
      PsiMember element = fqnToElement(project, fqn);
      insert(fqn, element, editor);
    }

    public boolean isPastePossible(DataContext dataContext) {
      return isPasteEnabled(dataContext);
    }

    public boolean isPasteEnabled(DataContext dataContext) {
      final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      final Editor editor = DataKeys.EDITOR.getData(dataContext);
      return project != null && editor != null && getCopiedFqn() != null;
    }
  }

  @Nullable
  private static String getCopiedFqn() {
    final Transferable contents = CopyPasteManager.getInstance().getContents();
    if (contents == null) return null;
    try {
      return (String)contents.getTransferData(OUR_DATA_FLAVOR);
    }
    catch (UnsupportedFlavorException e) {
    }
    catch (IOException e) {
    }
    return null;
  }

  private static final DataFlavor OUR_DATA_FLAVOR;
  static {
    try {
      //noinspection HardCodedStringLiteral
      OUR_DATA_FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + MyTransferable.class.getName());
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private static class MyTransferable implements Transferable {
    private final String fqn;

    public MyTransferable(String fqn) {
      this.fqn = fqn;
    }

    public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[]{OUR_DATA_FLAVOR, DataFlavor.stringFlavor};
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return OUR_DATA_FLAVOR.equals(flavor) || DataFlavor.stringFlavor.equals(flavor);
    }

    @Nullable
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
      if (!isDataFlavorSupported(flavor)) return null;
      return fqn;
    }
  }

  @Nullable
  private static String elementToFqn(final PsiElement element) {
    final String fqn;
    if (element instanceof PsiClass) {
      fqn = ((PsiClass)element).getQualifiedName();
    }
    else if (element instanceof PsiMember) {
      final PsiMember member = (PsiMember)element;
      fqn = member.getContainingClass().getQualifiedName() + "#" + member.getName();
    }
    else if (element instanceof PsiFile) {
      final PsiFile file = (PsiFile)element;
      fqn = FileUtil.toSystemIndependentName(getFileFqn(file));
    }
    else {
      fqn = element.getClass().getName();
    }
    return fqn;
  }

  @NotNull
  private static String getFileFqn(final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return file.getName();
    }
    final Project project = file.getProject();
    final LogicalRoot logicalRoot = LogicalRootsManager.getLogicalRootsManager(project).findLogicalRoot(virtualFile);
    if (logicalRoot != null) {
      return "/"+FileUtil.getRelativePath(VfsUtil.virtualToIoFile(logicalRoot.getVirtualFile()), VfsUtil.virtualToIoFile(virtualFile));
    }

    final VirtualFile contentRoot = ProjectRootManager.getInstance(project).getFileIndex().getContentRootForFile(virtualFile);
    if (contentRoot != null) {
      return "/"+FileUtil.getRelativePath(VfsUtil.virtualToIoFile(contentRoot), VfsUtil.virtualToIoFile(virtualFile));
    }
    return virtualFile.getPath();
  }

  @Nullable
  private static PsiMember fqnToElement(final Project project, final String fqn) {
    PsiClass aClass = PsiManager.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
    if (aClass != null) {
      return aClass;
    }
    final int endIndex = fqn.indexOf('#');
    if (endIndex == -1) return null;
    String className = fqn.substring(0, endIndex);
    if (className == null) return null;
    aClass = PsiManager.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
    if (aClass == null) return null;
    String memberName = fqn.substring(endIndex + 1);
    PsiField field = aClass.findFieldByName(memberName, false);
    if (field != null) {
      return field;
    }
    PsiMethod[] methods = aClass.findMethodsByName(memberName, false);
    if (methods.length == 0) return null;
    return methods[0];
  }
}
