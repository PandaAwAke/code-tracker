package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 22.05.2003
 * Time: 13:46:54
 * To change this template use Options | File Templates.
 */
public class GenerateDTDAction extends BaseCodeInsightAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.actions.GenerateDTDAction");
  protected CodeInsightActionHandler getHandler(){
    return new CodeInsightActionHandler(){
      public void invoke(Project project, Editor editor, PsiFile file){
        if(file instanceof XmlFile && file.getVirtualFile() != null && file.getVirtualFile().isWritable()){
          final @NonNls StringBuffer buffer = new StringBuffer();
          final XmlDocument document = ((XmlFile) file).getDocument();
          if(document.getRootTag() != null){
            buffer.append("<!DOCTYPE " + document.getRootTag().getName() + " [\n");
            buffer.append(XmlUtil.generateDocumentDTD(document));
            buffer.append("]>\n");
            XmlFile tempFile = null;
            try{
              tempFile = (XmlFile) file.getManager().getElementFactory().createFileFromText("dummy.xml", buffer.toString());
              document.getProlog().replace(tempFile.getDocument().getProlog());
            }
            catch(IncorrectOperationException e){
              LOG.error(e);
            }
          }
        }
      }

      public boolean startInWriteAction(){
        return true;
      }
    };
  }

  public void update(AnActionEvent event) {
    super.update(event);

    final DataContext dataContext = event.getDataContext();
    final Presentation presentation = event.getPresentation();
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);

    final boolean visible;
    if (editor != null && project != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      visible = file instanceof XmlFile;
    }
    else {
      visible = false;
    }

    presentation.setVisible(visible);
  }

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file){
    return file instanceof XmlFile;
  }
}
