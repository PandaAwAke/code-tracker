package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenTypeLinks;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.xml.IXmlElementType;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.*;
import com.intellij.psi.impl.source.parsing.xml.XmlParsing;
import com.intellij.psi.impl.source.parsing.xml.XmlPsiLexer;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.xml.*;
import com.intellij.xml.util.XmlUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * @author mike
 */
public class XmlEntityDeclImpl extends XmlElementImpl implements XmlEntityDecl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlEntityDeclImpl");

  public XmlEntityDeclImpl() {
    super(XML_ENTITY_DECL);
  }

  public String getName() {
    for (TreeElement e = firstChild; e != null; e = e.getTreeNext()) {
      if (e instanceof XmlTokenImpl) {
        XmlTokenImpl xmlToken = (XmlTokenImpl)e;

        if (xmlToken.getTokenType() == XmlTokenType.XML_NAME) return xmlToken.getText();
      }
    }

    return "";
  }

  public PsiElement parse(PsiFile baseFile, int context, final XmlEntityRef originalElement) {
    PsiElement dependsOnElement = getValueElement(baseFile);
    String value = null;
    if (dependsOnElement instanceof XmlAttributeValue) {
      XmlAttributeValue attributeValue = (XmlAttributeValue)dependsOnElement;
      value = attributeValue.getValue();
    }
    else if (dependsOnElement instanceof PsiFile) {
      PsiFile file = (PsiFile)dependsOnElement;
      value = file.getText();
    }

    if (value == null) return null;
    final FileElement holderElement = new DummyHolder(originalElement.getManager(), originalElement).getTreeElement();
    char[] buffer = value.toCharArray();
    Lexer lexer = getLexer(context, buffer);
    final ParsingContext parsingContext = new ParsingContext(holderElement.getCharTable());
    final CompositeElement element = Factory.createCompositeElement(XML_ELEMENT_DECL);
    TreeUtil.addChildren(holderElement, element);

    switch (context) {
      default :
        LOG.assertTrue(false, "Entity: " + getName() + " context: " + context);
        return null;

      case CONTEXT_ELEMENT_CONTENT_SPEC:
        {
          parsingContext.getXmlParsing().parseElementContentSpec(element, lexer);
          final PsiElement generated = SourceTreeToPsiMap.treeElementToPsi(element).getFirstChild().getFirstChild();
          setDependsOnElement(generated, dependsOnElement);
          return setOriginalElement(generated, originalElement);
        }

      case CONTEXT_ATTRIBUTE_SPEC:
        {
          parsingContext.getXmlParsing().parseAttributeContentSpec(element, lexer);
          final PsiElement generated = SourceTreeToPsiMap.treeElementToPsi(element).getFirstChild();
          setDependsOnElement(generated, dependsOnElement);
          return setOriginalElement(generated, originalElement);
        }

      case CONTEXT_ATTLIST_SPEC:
        {
          parsingContext.getXmlParsing().parseAttlistContent(element, lexer);
          final PsiElement generated = SourceTreeToPsiMap.treeElementToPsi(element).getFirstChild();
          setDependsOnElement(generated, dependsOnElement);
          return setOriginalElement(generated, originalElement);
        }

      case CONTEXT_ENTITY_DECL_CONTENT:
        {
          parsingContext.getXmlParsing().parseEntityDeclContent(element, lexer);
          final PsiElement generated = SourceTreeToPsiMap.treeElementToPsi(element).getFirstChild();
          setDependsOnElement(generated, dependsOnElement);
          return setOriginalElement(generated, originalElement);
        }

      case CONTEXT_GENERIC_XML:
        {

          Set<String> names = new HashSet<String>();
          {
            // calculating parent names
            PsiElement parent = originalElement;
            while(parent != null){
              if(parent instanceof XmlTag){
                names.add(((XmlTag)parent).getName());
              }
              parent = parent.getParent();
            }
          }
          parsingContext.getXmlParsing().parseGenericXml(lexer, element, names);
          final PsiElement generated = SourceTreeToPsiMap.treeElementToPsi(element).getFirstChild();
          setDependsOnElement(generated, dependsOnElement);
          return setOriginalElement(generated, originalElement);
        }

      case CONTEXT_ENUMERATED_TYPE:
        {
          parsingContext.getXmlParsing().parseEnumeratedTypeContent(element, lexer);
          final PsiElement generated = SourceTreeToPsiMap.treeElementToPsi(element).getFirstChild();
          setDependsOnElement(generated, dependsOnElement);
          return setOriginalElement(generated, originalElement);
        }
    }
  }

  private PsiElement setDependsOnElement(PsiElement generated, PsiElement dependsOnElement) {
    PsiElement e = generated;
    while (e != null) {
      e.putUserData(XmlElement.DEPENDING_ELEMENT, dependsOnElement);
      e = e.getNextSibling();
    }
    return generated;
  }

  private PsiElement setOriginalElement(PsiElement element, PsiElement valueElement) {
    PsiElement e = element;
    while (e != null) {
      e.putUserData(XmlElement.ORIGINAL_ELEMENT, (XmlElement)valueElement);
      e = e.getNextSibling();
    }
    return element;
  }

  private Lexer getLexer(int context, char[] buffer) {
    Lexer lexer = new XmlPsiLexer();
    FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(XmlParsing.XML_WHITE_SPACE_OR_COMMENT_BIT_SET));
    short state = 0;

    switch (context) {
      case CONTEXT_ELEMENT_CONTENT_SPEC:
      case CONTEXT_ATTRIBUTE_SPEC:
      case CONTEXT_ATTLIST_SPEC:
      case CONTEXT_ENUMERATED_TYPE:
      case CONTEXT_ENTITY_DECL_CONTENT:
        {
          state = _OldXmlLexer.DOCTYPE_MARKUP;
          break;
        }

      case CONTEXT_GENERIC_XML: {
        break;
      }


      default: LOG.error("context: " + context);
    }

    filterLexer.start(buffer, 0, buffer.length, state);
    return filterLexer;
  }

  private PsiElement getValueElement(PsiFile baseFile) {
    if (isInternalReference()) {
      for (TreeElement e = firstChild; e != null; e = e.getTreeNext()) {
        if (e.getElementType() == ElementType.XML_ATTRIBUTE_VALUE) {
          XmlAttributeValue value = (XmlAttributeValue)SourceTreeToPsiMap.treeElementToPsi(e);
          return value;
        }
      }
    }
    else {
      for (TreeElement e = lastChild; e != null; e = e.getTreePrev()) {
        if (e.getElementType() == ElementType.XML_ATTRIBUTE_VALUE) {
          XmlAttributeValue value = (XmlAttributeValue)SourceTreeToPsiMap.treeElementToPsi(e);
          final XmlFile xmlFile = XmlUtil.findXmlFile(baseFile, value.getValue());
          if (xmlFile != null) {
            return xmlFile;
          }

          return null;
        }
      }
    }

    return null;
  }

  private boolean isInternalReference() {
    for (TreeElement e = firstChild; e != null; e = e.getTreeNext()) {
      if (e.getElementType() instanceof IXmlElementType) {
        XmlToken token = (XmlToken)SourceTreeToPsiMap.treeElementToPsi(e);
        if (token.getTokenType() == XmlTokenType.XML_DOCTYPE_PUBLIC || token.getTokenType() == XmlToken.XML_DOCTYPE_SYSTEM) {
          return false;
        }
      }
    }

    return true;
  }
}
