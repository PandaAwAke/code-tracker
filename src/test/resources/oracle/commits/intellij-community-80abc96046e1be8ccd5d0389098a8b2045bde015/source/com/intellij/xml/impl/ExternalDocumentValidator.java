package com.intellij.xml.impl;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.reference.SoftReference;
import com.intellij.xml.actions.ValidateXmlActionHandler;
import org.xml.sax.SAXParseException;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 21.01.2005
 * Time: 0:07:51
 * To change this template use File | Settings | File Templates.
 */
public class ExternalDocumentValidator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.impl.ExternalDocumentValidator");
  private static final Key<SoftReference<ExternalDocumentValidator>> validatorInstanceKey = Key.create("validatorInstance");
  private ValidateXmlActionHandler myHandler;
  private Validator.ValidationHost myHost;

  private long myModificationStamp;
  private PsiFile myFile;

  private static class ValidationInfo {
    PsiElement element;
    String message;
    int type;
  }

  private WeakReference<List<ValidationInfo>> myInfos; // last jaxp validation result

  private void runJaxpValidation(final XmlElement element, Validator.ValidationHost host) {
    PsiFile file = element.getContainingFile();

    if (myFile == file &&
        file != null &&
        myModificationStamp == file.getModificationStamp() &&
        myInfos!=null &&
        myInfos.get()!=null // we have validated before
        ) {
      addAllInfos(host,myInfos.get());
      return;
    }

    if (myHandler==null)  myHandler = new ValidateXmlActionHandler(false);
    final Project project = element.getProject();

    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document==null) return;
    final List<ValidationInfo> results = new LinkedList<ValidationInfo>();

    myHost = new Validator.ValidationHost() {
      public void addMessage(PsiElement context, String message, int type) {
        final ValidationInfo o = new ValidationInfo();

        results.add(o);
        o.element = context;
        o.message = message;
        o.type = type;
      }
    };

    myHandler.setErrorReporter(myHandler.new ErrorReporter() {
      public boolean isStopOnUndeclaredResource() {
        return true;
      }

      public boolean filterValidationException(Exception ex) {
        if (ex instanceof ProcessCanceledException &&
            ApplicationManager.getApplication().isUnitTestMode()
           ) {
          return true;
        }
        
        return super.filterValidationException(ex);
      }

      public void processError(final SAXParseException e, final boolean warning) {
        try {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              if (e.getPublicId() != null) {
                return;
              }
              
              if (document.getLineCount() < e.getLineNumber() || e.getLineNumber() <= 0) {
                return;
              }

              int offset = Math.max(0, document.getLineStartOffset(e.getLineNumber() - 1) + e.getColumnNumber() - 2);
              if (offset >= document.getTextLength()) return;
              PsiElement currentElement = PsiDocumentManager.getInstance(project).getPsiFile(document).findElementAt(offset);
              PsiElement originalElement = currentElement;
              final String elementText = currentElement.getText();

              if (elementText.equals("</")) {
                currentElement = currentElement.getNextSibling();
              }
              else if (elementText.equals(">") || elementText.equals("=")) {
                currentElement = currentElement.getPrevSibling();
              }

              // Cannot find the declaration of element
              String localizedMessage = e.getLocalizedMessage();
              localizedMessage = localizedMessage.substring(localizedMessage.indexOf(':') + 1).trim();

              if (localizedMessage.startsWith("Cannot find the declaration of element") ||
                  localizedMessage.startsWith("Element") ||
                  localizedMessage.startsWith("Document root element") ||
                  localizedMessage.startsWith("The content of element type")
                  ) {
                addProblemToTagName(currentElement, originalElement, localizedMessage, warning);
                //return;
              } else if (localizedMessage.startsWith("Value ")) {
                addProblemToTagName(currentElement, originalElement, localizedMessage, warning);
              } else if (localizedMessage.startsWith("Attribute ")) {
                currentElement = PsiTreeUtil.getParentOfType(currentElement,XmlAttribute.class,false);
                final int messagePrefixLength = "Attribute ".length();

                if (currentElement==null && localizedMessage.charAt(messagePrefixLength) == '"') {
                  // extract the attribute name from message and get it from tag!
                  final int nextQuoteIndex = localizedMessage.indexOf('"', messagePrefixLength + 1);
                  String attrName = nextQuoteIndex == -1 ? null : localizedMessage.substring(messagePrefixLength + 1, nextQuoteIndex);

                  XmlTag parent = PsiTreeUtil.getParentOfType(originalElement,XmlTag.class);
                  currentElement = parent.getAttribute(attrName,null);

                  if (currentElement!=null) {
                    currentElement = SourceTreeToPsiMap.treeElementToPsi(
                      XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(
                        SourceTreeToPsiMap.psiElementToTree(currentElement)
                      )
                    );
                  }
                }

                if (currentElement!=null) {
                  assertValidElement(currentElement, originalElement,localizedMessage);
                  myHost.addMessage(currentElement,localizedMessage,warning ? Validator.ValidationHost.WARNING:Validator.ValidationHost.ERROR);
                } else {
                  addProblemToTagName(originalElement, originalElement, localizedMessage, warning);
                }
              } else if (localizedMessage.startsWith("The string")) {
                if (currentElement != null) {
                  myHost.addMessage(currentElement,localizedMessage,Validator.ValidationHost.WARNING);
                }
              }
              else {
                currentElement = getNodeForMessage(currentElement);
                assertValidElement(currentElement, originalElement,localizedMessage);
                if (currentElement!=null) {
                  myHost.addMessage(currentElement,localizedMessage,warning ? Validator.ValidationHost.WARNING:Validator.ValidationHost.ERROR);
                }
              }
            }
          });
        }
        catch (Exception ex) {
          if (ex instanceof ProcessCanceledException) throw (ProcessCanceledException)ex;
          LOG.error(ex);
        }
      }

    });

    myHandler.doValidate(project, element.getContainingFile());

    myFile = file;
    myModificationStamp = myFile == null ? 0 : myFile.getModificationStamp();
    myInfos = new WeakReference<List<ValidationInfo>>(results);

    addAllInfos(host,results);
  }

  private static final Class<? extends XmlElement>[] parentClasses = new Class[]{
    XmlTag.class,
    XmlProcessingInstruction.class,
    XmlElementDecl.class,
    XmlMarkupDecl.class,
    XmlEntityRef.class,
    XmlDoctype.class
  };

  private static PsiElement getNodeForMessage(final PsiElement currentElement) {
    PsiElement parentOfType = PsiTreeUtil.getParentOfType(
        currentElement,
        parentClasses,
        false
    );
    
    if (parentOfType == null) {
      if (currentElement instanceof XmlToken) {
        parentOfType = currentElement.getParent();
      }
      else {
        parentOfType = currentElement;
      }
    }
    return parentOfType;
  }

  private static void addAllInfos(Validator.ValidationHost host,List<ValidationInfo> highlightInfos) {
    for (ValidationInfo info : highlightInfos) {
      host.addMessage(info.element, info.message, info.type);
    }
  }

  private PsiElement addProblemToTagName(PsiElement currentElement,
                                     final PsiElement originalElement,
                                     final String localizedMessage,
                                     final boolean warning) {
    currentElement = PsiTreeUtil.getParentOfType(currentElement,XmlTag.class,false);
    if (currentElement==null) {
      currentElement = PsiTreeUtil.getParentOfType(originalElement,XmlElementDecl.class,false);
    }
    assertValidElement(currentElement, originalElement,localizedMessage);

    if (currentElement!=null) {
      myHost.addMessage(currentElement,localizedMessage,warning ? Validator.ValidationHost.WARNING:Validator.ValidationHost.ERROR);
    }

    return currentElement;
  }

  private static void assertValidElement(PsiElement currentElement, PsiElement originalElement, String message) {
    if (currentElement==null) {
      XmlTag tag = PsiTreeUtil.getParentOfType(originalElement, XmlTag.class);
      LOG.assertTrue(
        false,
        "The validator message:"+ message+ " is bound to null node,\n" +
        "initial element:"+originalElement.getText()+",\n"+
        "parent:" + originalElement.getParent()+",\n" +
        "tag:" + (tag != null? tag.getText():"null") + ",\n" +
        "offset in tag: " + (originalElement.getTextOffset() - (tag == null ? 0 : tag.getTextOffset()))
      );
    }
  }

  public static void doValidation(final PsiElement context, final Validator.ValidationHost host) {
    final PsiFile containingFile = context.getContainingFile();
    if (containingFile==null || containingFile.getFileType() != StdFileTypes.XML) return;
    final Project project = context.getProject();
    SoftReference<ExternalDocumentValidator> validatorReference = project.getUserData(validatorInstanceKey);
    ExternalDocumentValidator validator = validatorReference != null? validatorReference.get() : null;

    if(validator == null) {
      validator = new ExternalDocumentValidator();
      project.putUserData(validatorInstanceKey,new SoftReference<ExternalDocumentValidator>(validator));
    }

    validator.runJaxpValidation((XmlElement)context,host);
  }

  
}
