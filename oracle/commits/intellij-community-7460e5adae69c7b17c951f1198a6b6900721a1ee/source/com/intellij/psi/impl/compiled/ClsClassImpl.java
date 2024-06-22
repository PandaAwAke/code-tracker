package com.intellij.psi.impl.compiled;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.PomMemberOwner;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.ClassView;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.cls.BytePointer;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.cls.ClsUtil;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.*;

public class ClsClassImpl extends ClsRepositoryPsiElement implements PsiClass, ClsModifierListOwner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsClassImpl");

  private ClassFileData myClassFileData; // it's null when repository id exists

  // these fields are not used when has repository id
  private PsiElement myParent;
  private PsiField[] myFields = null;
  private PsiMethod[] myMethods = null;
  private PsiMethod[] myConstructors = null;
  private PsiClass[] myInnerClasses = null;

  private Map<String, PsiField> myCachedFieldsMap = null;
  private Map<String, PsiMethod[]> myCachedMethodsMap = null;
  private Map<String, PsiClass> myCachedInnersMap = null;


  private String myQualifiedName = null;
  private String myName = null;
  private PsiIdentifier myNameIdentifier = null;
  private ClsModifierListImpl myModifierList = null;
  private PsiReferenceList myExtendsList = null;
  private PsiReferenceList myImplementsList = null;
  private PsiTypeParameterList myTypeParameters = null;
  private Boolean myIsDeprecated = null;
  private PsiDocComment myDocComment = null;
  private ClsAnnotationImpl[] myAnnotations = null;

  public ClsClassImpl(PsiManagerImpl manager, ClsElementImpl parent, VirtualFile file) {
    super(manager, -1);
    myParent = parent;
    myClassFileData = new ClassFileData(file);
  }

  public ClsClassImpl(PsiManagerImpl manager, long repositoryId) {
    super(manager, repositoryId);
    myClassFileData = null;
    myParent = null;
  }

  void invalidate() {
    myParent = null;
  }

  boolean isContentsLoaded() {
    return myClassFileData != null;
  }

  ClassFileData getClassFileData() {
    VirtualFile vFile = getContainingFile().getVirtualFile();
    if (vFile != null) {
      LOG.assertTrue(!myManager.isAssertOnFileLoading(vFile));
    }
    return myClassFileData;
  }

  public void setRepositoryId(long repositoryId) {
    synchronized (PsiLock.LOCK) {
      super.setRepositoryId(repositoryId);
      if (repositoryId >= 0) {
        myClassFileData = null;
      }
      ;
    }
  }

  public PsiDirectory getContainingPackage() {
    return getContainingFile().getContainingDirectory();
  }

  public PsiElement getParent() {
    if (myParent == null) {
      long repositoryId = getRepositoryId();
      if (repositoryId >= 0) {
        long parentId = getRepositoryManager().getClassView().getParent(repositoryId);
        myParent = getRepositoryElementsManager().findOrCreatePsiElementById(parentId);
      }
    }
    return myParent;
  }

  public PsiFile getContainingFile() {
    PsiElement parent = getParent();
    if (parent == null) return null;
    return parent.getContainingFile();
  }

  public PsiElement[] getChildren() {
    PsiIdentifier name = getNameIdentifier();
    PsiDocComment docComment = getDocComment();
    PsiModifierList modifierList = getModifierList();
    PsiReferenceList extendsList = getExtendsList();
    PsiReferenceList implementsList = getImplementsList();
    PsiField[] fields = getFields();
    PsiMethod[] methods = getMethods();
    PsiClass[] classes = getInnerClasses();

    int count =
      (docComment != null ? 1 : 0)
      + (modifierList != null ? 1 : 0)
      + (name != null ? 1 : 0)
      + (extendsList != null ? 1 : 0)
      + (implementsList != null ? 1 : 0)
      + fields.length
      + methods.length
      + classes.length;
    PsiElement[] children = new PsiElement[count];

    int offset = 0;
    if (docComment != null) {
      children[offset++] = docComment;
    }
    if (modifierList != null) {
      children[offset++] = modifierList;
    }
    if (name != null) {
      children[offset++] = name;
    }
    if (extendsList != null) {
      children[offset++] = extendsList;
    }
    if (implementsList != null) {
      children[offset++] = implementsList;
    }
    System.arraycopy(fields, 0, children, offset, fields.length);
    offset += fields.length;
    System.arraycopy(methods, 0, children, offset, methods.length);
    offset += methods.length;
    System.arraycopy(classes, 0, children, offset, classes.length);
    /*offset += classes.length;*/

    return children;
  }

  public PsiIdentifier getNameIdentifier() {
    synchronized (PsiLock.LOCK) {
      if (myNameIdentifier == null) {
        String qName = getQualifiedName();
        String name = PsiNameHelper.getShortClassName(qName);
        if (name.length() == 0) {
          name = "_";
        }
        myNameIdentifier = new ClsIdentifierImpl(this, name);
      }
      ;
    }
    return myNameIdentifier;
  }

  public String getName() {
    if (myName == null) {
      String qName = getQualifiedName();
      myName = PsiNameHelper.getShortClassName(qName);
    }
    return myName;
  }

  public PsiTypeParameterList getTypeParameterList() {
    synchronized (PsiLock.LOCK) {
      if (myTypeParameters == null) {
        long repositoryId = getRepositoryId();
        if (repositoryId < 0) {
          if (!parseViaGenericSignature()) {
            myTypeParameters = new ClsTypeParametersListImpl(this, new ClsTypeParameterImpl[0]);
          }
        }
        else {
          ClassView classView = getRepositoryManager().getClassView();
          int count = classView.getParametersListSize(repositoryId);
          if (count == 0) {
            myTypeParameters = new ClsTypeParametersListImpl(this, new ClsTypeParameterImpl[0]);
          }
          else {
            StringBuffer compiledParams = new StringBuffer();
            compiledParams.append('<');
            for (int i = 0; i < count; i++) {
              compiledParams.append(classView.getParameterText(repositoryId, i));
            }
            compiledParams.append('>');
            try {
              final String signature = compiledParams.toString();
              myTypeParameters =
              GenericSignatureParsing.parseTypeParametersDeclaration(new StringCharacterIterator(signature, 0), this, signature);
            }
            catch (ClsFormatException e) {
              LOG.error(e); // dsl: this should not happen
            }
          }
        }
      }
      return myTypeParameters;
    }
  }

  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  public String getQualifiedName() {
    synchronized (PsiLock.LOCK) {
      if (myQualifiedName == null) {
        long repositoryId = getRepositoryId();
        if (repositoryId < 0) {
          try {
            ClassFileData classFileData = getClassFileData();
            BytePointer ptr = new BytePointer(classFileData.getData(), classFileData.getConstantPoolEnd() + 2);
            ptr.offset = classFileData.getOffsetInConstantPool(ClsUtil.readU2(ptr));
            int tag = ClsUtil.readU1(ptr);
            if (tag != ClsUtil.CONSTANT_Class) {
              throw new ClsFormatException();
            }
            ptr.offset = classFileData.getOffsetInConstantPool(ClsUtil.readU2(ptr));
            String className = ClsUtil.readUtf8Info(ptr, '/', '.');
            myQualifiedName = ClsUtil.convertClassName(className, false);
          }
          catch (ClsFormatException e) {
            myQualifiedName = "";
          }
        }
        else {
          myQualifiedName = getRepositoryManager().getClassView().getQualifiedName(repositoryId);
          if (myQualifiedName == null) {
            myQualifiedName = "";
          }
        }
      }
      ;
    }
    return myQualifiedName;
  }

  public PsiModifierList getModifierList() {
    synchronized (PsiLock.LOCK) {
      if (myModifierList == null) {
        int flags = getAccessFlags();
        myModifierList = new ClsModifierListImpl(this, flags);
      }
      return myModifierList;
    }
  }

  public boolean hasModifierProperty(String name) {
    return getModifierList().hasModifierProperty(name);
  }

  public PsiReferenceList getExtendsList() {
    synchronized (PsiLock.LOCK) {
      if (myExtendsList == null) {
        long repositoryId = getRepositoryId();
        if (repositoryId < 0) {
          try {
            if (parseViaGenericSignature()) return myExtendsList;
            if (!isInterface()) {
              myExtendsList = buildSuperList(PsiKeyword.EXTENDS);
            }
            else {
              myExtendsList = buildInterfaceList(PsiKeyword.EXTENDS);
            }
          }
          catch (ClsFormatException e) {
            myExtendsList = new ClsReferenceListImpl(this, PsiJavaCodeReferenceElement.EMPTY_ARRAY, PsiKeyword.EXTENDS);
          }
        }
        else {
          ClassView classView = getRepositoryManager().getClassView();
          String[] refTexts = classView.getExtendsList(repositoryId);
          ClsJavaCodeReferenceElementImpl[] refs = new ClsJavaCodeReferenceElementImpl[refTexts.length];
          for (int i = 0; i < refTexts.length; i++) {
            refs[i] = new ClsJavaCodeReferenceElementImpl(null, refTexts[i]);
          }
          myExtendsList = new ClsReferenceListImpl(this, refs, PsiKeyword.EXTENDS);
          for (int i = 0; i < refs.length; i++) {
            ClsJavaCodeReferenceElementImpl ref = refs[i];
            ref.setParent(myExtendsList);
          }
        }
      }
      ;
    }
    return myExtendsList;
  }

  private boolean parseViaGenericSignature() {
    try {
      String signature = getSignatureAttribute();
      if (signature == null) return false;

      CharacterIterator iterator = new StringCharacterIterator(signature, 0);
      myTypeParameters = GenericSignatureParsing.parseTypeParametersDeclaration(iterator, this, signature);

      PsiJavaCodeReferenceElement[] supers = GenericSignatureParsing.parseToplevelClassRefSignatures(iterator, this);

      if (!isInterface()) {
        if (supers.length > 0 && !supers[0].getCanonicalText().equals("java.lang.Object")) {
          myExtendsList = new ClsReferenceListImpl(this, new PsiJavaCodeReferenceElement[]{supers[0]}, PsiKeyword.EXTENDS);
        }
        else {
          myExtendsList = new ClsReferenceListImpl(this, PsiJavaCodeReferenceElement.EMPTY_ARRAY, PsiKeyword.EXTENDS);
        }

        PsiJavaCodeReferenceElement[] interfaces = buildInterfaces(supers);
        myImplementsList = new ClsReferenceListImpl(this, interfaces, PsiKeyword.IMPLEMENTS);
      }
      else {
        myImplementsList = new ClsReferenceListImpl(this, PsiJavaCodeReferenceElement.EMPTY_ARRAY, PsiKeyword.IMPLEMENTS);
        if (supers.length == 0 || supers[0].getCanonicalText().equals("java.lang.Object")) {
          supers = buildInterfaces(supers);
        }
        myExtendsList = new ClsReferenceListImpl(this, supers, PsiKeyword.EXTENDS);
      }
    }
    catch (ClsFormatException e) {
      return false;
    }

    return true;
  }

  private static PsiJavaCodeReferenceElement[] buildInterfaces(PsiJavaCodeReferenceElement[] supers) {
    PsiJavaCodeReferenceElement[] interfaces;
    if (supers.length > 0) {
      interfaces = new PsiJavaCodeReferenceElement[supers.length - 1];
      for (int i = 1; i < supers.length; i++) {
        interfaces[i - 1] = supers[i];
      }
    }
    else {
      interfaces = PsiJavaCodeReferenceElement.EMPTY_ARRAY;
    }
    return interfaces;
  }

  public PsiReferenceList getImplementsList() {
    synchronized (PsiLock.LOCK) {
      if (myImplementsList == null) {
        if (!isInterface()) {
          long repositoryId = getRepositoryId();
          if (repositoryId < 0) {
            try {
              if (parseViaGenericSignature()) return myImplementsList;
              myImplementsList = buildInterfaceList(PsiKeyword.IMPLEMENTS);
            }
            catch (ClsFormatException e) {
              myImplementsList = new ClsReferenceListImpl(this, PsiJavaCodeReferenceElement.EMPTY_ARRAY, PsiKeyword.IMPLEMENTS);
            }
          }
          else {
            ClassView classView = getRepositoryManager().getClassView();
            String[] refTexts = classView.getImplementsList(repositoryId);
            ClsJavaCodeReferenceElementImpl[] refs = new ClsJavaCodeReferenceElementImpl[refTexts.length];
            for (int i = 0; i < refTexts.length; i++) {
              refs[i] = new ClsJavaCodeReferenceElementImpl(null, refTexts[i]);
            }
            myImplementsList = new ClsReferenceListImpl(this, refs, PsiKeyword.IMPLEMENTS);
            for (int i = 0; i < refs.length; i++) {
              ClsJavaCodeReferenceElementImpl ref = refs[i];
              ref.setParent(myImplementsList);
            }
          }
        }
        else {
          myImplementsList = new ClsReferenceListImpl(this, PsiJavaCodeReferenceElement.EMPTY_ARRAY, PsiKeyword.IMPLEMENTS);
        }
      }
      ;
    }
    return myImplementsList;
  }

  public PsiClassType[] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  public PsiClassType[] getImplementsListTypes() {
    return PsiClassImplUtil.getImplementsListTypes(this);
  }

  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  public PsiClass[] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  public PsiClass[] getSupers() {
    return PsiClassImplUtil.getSupers(this);
  }

  public PsiClassType[] getSuperTypes() {
    return PsiClassImplUtil.getSuperTypes(this);
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? ((PsiClass)parent) : null;
  }

  private PsiReferenceList buildSuperList(String type) throws ClsFormatException {
    ClassFileData classFileData = getClassFileData();
    int offset = classFileData.getConstantPoolEnd() + 4;
    if (offset + 2 > myClassFileData.data.length) {
      throw new ClsFormatException();
    }
    int b1 = myClassFileData.data[offset++] & 0xFF;
    int b2 = myClassFileData.data[offset/*++*/] & 0xFF;
    int index = (b1 << 8) + b2;
    ClsJavaCodeReferenceElementImpl ref = index != 0 ? classFileData.buildReference(index) : null;
    if (ref != null && "java.lang.Object".equals(ref.getCanonicalText())) {
      ref = null;
    }
    PsiReferenceList list = new ClsReferenceListImpl(this,
                                                     ref != null
                                                     ? new PsiJavaCodeReferenceElement[]{ref}
                                                     : PsiJavaCodeReferenceElement.EMPTY_ARRAY,
                                                     type);
    if (ref != null) {
      ref.setParent(list);
    }
    return list;
  }

  private PsiReferenceList buildInterfaceList(String type) throws ClsFormatException {
    ClassFileData classFileData = getClassFileData();
    int offset = classFileData.getConstantPoolEnd() + 6;
    byte[] data = classFileData.getData();
    if (offset + 2 > data.length) {
      throw new ClsFormatException();
    }
    int b1 = data[offset++] & 0xFF;
    int b2 = data[offset++] & 0xFF;
    int count = (b1 << 8) + b2;
    if (offset + count * 2 > data.length) {
      throw new ClsFormatException();
    }
    ClsJavaCodeReferenceElementImpl[] refs = new ClsJavaCodeReferenceElementImpl[count];
    for (int i = 0; i < count; i++) {
      b1 = data[offset++] & 0xFF;
      b2 = data[offset++] & 0xFF;
      int index = (b1 << 8) + b2;
      refs[i] = classFileData.buildReference(index);
    }
    PsiReferenceList list = new ClsReferenceListImpl(this, refs, type);
    for (int i = 0; i < count; i++) {
      refs[i].setParent(list);
    }
    return list;
  }

  public PsiField[] getFields() {
    synchronized (PsiLock.LOCK) {
      if (myFields == null) {
        long repositoryId = getRepositoryId();
        if (repositoryId < 0) {
          try {
            ClassFileData classFileData = getClassFileData();
            BytePointer ptr = new BytePointer(classFileData.getData(), classFileData.getConstantPoolEnd() + 6);
            int count = ClsUtil.readU2(ptr);
            ptr.offset += count * 2; // skip interfaces
            count = ClsUtil.readU2(ptr);
            ArrayList<PsiField> array = new ArrayList<PsiField>();
            for (int i = 0; i < count; i++) {
              PsiField field;
              if (isEnumField(ptr.offset)) {
                field = new ClsEnumConstantImpl(this, ptr.offset);
              }
              else {
                field = new ClsFieldImpl(this, ptr.offset);
              }
              String name = field.getName();
              //if (name.indexOf('$') < 0 && name.indexOf('<') < 0){ // skip synthetic fields
              if (myManager.getNameHelper().isIdentifier(name) && name.indexOf('$') < 0) { // skip synthetic&obfuscated fields
                array.add(field);
              }
              ptr.offset += 6;
              ClsUtil.skipAttributes(ptr);
            }
            myFields = array.toArray(new PsiField[array.size()]);
          }
          catch (ClsFormatException e) {
            myFields = PsiField.EMPTY_ARRAY;
          }
        }
        else {
          long[] fieldIds = getRepositoryManager().getClassView().getFields(repositoryId);
          PsiField[] fields = new PsiField[fieldIds.length];
          RepositoryElementsManager repositoryElementsManager = getRepositoryElementsManager();
          for (int i = 0; i < fieldIds.length; i++) {
            long id = fieldIds[i];
            fields[i] = (PsiField)repositoryElementsManager.findOrCreatePsiElementById(id);
          }
          myFields = fields;
        }
      }
      ;
    }
    return myFields;
  }

  private boolean isEnumField(int ptrOffset) {
    int flags = 0;
    try {
      int offset = ptrOffset;
      byte[] data = getClassFileData().getData();
      if (offset + 2 > data.length) {
        throw new ClsFormatException();
      }
      int b1 = data[offset++] & 0xFF;
      int b2 = data[offset] & 0xFF;
      flags = (b1 << 8) + b2;
    }
    catch (ClsFormatException e) {}

    return (flags & ClsUtil.ACC_ENUM) != 0;
  }

  public PsiMethod[] getMethods() {
    synchronized (PsiLock.LOCK) {
      if (myMethods == null) {
        long repositoryId = getRepositoryId();

        if (repositoryId < 0) {
          try {
            ClassFileData classFileData = getClassFileData();
            BytePointer ptr = new BytePointer(classFileData.getData(), classFileData.getConstantPoolEnd() + 6);
            int count = ClsUtil.readU2(ptr);
            ptr.offset += count * 2; // skip interfaces
            count = ClsUtil.readU2(ptr);
            for (int i = 0; i < count; i++) { // skip fields
              ptr.offset += 6;
              ClsUtil.skipAttributes(ptr);
            }
            count = ClsUtil.readU2(ptr);
            ArrayList<PsiMethod> array = new ArrayList<PsiMethod>();
            for (int i = 0; i < count; i++) {
              ClsMethodImpl method = new ClsMethodImpl(this, ptr.offset);
              String name = method.getName();
              //if (name.indexOf('$') < 0 && name.indexOf('<') < 0){ // skip synthetic methods
              if (!method.isBridge()) { //skip bridge methods
                if (myManager.getNameHelper().isIdentifier(name) && name.indexOf('$') < 0) { // skip synthetic&obfuscated methods
                  array.add(method);
                }
              }
              ptr.offset += 6;
              ClsUtil.skipAttributes(ptr);
            }
            myMethods = array.toArray(new PsiMethod[array.size()]);
          }
          catch (ClsFormatException e) {
            myMethods = PsiMethod.EMPTY_ARRAY;
          }
        }
        else {
          long[] methodIds = getRepositoryManager().getClassView().getMethods(repositoryId);
          PsiMethod[] methods = new PsiMethod[methodIds.length];
          RepositoryElementsManager repositoryElementsManager = getRepositoryElementsManager();
          for (int i = 0; i < methodIds.length; i++) {
            long id = methodIds[i];
            methods[i] = (PsiMethod)repositoryElementsManager.findOrCreatePsiElementById(id);
          }
          myMethods = methods;
        }
      }
      ;
    }
    return myMethods;
  }

  public PsiMethod[] getConstructors() {
    if (myConstructors == null){
      myConstructors = PsiImplUtil.getConstructors(this);
    }
    return myConstructors;
  }

  public PsiClass[] getInnerClasses() {
    synchronized (PsiLock.LOCK) {
      if (myInnerClasses == null) {
        long repositoryId = getRepositoryId();
        if (repositoryId < 0) {
          VirtualFile vFile = myClassFileData.vFile;
          VirtualFile parentFile = vFile.getParent();
          if (parentFile == null) return null;
          String name = vFile.getNameWithoutExtension();
          String prefix = name + "$";
          ArrayList<PsiClass> array = new ArrayList<PsiClass>();
          VirtualFile[] children = parentFile.getChildren();
          for (int i = 0; i < children.length; i++) {
            VirtualFile child = children[i];
            String childName = child.getNameWithoutExtension();
            if (childName.startsWith(prefix)) {
              String innerName = childName.substring(prefix.length());
              if (innerName.indexOf('$') >= 0) continue;
              if (!myManager.getNameHelper().isIdentifier(innerName)) continue;
              PsiClass aClass = new ClsClassImpl(myManager, this, child);
              array.add(aClass);
            }
          }
          myInnerClasses = array.toArray(new PsiClass[array.size()]);
        }
        else {
          long[] classIds = getRepositoryManager().getClassView().getInnerClasses(repositoryId);
          PsiClass[] classes = new PsiClass[classIds.length];
          RepositoryElementsManager repositoryElementsManager = getRepositoryElementsManager();
          for (int i = 0; i < classIds.length; i++) {
            long id = classIds[i];
            classes[i] = (PsiClass)repositoryElementsManager.findOrCreatePsiElementById(id);
          }
          myInnerClasses = classes;
        }
      }
      ;
    }
    return myInnerClasses;
  }

  public PsiClassInitializer[] getInitializers() {
    //Diagnostic.methodNotImplemented();
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  public PsiTypeParameter[] getTypeParameters() {
    return PsiClassImplUtil.getTypeParameters(this);
  }

  public PsiField[] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  public PsiMethod[] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  public PsiClass[] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  public PsiField findFieldByName(String name, boolean checkBases) {
    if(!checkBases){
      if(myCachedFieldsMap == null){
        myCachedFieldsMap = new HashMap<String,PsiField>();
        final PsiField[] fields = getFields();
        for (int i = 0; i < fields.length; i++) {
          final PsiField field = fields[i];
          myCachedFieldsMap.put(field.getName(), field);
        }
      }
      return myCachedFieldsMap.get(name);
    }
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    if(!checkBases){
      if(myCachedMethodsMap == null){
        myCachedMethodsMap = new HashMap<String,PsiMethod[]>();
        Map<String, List<PsiMethod>> cachedMethodsMap = new HashMap<String,List<PsiMethod>>();
        final PsiMethod[] methods = getMethods();
        for (int i = 0; i < methods.length; i++) {
          final PsiMethod method = methods[i];
          List<PsiMethod> list = cachedMethodsMap.get(method.getName());
          if(list == null){
            list = new ArrayList<PsiMethod>(1);
            cachedMethodsMap.put(method.getName(), list);
          }
          list.add(method);
        }
        final Iterator<String> iterator = cachedMethodsMap.keySet().iterator();
        while (iterator.hasNext()) {
          final String methodName = iterator.next();
          myCachedMethodsMap.put(methodName, cachedMethodsMap.get(methodName).toArray(PsiMethod.EMPTY_ARRAY));
        }
      }
      final PsiMethod[] psiMethods = myCachedMethodsMap.get(name);
      return psiMethods != null ? psiMethods : PsiMethod.EMPTY_ARRAY;
    }

    return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiMethod.class);
  }

  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    if(!checkBases){
      if(myCachedInnersMap == null){
        myCachedInnersMap = new HashMap<String,PsiClass>();
        final PsiClass[] classes = getInnerClasses();
        for (int i = 0; i < classes.length; i++) {
          final PsiClass psiClass = classes[i];
          myCachedInnersMap.put(psiClass.getName(), psiClass);
        }
      }
      return myCachedInnersMap.get(name);
    }
    return PsiClassImplUtil.findInnerByName(this, name, checkBases);
  }

  public boolean isDeprecated() {
    synchronized (PsiLock.LOCK) {
      if (myIsDeprecated == null) {
        long repositoryId = getRepositoryId();
        if (repositoryId < 0) {
          try {
            boolean isDeprecated = readClassAttribute("Deprecated") != null;
            myIsDeprecated = isDeprecated ? Boolean.TRUE : Boolean.FALSE;
          }
          catch (ClsFormatException e) {
            myIsDeprecated = Boolean.FALSE;
          }
        }
        else {
          boolean isDeprecated = getRepositoryManager().getClassView().isDeprecated(repositoryId);
          myIsDeprecated = isDeprecated ? Boolean.TRUE : Boolean.FALSE;
        }
      }
      ;
    }
    return myIsDeprecated.booleanValue();
  }

  public String getSourceFileName() {
    synchronized (PsiLock.LOCK) {
      long repositoryId = getRepositoryId();
      if (repositoryId < 0) {
        try {
          String sourceFileName = getClassFileData().readUtf8Attribute(readClassAttribute("SourceFile"));
          if (sourceFileName == null) {
            sourceFileName = obtainSourceFileNameFromClassFileName();
            return sourceFileName;
          }
          int slashIndex = sourceFileName.lastIndexOf('/');      // We need short name while some compilers do generate fulls
          if (slashIndex >= 0) {
            sourceFileName = sourceFileName.substring(slashIndex + 1, sourceFileName.length());
          }
          return sourceFileName;
        }
        catch (ClsFormatException e) {
          return null;
        }
      }
      else {
        ClsFileImpl file = (ClsFileImpl)getContainingFile();
        String sourceFileName = getRepositoryManager().getFileView().getSourceFileName(file.getRepositoryId());
        if (sourceFileName == null || sourceFileName.length() == 0) {
          sourceFileName = obtainSourceFileNameFromClassFileName();
        }
        return sourceFileName;
      }
    }
  }

  private String obtainSourceFileNameFromClassFileName() {
    final String name = getContainingFile().getName();
    int i = name.indexOf('$');
    if (i < 0) {
      i = name.indexOf('.');
      if (i < 0) {
        i = name.length();
      }
    }
    return name.substring(0, i) + ".java";
  }

  public String getSignatureAttribute() throws ClsFormatException {
    return getClassFileData().readUtf8Attribute(readClassAttribute("Signature"));
  }

  private BytePointer readClassAttribute(String attributeName) throws ClsFormatException {
    ClassFileData classFileData = getClassFileData();
    BytePointer ptr = new BytePointer(classFileData.getData(), classFileData.getConstantPoolEnd() + 6);
    int count = ClsUtil.readU2(ptr);
    ptr.offset += count * 2; // skip interfaces
    count = ClsUtil.readU2(ptr);
    for (int i = 0; i < count; i++) { // skip fields
      ptr.offset += 6;
      ClsUtil.skipAttributes(ptr);
    }
    count = ClsUtil.readU2(ptr);
    for (int i = 0; i < count; i++) { // skip methods
      ptr.offset += 6;
      ClsUtil.skipAttributes(ptr);
    }

    BytePointer attribute = getClassFileData().findAttribute(ptr.offset, attributeName);
    return attribute;
  }

  public PsiDocComment getDocComment() {
    if (!isDeprecated()) return null;

    synchronized (PsiLock.LOCK) {
      if (myDocComment == null) {
        myDocComment = new ClsDocCommentImpl(this);
      }
      ;
    }
    return myDocComment;
  }

  public PsiJavaToken getLBrace() {
    return null;
  }

  public PsiJavaToken getRBrace() {
    return null;
  }

  public boolean isInterface() {
    long repositoryId = getRepositoryId();
    if (repositoryId < 0) {
      return (getAccessFlags() & ClsUtil.ACC_INTERFACE) != 0;
    }
    else {
      return getRepositoryManager().getClassView().isInterface(repositoryId);
    }
  }

  public boolean isAnnotationType() {
    long repositoryId = getRepositoryId();
    if (repositoryId < 0) {
      return (getAccessFlags() & ClsUtil.ACC_ANNOTATION) != 0;
    }
    else {
      return getRepositoryManager().getClassView().isAnnotationType(repositoryId);
    }
  }

  public boolean isEnum() {
    PsiField[] fields = getFields();
    if (fields.length == 0) return false;
    return fields[0] instanceof ClsEnumConstantImpl;
  }

  private int getAccessFlags() {
    synchronized (PsiLock.LOCK) {
      long repositoryId = getRepositoryId();
      if (repositoryId < 0) {
        try {
          ClassFileData classFileData = getClassFileData();
          int offset = classFileData.getConstantPoolEnd();
          byte[] data = classFileData.getData();
          if (offset + 2 > data.length) {
            throw new ClsFormatException();
          }
          int b1 = data[offset++] & 0xFF;
          int b2 = data[offset/*++*/] & 0xFF;
          int flags = ((b1 << 8) + b2) & ClsUtil.ACC_CLASS_MASK;

          PsiElement parent = getParent();
          if (parent instanceof PsiClass) {
            PsiClass aClass = (PsiClass)parent;
            if (aClass.isInterface()) {
              flags |= ClsUtil.ACC_STATIC;
            }
            else {
              flags &= ~ClsUtil.ACC_STATIC;

              BytePointer ptr = readClassAttribute("InnerClasses");
              if (ptr != null) {
                //Skip attribute_length
                ptr.offset += 4;
                int numClasses = ClsUtil.readU2(ptr);
                int startOffset = ptr.offset + 4;
                for (int i = 0; i < numClasses; i++) {
                  BytePointer ptr1 = new BytePointer(classFileData.getData(), startOffset + i * 8);
                  int innerNameIdx = ClsUtil.readU2(ptr1);
                  if (innerNameIdx == 0) {
                    continue;
                  }
                  int innerNameOffset = classFileData.getOffsetInConstantPool(innerNameIdx);
                  String innerName = ClsUtil.convertClassName(ClsUtil.readUtf8Info(classFileData.getData(), innerNameOffset), true);
                  if (getName().equals(innerName)) {
                    int accessFlags = ClsUtil.readU2(ptr1);
                    flags = accessFlags;
                    break;
                  }
                }
              }
            }
          }
          return flags;
        }
        catch (ClsFormatException e) {
          return 0;
        }
      }
      else {
        ClassView classView = getRepositoryManager().getClassView();
        return classView.getModifiers(repositoryId);
      }
    }
  }

  public String getMirrorText() {
    StringBuffer buffer = new StringBuffer();
    ClsDocCommentImpl docComment = (ClsDocCommentImpl)getDocComment();
    if (docComment != null) {
      buffer.append(docComment.getMirrorText());
    }
    buffer.append(((ClsElementImpl)getModifierList()).getMirrorText());
    buffer.append(' ');
    buffer.append(isEnum() ? "enum " : isAnnotationType() ? "@interface " : isInterface() ? "interface " : "class ");
    buffer.append(((ClsElementImpl)getNameIdentifier()).getMirrorText());
    buffer.append(((ClsTypeParametersListImpl)getTypeParameterList()).getMirrorText());
    buffer.append(' ');
    if (!isEnum() & !isAnnotationType()) {
      buffer.append(((ClsElementImpl)getExtendsList()).getMirrorText());
      buffer.append(' ');
    }
    if (!isInterface()) {
      buffer.append(((ClsElementImpl)getImplementsList()).getMirrorText());
    }
    buffer.append('{');
    PsiField[] fields = getFields();
    for (int i = 0; i < fields.length; i++) {
      PsiField field = fields[i];
      buffer.append(((ClsElementImpl)field).getMirrorText());
      if (field instanceof ClsEnumConstantImpl) {
        if (i < fields.length - 1 && fields[i + 1] instanceof ClsEnumConstantImpl) {
          buffer.append(", ");
        } else {
          buffer.append(";");
        }
      }
    }
    PsiMethod[] methods = getMethods();
    for (int i = 0; i < methods.length; i++) {
      buffer.append(((ClsElementImpl)methods[i]).getMirrorText());
    }
    PsiClass[] classes = getInnerClasses();
    for (int i = 0; i < classes.length; i++) {
      buffer.append(((ClsElementImpl)classes[i]).getMirrorText());
    }
    buffer.append('}');
    return buffer.toString();
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(myMirror == null);
    myMirror = element;
    PsiClass mirror = (PsiClass)SourceTreeToPsiMap.treeElementToPsi(element);

    if (getDocComment() != null) {
      ((ClsElementImpl)getDocComment()).setMirror(SourceTreeToPsiMap.psiElementToTree(mirror.getDocComment()));
    }
    ((ClsElementImpl)getModifierList()).setMirror(SourceTreeToPsiMap.psiElementToTree(mirror.getModifierList()));
    ((ClsElementImpl)getNameIdentifier()).setMirror(SourceTreeToPsiMap.psiElementToTree(mirror.getNameIdentifier()));
    if (!isAnnotationType() &&!isEnum()) {
      ((ClsElementImpl)getExtendsList()).setMirror(SourceTreeToPsiMap.psiElementToTree(mirror.getExtendsList()));
    }
    ((ClsElementImpl)getImplementsList()).setMirror(SourceTreeToPsiMap.psiElementToTree(mirror.getImplementsList()));
    ((ClsElementImpl)getTypeParameterList()).setMirror(SourceTreeToPsiMap.psiElementToTree(mirror.getTypeParameterList()));

    PsiField[] fields = getFields();
    PsiField[] mirrorFields = mirror.getFields();
    if (LOG.assertTrue(fields.length == mirrorFields.length)) {
      for (int i = 0; i < fields.length; i++) {
        ((ClsElementImpl)fields[i]).setMirror(SourceTreeToPsiMap.psiElementToTree(mirrorFields[i]));
      }
    }

    PsiMethod[] methods = getMethods();
    PsiMethod[] mirrorMethods = mirror.getMethods();
    if (LOG.assertTrue(methods.length == mirrorMethods.length)) {
      for (int i = 0; i < methods.length; i++) {
        ((ClsElementImpl)methods[i]).setMirror(SourceTreeToPsiMap.psiElementToTree(mirrorMethods[i]));
      }
    }

    PsiClass[] classes = getInnerClasses();
    PsiClass[] mirrorClasses = mirror.getInnerClasses();
    if (LOG.assertTrue(classes.length == mirrorClasses.length)) {
      for (int i = 0; i < classes.length; i++) {
        ((ClsElementImpl)classes[i]).setMirror(SourceTreeToPsiMap.psiElementToTree(mirrorClasses[i]));
      }
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitClass(this);
  }

  public String toString() {
    return "PsiClass:" + getName();
  }

  public boolean processDeclarations(PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastParent,
                                     PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, substitutor, new HashSet(), lastParent, place);
  }

  public PsiElement getScope() {
    return getParent();
  }

  public boolean isInheritor(PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  public PomMemberOwner getPom() {
    //TODO:
    return null;
  }

  public PsiClass getSourceMirrorClass() {
    PsiElement parent = getParent();
    if (parent instanceof PsiFile) {
      String packageName = ((ClsFileImpl)parent).getPackageName();
      String sourceFileName = getSourceFileName();
      String relativeFilePath = packageName.replace('.', '/') + '/' + sourceFileName;

      final VirtualFile vFile = getContainingFile().getVirtualFile();
      ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
      final OrderEntry[] orderEntries = projectFileIndex.getOrderEntriesForFile(vFile);
      for (int i = 0; i < orderEntries.length; i++) {
        VirtualFile[] files = orderEntries[i].getFiles(OrderRootType.SOURCES);
        for (int j = 0; j < files.length; j++) {
          VirtualFile source = files[j].findFileByRelativePath(relativeFilePath);
          if (source != null) {
            PsiFile psiSource = getManager().findFile(source);
            if (psiSource == null) continue;
            if (!(psiSource instanceof PsiJavaFile)) {
              LOG.error("Not PsiJavaFile:" + psiSource);
              continue;
            }
            PsiJavaFile psiJavaFile = (PsiJavaFile)psiSource;
            PsiClass[] classes = psiJavaFile.getClasses();
            for (int k = 0; k < classes.length; k++) {
              PsiClass aClass = classes[k];
              if (aClass.getName().equals(getName())) return aClass;
            }
          }
        }
      }
    }
    else {
      ClsClassImpl parentClass = (ClsClassImpl)parent;
      PsiClass parentSourceMirror = parentClass.getSourceMirrorClass();
      if (parentSourceMirror == null) return null;
      PsiClass[] innerClasses = parentSourceMirror.getInnerClasses();
      for (int i = 0; i < innerClasses.length; i++) {
        PsiClass innerClass = innerClasses[i];
        if (innerClass.getName().equals(getName())) return innerClass;
      }
    }

    return null;
  }

  public PsiElement getNavigationElement() {
    PsiClass aClass = getSourceMirrorClass();
    return aClass != null ? aClass : this;
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  public boolean isMetaEnough() {
    return false;
  }

  public ClsAnnotationImpl[] getAnnotations() {
    if (myAnnotations == null) {
      ClsAnnotationsUtil.AttributeReader reader = new ClsAnnotationsUtil.AttributeReader() {
        public BytePointer readAttribute(String attributeName) {
          try {
            return readClassAttribute(attributeName);
          }
          catch (ClsFormatException e) {
            return null;
          }
        }

        public ClassFileData getClassFileData() {
          return ClsClassImpl.this.getClassFileData();
        }
      };
      myAnnotations = ClsAnnotationsUtil.getAnnotationsImpl(this, reader, myModifierList);
    }

    return myAnnotations;
  }

  public ItemPresentation getPresentation() {
    return ClassPresentationUtil.getPresentation(this);
  }
}
