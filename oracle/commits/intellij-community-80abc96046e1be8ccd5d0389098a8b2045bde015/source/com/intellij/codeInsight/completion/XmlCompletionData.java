package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.MacroFactory;
import com.intellij.codeInsight.TailType;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.getters.XmlAttributeValueGetter;
import com.intellij.psi.filters.getters.AllWordsGetter;
import com.intellij.psi.filters.position.LeftNeighbour;
import com.intellij.psi.filters.position.TokenTypeFilter;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.ComplexTypeDescriptor;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.06.2003
 * Time: 18:55:15
 * To change this template use Options | File Templates.
 */
public class XmlCompletionData extends CompletionData {
  public XmlCompletionData(){
    declareFinalScope(XmlTag.class);
    declareFinalScope(XmlAttribute.class);
    declareFinalScope(XmlAttributeValue.class);

    {
      final CompletionVariant variant = new CompletionVariant(createTagCompletionFilter());
      variant.includeScopeClass(XmlTag.class);
      variant.addCompletionFilterOnElement(TrueFilter.INSTANCE);
      variant.setInsertHandler(new XmlTagInsertHandler());
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(createAttributeCompletion());
      variant.includeScopeClass(XmlAttribute.class);
      variant.addCompletionFilterOnElement(TrueFilter.INSTANCE);
      variant.setInsertHandler(new XmlAttributeInsertHandler());
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(createAttributeValueCompletionFilter());
      variant.includeScopeClass(XmlAttributeValue.class);
      variant.addCompletion(new XmlAttributeValueGetter());
      variant.addCompletionFilter(TrueFilter.INSTANCE, TailType.NONE);
      variant.setInsertHandler(new XmlAttributeValueInsertHandler());
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(new TokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS));
      variant.includeScopeClass(XmlToken.class, true);
      variant.addCompletion(new SimpleTagContentEnumerationValuesGetter(),TailType.NONE);

      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(
        new AndFilter(
          new LeftNeighbour(new TextFilter("&")),
          new OrFilter(
            new TokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS),
            new TokenTypeFilter(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN)
          )
        )
      );
      variant.includeScopeClass(XmlToken.class, true);
      variant.addCompletion(new EntityRefGetter());
      variant.setInsertHandler(new EntityRefInsertHandler());
      registerVariant(variant);
    }
  }

  protected ElementFilter createAttributeCompletion() {
    return TrueFilter.INSTANCE;
  }

  protected ElementFilter createAttributeValueCompletionFilter() {
    return TrueFilter.INSTANCE;
  }

  protected ElementFilter createTagCompletionFilter() {
    return TrueFilter.INSTANCE;
  }

  private static class XmlAttributeValueInsertHandler extends DefaultInsertHandler{
    public void handleInsert(CompletionContext context,
                             int startOffset,
                             LookupData data,
                             LookupItem item,
                             boolean signatureSelected,
                             char completionChar) {
      super.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);
      final PsiElement current = context.file.findElementAt(context.startOffset);
      final String text = current.getText();
      final CaretModel caretModel = context.editor.getCaretModel();
      int localOffset = caretModel.getOffset() - current.getTextRange().getStartOffset() - 1;
      startOffset = localOffset;
      while(localOffset > 0){
        final char cur = text.charAt(localOffset--);
        if(cur == '}') break;
        if(cur == '{'){
          if(localOffset >= 0 && text.charAt(localOffset) == '$'){
            if (startOffset >= text.length() - 1 || text.charAt(startOffset + 1) != '}') {
              context.editor.getDocument().insertString(caretModel.getOffset(), "}");
            }
            caretModel.moveToOffset(caretModel.getOffset() + 1);
          }
          break;
        }
      }
    }
  }

  private static class XmlAttributeInsertHandler extends BasicInsertHandler {
    public XmlAttributeInsertHandler() {
    }

    public void handleInsert(
      CompletionContext context,
      int startOffset,
      LookupData data,
      LookupItem item,
      boolean signatureSelected, char completionChar) {
      super.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);

      final Editor editor = context.editor;

      final Document document = editor.getDocument();
      final int caretOffset = editor.getCaretModel().getOffset();
      if (PsiDocumentManager.getInstance(editor.getProject()).getPsiFile(document).getFileType() == StdFileTypes.HTML &&
          HtmlUtil.isSingleHtmlAttribute((String)item.getObject())
          ) {
        return;
      }

      final CharSequence chars = document.getCharsSequence();
      if (!CharArrayUtil.regionMatches(chars, caretOffset, "=\"") &&
          !CharArrayUtil.regionMatches(chars, caretOffset, "='")) {
        if("/> \n\t\r".indexOf(document.getCharsSequence().charAt(caretOffset)) < 0) document.insertString(caretOffset, "=\"\" ");
        else document.insertString(caretOffset, "=\"\"");
      }

      editor.getCaretModel().moveToOffset(caretOffset + 2);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
    }
  }

  private static class XmlTagInsertHandler extends BasicInsertHandler {
    public XmlTagInsertHandler() {}

    public void handleInsert(
        CompletionContext context,
        int startOffset,
        LookupData data,
        LookupItem item,
        boolean signatureSelected, char completionChar) {
      super.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);
      Project project = context.project;
      Editor editor = context.editor;
      // Need to insert " " to prevent creating tags like <tagThis is my text
      editor.getDocument().insertString(editor.getCaretModel().getOffset(), " ");
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      PsiElement current = context.file.findElementAt(context.startOffset);
      editor.getDocument().deleteString(editor.getCaretModel().getOffset(), editor.getCaretModel().getOffset() + 1);
      final XmlTag tag = PsiTreeUtil.getContextOfType(current, XmlTag.class, true);

      if (tag == null) return;

      final XmlElementDescriptor descriptor = tag.getDescriptor();

      if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) == null &&
          XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) == null) {

        Template t = TemplateManager.getInstance(project).getActiveTemplate(editor);
        if (t == null && descriptor != null) {
          insertIncompleteTag(completionChar, editor, project, descriptor, tag);
        }
      }
      else if (completionChar == Lookup.REPLACE_SELECT_CHAR) {
        PsiDocumentManager.getInstance(project).commitAllDocuments();

        int caretOffset = editor.getCaretModel().getOffset();

        PsiElement otherTag = PsiTreeUtil.getParentOfType(context.file.findElementAt(caretOffset), getTagClass());

        PsiElement endTagStart = getEndTagStart(otherTag);

        if (endTagStart != null) {
          PsiElement sibling = endTagStart.getNextSibling();

          if (isTagNameToken(sibling)) {
            int sOffset = sibling.getTextRange().getStartOffset();
            int eOffset = sibling.getTextRange().getEndOffset();

            editor.getDocument().deleteString(sOffset, eOffset);
            editor.getDocument().insertString(sOffset, getTagText(otherTag));
          }
        }

        editor.getCaretModel().moveToOffset(caretOffset + 1);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().removeSelection();
      }
      current = context.file.findElementAt(context.startOffset);
      if(current != null && current.getPrevSibling() instanceof XmlToken){
        if(!isClosed(current) && ((XmlToken)current.getPrevSibling()).getTokenType() == XmlTokenType.XML_END_TAG_START){
          editor.getDocument().insertString(current.getTextRange().getEndOffset(), ">");
          editor.getCaretModel().moveToOffset(editor.getCaretModel().getOffset() + 1);
        }
      }
    }

    private boolean isClosed(PsiElement current) {
      PsiElement e = current;

      while (e != null) {
        if (e instanceof XmlToken) {
          XmlToken token = (XmlToken)e;
          if (token.getTokenType() == XmlTokenType.XML_TAG_END) return true;
          if (token.getTokenType() == XmlTokenType.XML_EMPTY_ELEMENT_END) return true;
        }

        e = e.getNextSibling();
      }

      return false;
    }

    private void insertIncompleteTag(char completionChar, Editor editor, Project project, XmlElementDescriptor descriptor, XmlTag tag) {
      TemplateManager templateManager = TemplateManager.getInstance(project);
      Template template = templateManager.createTemplate("", "");
      template.setToReformat(true);
      template.setToIndent(true);

      // temp code
      FileType fileType = tag.getContainingFile().getFileType();
      boolean htmlCode = fileType==StdFileTypes.HTML || fileType==StdFileTypes.XHTML;

      XmlAttributeDescriptor[] attributes = descriptor.getAttributesDescriptors();

      for (XmlAttributeDescriptor attributeDecl : attributes) {
        String attributeName = attributeDecl.getName(tag);

        if (attributeDecl.isRequired()) {
          template.addTextSegment(" " + attributeName + "=\"");
          Expression expression = new MacroCallNode(MacroFactory.createMacro("complete"));
          template.addVariable(attributeName, expression, expression, true);
          template.addTextSegment("\"");
        }
        else if (attributeDecl.isFixed() && attributeDecl.getDefaultValue() != null && !htmlCode) {
          template.addTextSegment(" " + attributeName + "=\"" + attributeDecl.getDefaultValue() + "\"");
        }
      }

      if (completionChar == '>') {
        template.addTextSegment(">");
        template.addEndVariable();

        if ( !(tag instanceof HtmlTag) ||
            !HtmlUtil.isSingleHtmlTag(tag.getName())
           ) {
          template.addTextSegment("</");
          template.addTextSegment(descriptor.getName(tag));
          template.addTextSegment(">");
        }
      }
      else if (completionChar == '/') {
        template.addTextSegment("/>");
      }

      templateManager.startTemplate(editor, template);
    }

    private Class getTagClass() {
      return XmlTag.class;
    }

    private PsiElement getEndTagStart(PsiElement tag) {
      return XmlUtil.getTokenOfType(tag, XmlTokenType.XML_END_TAG_START);
    }

    private String getTagText(PsiElement tag) {
      return ((XmlTag)tag).getName();
    }

    private boolean isTagNameToken(PsiElement token) {
      return token.getNode().getElementType() == XmlTokenType.XML_NAME;
    }
  }

  private static class SimpleTagContentEnumerationValuesGetter implements ContextGetter {
    public Object[] get(final PsiElement context, CompletionContext completionContext) {
      XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);
      XmlElementDescriptor descriptor = tag != null ? tag.getDescriptor() : null;

      if (descriptor instanceof XmlElementDescriptorImpl) {
        final TypeDescriptor type = ((XmlElementDescriptorImpl)descriptor).getType();

        if (type instanceof ComplexTypeDescriptor) {
          final XmlTag[] simpleContent = new XmlTag[1];

          XmlUtil.processXmlElements(((ComplexTypeDescriptor)type).getDeclaration(),new PsiElementProcessor() {
            public boolean execute(final PsiElement element) {
              if (element instanceof XmlTag &&
                ((XmlTag)element).getLocalName().equals("simpleContent") &&
                ((XmlTag)element).getNamespace().equals(XmlUtil.XML_SCHEMA_URI)
                ) {
                simpleContent[0] = (XmlTag)element;
                return false;
              }

              return true;
            }
          }, true);

          if (simpleContent[0]!=null) {
            final HashSet<String> variants = new HashSet<String>();
            XmlUtil.collectEnumerationValues(simpleContent[0],variants);
            if (variants.size() > 0) return variants.toArray(new Object[variants.size()]);
          }
        }
      }

      return new AllWordsGetter().get(context, completionContext);
    }
  }

  private static class EntityRefGetter implements ContextGetter {
    public Object[] get(final PsiElement context, CompletionContext completionContext) {
      final XmlTag parentOfType = PsiTreeUtil.getParentOfType(context, XmlTag.class);
      if (parentOfType != null) {
        final XmlElementDescriptor descriptor = parentOfType.getDescriptor();
        final List<String> results = new ArrayList<String>();

        if (descriptor != null) {
          final XmlFile descriptorFile = descriptor.getNSDescriptor().getDescriptorFile();

          // skip content of embedded dtd, its content will be inserted by word completion
          if (descriptorFile != null && !descriptorFile.equals(parentOfType.getContainingFile())) {
            final PsiElementProcessor processor = new PsiElementProcessor() {
              public boolean execute(final PsiElement element) {
                if (element instanceof XmlEntityDecl) {
                  final XmlEntityDecl xmlEntityDecl = (XmlEntityDecl)element;
                  if (xmlEntityDecl.isInternalReference()) results.add(xmlEntityDecl.getName());
                }
                return true;
              }
            };
  
            XmlUtil.processXmlElements(
              descriptorFile,
              processor,
              true
            );
  
            return results.toArray(new Object[results.size()]);
          }
        }
      }
      return new Object[0];
    }
  }

  private static class EntityRefInsertHandler extends BasicInsertHandler {
    public void handleInsert(CompletionContext context,
                             int startOffset,
                             LookupData data,
                             LookupItem item,
                             boolean signatureSelected,
                             char completionChar) {
      super.handleInsert(context, startOffset, data, item, signatureSelected,
                         completionChar);

      final CaretModel caretModel = context.editor.getCaretModel();
      context.editor.getDocument().insertString(caretModel.getOffset(), ";");
      caretModel.moveToOffset(caretModel.getOffset() + 1);
    }
  }
}
