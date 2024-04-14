package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.PatchedSoftReference;
import com.intellij.util.PatchedWeakReference;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;

public final class PsiReferenceListImpl extends SlaveRepositoryPsiElement implements PsiReferenceList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiReferenceListImpl");

  private final IElementType myElementType;

  private volatile Reference<PsiClassType[]> myRepositoryTypesRef = null;

  public PsiReferenceListImpl(PsiManagerEx manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
    myElementType = treeElement.getElementType();
  }

  public PsiReferenceListImpl(PsiManagerEx manager, SrcRepositoryPsiElement owner, IElementType elementType) {
    super(manager, owner);
    myElementType = elementType;
  }

  public IElementType getElementType() {
    return myElementType;
  }

  protected Object clone() {
    PsiReferenceListImpl clone = (PsiReferenceListImpl)super.clone();
    clone.myRepositoryTypesRef = null;
    return clone;
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myRepositoryTypesRef = null;
  }

  public void setOwner(SrcRepositoryPsiElement owner) {
    super.setOwner(owner);
    myRepositoryTypesRef = null;
  }

  private static final TokenSet REFERENCE_BIT_SET = TokenSet.create(JAVA_CODE_REFERENCE);

  @NotNull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return calcTreeElement().getChildrenAsPsiElements(REFERENCE_BIT_SET, PSI_REFERENCE_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  @NotNull
  public PsiClassType[] getReferencedTypes() {
    Reference<PsiClassType[]> repositoryTypesRef = myRepositoryTypesRef;
    PsiClassType[] types = repositoryTypesRef == null ? null : repositoryTypesRef.get();
    if (types == null) {
      ASTNode treeElement = getTreeElement();
      final PsiElementFactory factory = getManager().getElementFactory();
      long repositoryId = getRepositoryId();
      if (treeElement == null && repositoryId > 0) {
        RepositoryManager repositoryManager = getRepositoryManager();
        String[] refTexts;
        if (myElementType == JavaElementType.EXTENDS_LIST) {
          refTexts = repositoryManager.getClassView().getExtendsList(repositoryId);
        }
        else if (myElementType == JavaElementType.IMPLEMENTS_LIST) {
          refTexts = repositoryManager.getClassView().getImplementsList(repositoryId);
        }
        else if (myElementType == JavaElementType.THROWS_LIST) {
          refTexts = repositoryManager.getMethodView().getThrowsList(repositoryId);
        }
        else {
          LOG.error("Unknown element type:" + myElementType);
          return null;
        }

        types = new PsiClassType[refTexts.length];
        int nullcount = 0;
        for (int i = 0; i < types.length; i++) {
          final PsiElement parent = getParent();
          PsiElement context = this;
          if (parent instanceof PsiClass) {
            context = ((PsiClassImpl)parent).calcBasesResolveContext(PsiNameHelper.getShortClassName(refTexts[i]), this);
          }

          final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
          final PsiJavaCodeReferenceElementImpl ref = (PsiJavaCodeReferenceElementImpl)Parsing.parseJavaCodeReferenceText(myManager,
                                                                                                                          refTexts[i],
                                                                                                                          holderElement.getCharTable());
          if (ref != null) {
            TreeUtil.addChildren(holderElement, ref);
            ref.setKindWhenDummy(PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND);
            types[i] = factory.createType(ref);
          }
          else {
            types[i] = null;
            nullcount++;
          }
        }

        if (nullcount > 0) {
          PsiClassType[] newtypes = new PsiClassType[types.length - nullcount];
          int cnt = 0;
          for (PsiClassType type : types) {
            if (type != null) newtypes[cnt++] = type;
          }
          types = newtypes;
        }
      }
      else {
        final PsiJavaCodeReferenceElement[] refs = getReferenceElements();
        types = new PsiClassType[refs.length];
        for (int i = 0; i < types.length; i++) {
          types[i] = factory.createType(refs[i]);
        }
      }

      myRepositoryTypesRef = myManager.isBatchFilesProcessingMode()
                             ? new PatchedWeakReference<PsiClassType[]>(types)
                             : new PatchedSoftReference<PsiClassType[]>(types);
    }
    return types;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiReferenceList";
  }
}
