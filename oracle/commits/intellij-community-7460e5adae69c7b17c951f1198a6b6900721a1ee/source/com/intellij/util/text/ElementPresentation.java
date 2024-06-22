package com.intellij.util.text;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;

public abstract class ElementPresentation {
  private final Noun myKind;

  protected ElementPresentation(Noun kind) {
    myKind = kind;
  }

  public static ElementPresentation forElement(PsiElement psiElement) {
    if (psiElement == null || !psiElement.isValid()) return new InvalidPresentation();
    if (psiElement instanceof PsiDirectory) return new ForDirectory(((PsiDirectory)psiElement));
    if (psiElement instanceof PsiFile) return new ForFile(((PsiFile)psiElement));
    if (psiElement instanceof PsiPackage) return new ForPackage(((PsiPackage)psiElement));
    if (psiElement instanceof XmlTag) return new ForXmlTag(((XmlTag)psiElement));
    if (psiElement instanceof PsiAnonymousClass) return new ForAnonymousClass(((PsiAnonymousClass)psiElement));
    if (psiElement instanceof PsiClass) return new ForClass(((PsiClass)psiElement));
    if (psiElement instanceof PsiMethod) return new ForMethod(((PsiMethod)psiElement));
    if (psiElement instanceof PsiField) return new ForField(((PsiField)psiElement));
    return new ForGeneralElement(psiElement);
  }

  public static ElementPresentation forVirtualFile(VirtualFile file) {
    return new ForVirtualFile(file);
  }

  private static boolean validNotNull(VirtualFile virtualFile) {
    return virtualFile != null && virtualFile.isValid();
  }

  public abstract String getQualifiedName();

  public Noun getKind() {
    return myKind;
  }

  public abstract String getName();

  public abstract String getComment();

  public String getNameWithFQComment() {
    String comment = getComment();
    String result = getName();
    if (comment.trim().length() == 0) return result;
    return result + " (" + comment + ")";
  }

  public static class Noun {
    private final String myPlural;
    private final String mySingular;

    public static final Noun DIRECTORY = new Noun("directory", "directories");
    public static final Noun PACKAGE = new Noun("package", "packages");
    public static final Noun FILE = new Noun("file", "files");
    public static final Noun CLASS = new Noun("class", "classes");
    public static final Noun METHOD = new Noun("method", "methods");
    public static final Noun FIELD = new Noun("field", "fields");
    public static final Noun FRAGMENT = new Noun("fragment", "fragments");
    public static final Noun XML_TAG = new Noun("tag", "tags");

    public Noun(String singular, String plural) {
      mySingular = singular;
      myPlural = plural;
    }

    public String getPlural(boolean firstCapitalized) {
      return firstCapitalized(myPlural, firstCapitalized);
    }

    public String getSingular(boolean firstCapitalized) {
      return firstCapitalized(mySingular, firstCapitalized);
    }

    private static String firstCapitalized(String word, boolean firstCapitalized) {
      if (!firstCapitalized) return word;
      char[] chars = word.toCharArray();
      chars[0] = Character.toUpperCase(chars[0]);
      return new String(chars);
    }
  }

  private static class InvalidPresentation extends ElementPresentation {
    public InvalidPresentation() {
      super(new Noun("INVALID", "INVALID"));
    }

    public String getComment() {
      return "";
    }

    public String getName() {
      return "INVALID";
    }

    public String getQualifiedName() {
      return getName();
    }
  }

  private static class ForDirectory extends ElementPresentation {
    private final PsiDirectory myPsiDirectory;

    public ForDirectory(PsiDirectory psiDirectory) {
      super(Noun.DIRECTORY);
      myPsiDirectory = psiDirectory;
    }

    public String getQualifiedName() {
      VirtualFile virtualFile = myPsiDirectory.getVirtualFile();
      if (validNotNull(virtualFile)) return virtualFile.getPresentableUrl();
      return myPsiDirectory.getName();
    }

    public String getName() {
      return myPsiDirectory.getName();
    }

    public String getComment() {
      PsiDirectory parentDirectory = myPsiDirectory.getParentDirectory();
      if (parentDirectory == null) return "";
      return ElementPresentation.forElement(parentDirectory).getQualifiedName();
    }
  }

  private static class ForFile extends ElementPresentation {
    private final PsiFile myFile;

    public ForFile(PsiFile file) {
      super(Noun.FILE);
      myFile = file;
    }

    public String getQualifiedName() {
      VirtualFile virtualFile = myFile.getVirtualFile();
      if (validNotNull(virtualFile)) return virtualFile.getPresentableUrl();
      return myFile.getName();
    }

    public String getName() {
      return myFile.getName();
    }

    public String getComment() {
      PsiDirectory directory = myFile.getContainingDirectory();
      if (directory == null) return "";
      return ElementPresentation.forElement(directory).getQualifiedName();
    }
  }

  public static class ForPackage extends ElementPresentation {
    private final PsiPackage myPsiPackage;

    public ForPackage(PsiPackage psiPackage) {
      super(Noun.PACKAGE);
      myPsiPackage = psiPackage;
    }

    public String getQualifiedName() {
      String qualifiedName = myPsiPackage.getQualifiedName();
      if (qualifiedName.length() == 0) return "<default>";
      return qualifiedName;
    }

    public String getName() {
      return getQualifiedName();
    }

    public String getComment() {
      return "";
    }
  }

  private static class ForAnonymousClass extends ElementPresentation {
    private final PsiAnonymousClass myPsiAnonymousClass;

    public ForAnonymousClass(PsiAnonymousClass psiAnonymousClass) {
      super(Noun.FRAGMENT);
      myPsiAnonymousClass = psiAnonymousClass;
    }

    public String getQualifiedName() {
      PsiClass psiClass = PsiTreeUtil.getParentOfType(myPsiAnonymousClass, PsiClass.class);
      if (psiClass != null) return "Anonymous in " + forElement(psiClass).getQualifiedName();
      return "Anonymous class";
    }

    public String getName() {
      return getQualifiedName();
    }

    public String getComment() {
      return "";
    }
  }

  private static class ForClass extends ElementPresentation {
    private final PsiClass myPsiClass;

    public ForClass(PsiClass psiClass) {
      super(Noun.CLASS);
      myPsiClass = psiClass;
    }

    public String getQualifiedName() {
      return myPsiClass.getQualifiedName();
    }

    public String getName() {
      return myPsiClass.getName();
    }

    public String getComment() {
      PsiPackage psiPackage = myPsiClass.getContainingFile().getContainingDirectory().getPackage();
      if (psiPackage == null) return "";
      return forElement(psiPackage).getQualifiedName();
    }
  }

  private static class ForMethod extends ElementPresentation {
    private static final int FQ_OPTIONS = PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_FQ_NAME | PsiFormatUtil.SHOW_NAME |
                                          PsiFormatUtil.SHOW_PARAMETERS;
    private static final int NAME_OPTIONS = PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS;
    private final PsiMethod myPsiMethod;

    public ForMethod(PsiMethod psiMethod) {
      super(Noun.METHOD);
      myPsiMethod = psiMethod;
    }

    public String getQualifiedName() {
      return PsiFormatUtil.formatMethod(myPsiMethod, PsiSubstitutor.EMPTY, FQ_OPTIONS, PsiFormatUtil.SHOW_TYPE);
    }

    public String getName() {
      return PsiFormatUtil.formatMethod(myPsiMethod, PsiSubstitutor.EMPTY, NAME_OPTIONS, PsiFormatUtil.SHOW_TYPE);
    }

    public String getComment() {
      PsiClass containingClass = myPsiMethod.getContainingClass();
      if (containingClass == null) return "";
      return forElement(containingClass).getComment();
    }
  }

  private static class ForField extends ElementPresentation {
    private final PsiField myPsiField;

    public ForField(PsiField psiField) {
      super(Noun.FIELD);
      myPsiField = psiField;
    }

    public String getQualifiedName() {
      PsiClass psiClass = myPsiField.getContainingClass();
      String name = myPsiField.getName();
      if (psiClass != null) return forElement(psiClass).getQualifiedName() + "." + name;
      else return name;
    }

    public String getName() {
      PsiClass psiClass = myPsiField.getContainingClass();
      String name = myPsiField.getName();
      if (psiClass == null) return name;
      return forElement(psiClass).getName() + "." + name;
    }

    public String getComment() {
      PsiClass psiClass = myPsiField.getContainingClass();
      if (psiClass == null) return "";
      return forElement(psiClass).getComment();
    }
  }

  private static class ForGeneralElement extends ElementPresentation {
    private final PsiElement myPsiElement;

    public ForGeneralElement(PsiElement psiElement) {
      super(Noun.FRAGMENT);
      myPsiElement = psiElement;
    }

    public String getQualifiedName() {
      PsiFile containingFile = myPsiElement.getContainingFile();
      if (containingFile != null) return "Code from " + forElement(containingFile).getQualifiedName();
      return "Code";
    }

    public String getName() {
      return getQualifiedName();
    }

    public String getComment() {
      return "";
    }
  }

  private static class ForXmlTag extends ElementPresentation {
    private final XmlTag myXmlTag;

    public ForXmlTag(XmlTag xmlTag) {
      super(Noun.XML_TAG);
      myXmlTag = xmlTag;
    }

    public String getQualifiedName() {
      return "<" + myXmlTag.getLocalName() + ">";
    }

    public String getName() {
      return getQualifiedName();
    }

    public String getComment() {
      return "";
    }
  }

  private static class ForVirtualFile extends ElementPresentation {
    private final VirtualFile myFile;

    public ForVirtualFile(VirtualFile file) {
      super(file.isDirectory() ? Noun.DIRECTORY : Noun.FILE);
      myFile = file;
    }

    public String getComment() {
      String name = myFile.getName();
      if (!myFile.isValid()) return name;
      VirtualFile parent = myFile.getParent();
      if (parent == null) return name;
      return parent.getPresentableUrl();
    }

    public String getName() {
      return myFile.getName();
    }

    public String getQualifiedName() {
      if (!myFile.isValid()) return myFile.getName();
      return myFile.getPresentableUrl();
    }
  }
}
