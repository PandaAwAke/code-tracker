/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.javadoc.JavaDocInfoComponent;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.LightweightHint;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public class ImplementationViewComponent extends JPanel {
  private final PsiElement[] myElements;
  private int myIndex;

  private Editor myEditor;
  private JPanel myViewingPanel;
  private JLabel myLocationLabel;
  private JLabel myCountLabel;
  private JComboBox myFileChooser;
  private CardLayout myBinarySwitch;
  private JPanel myBinaryPanel;
  private FileEditor myNonTextEditor;
  private FileEditorProvider myCurrentNonTextEditorProvider;
  private LightweightHint myHint;

  public void setHint(final LightweightHint hint) {
    myHint = hint;
  }

  private static class FileDescriptor {
    public VirtualFile myFile;

    public FileDescriptor(VirtualFile file) {
      myFile = file;
    }
  }

  public ImplementationViewComponent(PsiElement[] elements) {
    super(new BorderLayout());
    myElements = new PsiElement[elements.length];
    myIndex = 0;

    FileDescriptor[] files = new FileDescriptor[myElements.length];
    for (int i = 0; i < elements.length; i++) {
      myElements[i] = elements[i].getNavigationElement();
      files[i] = new FileDescriptor(getContainingFile(myElements[i]).getVirtualFile());
    }

    final Project project = myElements[0].getProject();
    EditorFactory factory = EditorFactory.getInstance();
    Document doc = factory.createDocument("");
    doc.setReadOnly(true);
    myEditor = factory.createEditor(doc, project);
    PsiFile psiFile = getContainingFile(myElements[0]);
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(project, psiFile.getName());
    ((EditorEx)myEditor).setHighlighter(highlighter);
    ((EditorEx)myEditor).setBackgroundColor(EditorFragmentComponent.getBackgroundColor(myEditor));

    myEditor.getSettings().setAdditionalLinesCount(1);
    myEditor.getSettings().setAdditionalColumnsCount(1);
    myEditor.getSettings().setLineMarkerAreaShown(false);
    myEditor.getSettings().setLineNumbersShown(false);
    myEditor.getSettings().setFoldingOutlineShown(false);

    myBinarySwitch = new CardLayout();
    myViewingPanel = new JPanel(myBinarySwitch);
    myViewingPanel.add(myEditor.getComponent(), "Text");

    myBinaryPanel = new JPanel(new BorderLayout());
    myViewingPanel.add(myBinaryPanel, "Binary");

    add(myViewingPanel, BorderLayout.CENTER);

    final ActionToolbar toolbar = createToolbar();
    myLocationLabel = new JLabel();
    myCountLabel = new JLabel();

    JPanel header = new JPanel(new BorderLayout());
    header.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    JPanel toolbarPanel = new JPanel(new FlowLayout());
    toolbarPanel.add(toolbar.getComponent());


    if (myElements.length > 1) {
      myFileChooser = new JComboBox(files);
      myFileChooser.setRenderer(new DefaultListCellRenderer() {
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          VirtualFile file = ((FileDescriptor)value).myFile;
          setIcon(file.getIcon());
          setForeground(FileStatusManager.getInstance(project).getStatus(file).getColor());
          setText(file.getPresentableName());
          return this;
        }
      });

      myFileChooser.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          int index = myFileChooser.getSelectedIndex();
          if (myIndex != index) {
            myIndex = index;
            updateControls();
          }
        }
      });

      toolbarPanel.add(myFileChooser);
      toolbarPanel.add(myCountLabel);
    }
    else {
      final JLabel label = new JLabel();
      VirtualFile file = psiFile.getVirtualFile();
      label.setIcon(file.getIcon());
      label.setForeground(FileStatusManager.getInstance(project).getStatus(file).getColor());
      label.setText(file.getPresentableName());
      label.setBorder(new CompoundBorder(IdeBorderFactory.createBorder(), IdeBorderFactory.createEmptyBorder(0, 0, 0, 5)));
      toolbarPanel.add(label);
    }


    header.add(toolbarPanel, BorderLayout.WEST);
    header.add(myLocationLabel, BorderLayout.EAST);

    add(header, BorderLayout.NORTH);
    setPreferredSize(new Dimension(600, 400));

    updateControls();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (myFileChooser != null) {
          myFileChooser.requestFocus();
        }
        else {
          myViewingPanel.requestFocus();
        }
      }
    });
  }

  private void updateControls() {
    updateLabels();
    updateCombo();
    updateEditorText();
  }

  private void updateCombo() {
    if (myFileChooser != null) {
      myFileChooser.setSelectedIndex(myIndex);
    }
  }

  private void updateEditorText() {
    disposeNonTextEditor();

    final PsiElement elt = myElements[myIndex];
    Project project = elt.getProject();
    PsiFile psiFile = getContainingFile(elt);
    final VirtualFile vFile = psiFile.getVirtualFile();
    final FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(project, vFile);
    for (FileEditorProvider provider : providers) {
      if (provider instanceof TextEditorProvider) {
        updateTextElement(elt);
        myBinarySwitch.show(myViewingPanel, "Text");
        break;
      }
      else if (provider.accept(project, vFile)) {
        myCurrentNonTextEditorProvider = provider;
        myNonTextEditor = myCurrentNonTextEditorProvider.createEditor(project, vFile);
        myBinaryPanel.removeAll();
        myBinaryPanel.add(myNonTextEditor.getComponent());
        myBinarySwitch.show(myViewingPanel, "Binary");
        break;
      }
    }
  }

  private void disposeNonTextEditor() {
    if (myNonTextEditor != null) {
      myCurrentNonTextEditorProvider.disposeEditor(myNonTextEditor);
      myNonTextEditor = null;
      myCurrentNonTextEditorProvider = null;
    }
  }

  private void updateTextElement(final PsiElement elt) {
    Project project = elt.getProject();
    PsiFile psiFile = getContainingFile(elt);

    final Document doc = PsiDocumentManager.getInstance(project).getDocument(psiFile);

    final HintUtil.ImplementationTextSelectioner implementationTextSelectioner =
      HintUtil.getImplementationTextSelectioner(psiFile.getFileType());
    int start = implementationTextSelectioner.getTextStartOffset(elt);
    final int end = implementationTextSelectioner.getTextEndOffset(elt);

    final int lineStart = doc.getLineStartOffset(doc.getLineNumber(start));
    final int lineEnd = doc.getLineEndOffset(doc.getLineNumber(end));

    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            Document fragmentDoc = myEditor.getDocument();
            fragmentDoc.setReadOnly(false);
            fragmentDoc.replaceString(0, fragmentDoc.getTextLength(), doc.getCharsSequence().subSequence(lineStart, lineEnd).toString());
            fragmentDoc.setReadOnly(true);
            myEditor.getCaretModel().moveToOffset(0);
            myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          }
        });
      }
    });
  }

  private PsiFile getContainingFile(final PsiElement elt) {
    PsiFile psiFile = elt.getContainingFile();
    if (psiFile == null) return null;
    PsiFile originalFile = psiFile.getOriginalFile();
    if (originalFile != null) psiFile = originalFile;
    return psiFile;
  }

  public void removeNotify() {
    super.removeNotify();
    EditorFactory.getInstance().releaseEditor(myEditor);
    disposeNonTextEditor();
  }

  private void updateLabels() {
    //TODO: Move from JavaDoc to somewhere more appropriate place.
    JavaDocInfoComponent.customizeElementLabel(myElements[myIndex], myLocationLabel);
    myCountLabel.setText("" + (myIndex + 1) + " of " + myElements.length);
  }

  private ActionToolbar createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();

    BackAction back = new BackAction();
    back.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)), this);
    group.add(back);

    ForwardAction forward = new ForwardAction();
    forward.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)), this);
    group.add(forward);

    EditSourceActionBase edit = new EditSourceAction();
    edit.registerCustomShortcutSet(CommonShortcuts.getEditSource(), this);
    edit.registerCustomShortcutSet(CommonShortcuts.ENTER, this);
    group.add(edit);

    edit = new ShowSourceAction();
    edit.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, this);
    group.add(edit);

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
  }

  private void goBack() {
    myIndex--;
    updateControls();
  }

  private void goForward() {
    myIndex++;
    updateControls();
  }

  private class BackAction extends AnAction implements HintManager.ActionToIgnore {
    public BackAction() {
      super("Back", null, IconLoader.getIcon("/actions/back.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      goBack();
    }


    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myIndex > 0);
    }
  }

  private class ForwardAction extends AnAction implements HintManager.ActionToIgnore {
    public ForwardAction() {
      super("Forward", null, IconLoader.getIcon("/actions/forward.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      goForward();
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myIndex < myElements.length - 1);
    }
  }

  private class EditSourceAction extends EditSourceActionBase {
    public EditSourceAction() {
      super(true, IconLoader.getIcon("/actions/editSource.png"), "Edit Source");
    }

    @Override public void actionPerformed(AnActionEvent e) {
      super.actionPerformed(e);
      if (myHint.isVisible()) {
        myHint.hide();
      }
    }
  }

  private class ShowSourceAction extends EditSourceActionBase implements HintManager.ActionToIgnore {
    public ShowSourceAction() {
      super(false, IconLoader.getIcon("/actions/showSource.png"), "Show Source");
    }

    @Override public void actionPerformed(AnActionEvent e) {
      super.actionPerformed(e);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myHint.getComponent().requestFocusInWindow();
        }
      });
    }
  }

  private class EditSourceActionBase extends AnAction {
    private boolean myFocusEditor;

    public EditSourceActionBase(boolean focusEditor, Icon icon, String text) {
      super(text, null, icon);
      myFocusEditor = focusEditor;
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myFileChooser == null || !myFileChooser.isPopupVisible());
    }

    public void actionPerformed(AnActionEvent e) {
      PsiElement element = myElements[myIndex];
      PsiElement navigationElement = element.getNavigationElement();
      PsiFile file = getContainingFile(navigationElement);
      Project project = element.getProject();
      FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file.getVirtualFile(), navigationElement.getTextOffset());
      if (myFocusEditor) {
        fileEditorManager.openTextEditor(descriptor, true);
      }
      else {
        fileEditorManager.openTextEditorEnsureNoFocus(descriptor);
      }
    }
  }
}
