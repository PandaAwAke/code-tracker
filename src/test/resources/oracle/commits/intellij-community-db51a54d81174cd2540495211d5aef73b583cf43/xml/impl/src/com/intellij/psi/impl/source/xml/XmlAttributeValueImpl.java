package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.XmlAttributeLiteralEscaper;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mike
 */
public class XmlAttributeValueImpl extends XmlElementImpl implements XmlAttributeValue, PsiLanguageInjectionHost {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlAttributeValueImpl");
  private volatile PsiReference[] myCachedReferences;
  private volatile long myModCount;

  public XmlAttributeValueImpl() {
    super(XmlElementType.XML_ATTRIBUTE_VALUE);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlAttributeValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String getValue() {
    return StringUtil.stripQuotesAroundValue(getText());
  }

  public TextRange getValueTextRange() {
    final TextRange range = getTextRange();
    final String value = getValue();
    if (value.length() == 0) {
      return range; 
    }
    final int start = range.getStartOffset() + getText().indexOf(value);
    final int end = start + value.length();
    return new TextRange(start, end);
  }

  public void clearCaches() {
    super.clearCaches();
    myCachedReferences = null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    PsiReference[] cachedReferences = myCachedReferences;
    final long curModCount = getManager().getModificationTracker().getModificationCount();
    if (cachedReferences != null && myModCount == curModCount) {
      return cachedReferences;
    }
    cachedReferences = ResolveUtil.getReferencesFromProviders(this, XmlAttributeValue.class);
    myCachedReferences = cachedReferences;
    myModCount = curModCount;
    return cachedReferences;
  }

  public PsiReference getReference() {
    final PsiReference[] refs = getReferences();
    if (refs.length > 0) return refs[0];
    return null;
  }


  public int getTextOffset() {
    return getTextRange().getStartOffset() + 1;
  }

  @Nullable
  public List<Pair<PsiElement,TextRange>> getInjectedPsi() {
    PsiElement parent = getParent();
    if (parent instanceof XmlAttributeImpl) {
      return InjectedLanguageUtil.getInjectedPsiFiles(this);
    }
    return null;
  }

  public void fixText(@NotNull String text) {
    try {
      String contents = StringUtil.trimEnd(StringUtil.trimStart(text, "\""), "\"");
      XmlAttribute newAttribute = getManager().getElementFactory().createXmlAttribute("q", contents);
      XmlAttributeValue newValue = newAttribute.getValueElement();

      CheckUtil.checkWritable(this);
      replaceAllChildrenToChildrenOf(newValue.getNode());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public LiteralTextEscaper<XmlAttributeValueImpl> createLiteralTextEscaper() {
    return new XmlAttributeLiteralEscaper(this);
  }
  public void processInjectedPsi(@NotNull InjectedPsiVisitor visitor) {
    InjectedLanguageUtil.enumerate(this, visitor);
  }
}
