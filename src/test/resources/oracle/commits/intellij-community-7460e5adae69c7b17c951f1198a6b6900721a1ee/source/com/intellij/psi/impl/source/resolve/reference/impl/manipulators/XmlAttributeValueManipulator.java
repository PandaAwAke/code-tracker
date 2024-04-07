package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.reference.ElementManipulator;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 06.01.2004
 * Time: 20:00:23
 * To change this template use Options | File Templates.
 */
public class XmlAttributeValueManipulator implements ElementManipulator{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.manipulators.XmlAttributeValueManipulator");
  public PsiElement handleContentChange(PsiElement element, TextRange range, String newContent) throws IncorrectOperationException{
    final CompositeElement compositeElement = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(element);
    final PsiElement nextSibling = element.getFirstChild().getNextSibling();

    CheckUtil.checkWritable(element);
    String text;
    try {
      text = element.getText();
      String textBeforeRange = text.substring(1, range.getStartOffset());
      String textAfterRange = text.substring(range.getEndOffset(), text.length() - 1);
      text = textBeforeRange + newContent + textAfterRange;
    } catch(StringIndexOutOfBoundsException e) {
      LOG.error("Range: " + range + " in text: '" + element.getText() + "'", e);
      throw e;
    }
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(compositeElement);
    final LeafElement newValueElement = Factory.createSingleLeafElement(
      XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
      text.toCharArray(),
      0,
      text.length(), charTableByTree, element.getManager());


    compositeElement.replaceChildInternal(SourceTreeToPsiMap.psiElementToTree(nextSibling), newValueElement);
    return element;
    //return ((XmlAttributeValueImpl) element).replaceRangeInText(range, newContent);
  }
}
