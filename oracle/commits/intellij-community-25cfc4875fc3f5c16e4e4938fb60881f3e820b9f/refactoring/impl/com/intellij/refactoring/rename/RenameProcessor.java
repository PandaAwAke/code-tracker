package com.intellij.refactoring.rename;

import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.naming.*;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RenameProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameProcessor");

  private final LinkedHashMap<PsiElement, String> myAllRenames = new LinkedHashMap<PsiElement, String>();

  private PsiElement myPrimaryElement;
  private String myNewName = null;

  private boolean mySearchInComments;
  private boolean mySearchTextOccurrences;
  private String myCommandName;
  private boolean myShouldRenameVariables;
  private boolean myShouldRenameInheritors;

  private boolean myShouldRenameForms;
  private NonCodeUsageInfo[] myNonCodeUsages = new NonCodeUsageInfo[0];
  private final List<AutomaticRenamer> myRenamers = new ArrayList<AutomaticRenamer>();

  public RenameProcessor(Project project,
                         PsiElement element,
                         @NonNls String newName,
                         boolean isSearchInComments,
                         boolean isSearchTextOccurrences) {
    super(project);
    myPrimaryElement = element;

    mySearchInComments = isSearchInComments;
    mySearchTextOccurrences = isSearchTextOccurrences;

    setNewName(newName);
  }
  public RenameProcessor(Project project, PsiElement element) {
    this(project, element, null, false, false);
  }

  public Set<PsiElement> getElements() {
    return Collections.unmodifiableSet(myAllRenames.keySet());
  }

  public void setShouldRenameVariables(boolean shouldRenameVariables) {
    myShouldRenameVariables = shouldRenameVariables;
  }

  public void setShouldRenameInheritors(boolean shouldRenameInheritors) {
    myShouldRenameInheritors = shouldRenameInheritors;
  }

  public void setShouldRenameForms(final boolean shouldRenameForms) {
    myShouldRenameForms = shouldRenameForms;
  }

  public void doRun() {
    prepareRenaming();

    super.doRun();
  }

  public void prepareRenaming() {
    if (myPrimaryElement instanceof PsiClass) {
      prepareClassRenaming((PsiClass)myPrimaryElement, myNewName);
    }
    else if (myPrimaryElement instanceof PsiField) {
      prepareFieldRenaming((PsiField)myPrimaryElement, myNewName);
    }
    else if (myPrimaryElement instanceof PsiPackage) {
      preparePackageRenaming((PsiPackage)myPrimaryElement, myNewName);
    }
    else if (myPrimaryElement instanceof PsiDirectory) {
      prepareDirectoryRenaming((PsiDirectory)myPrimaryElement, myNewName);
    }
    else if (myPrimaryElement instanceof Property) {
      preparePropertyRenaming((Property)myPrimaryElement, myNewName);
    }
  }

  private void prepareClassRenaming(final PsiClass aClass, final String newName) {
    final PsiMethod[] constructors = aClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      myAllRenames.put(constructor, newName);
    }
  }

  private void prepareDirectoryRenaming(final PsiDirectory directory, final String newName) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage != null && aPackage.getName() != null) {
      myAllRenames.put(aPackage, newName);
      preparePackageRenaming(aPackage, newName);
    }
  }

  private void preparePropertyRenaming(final Property property, final String newName) {
    ResourceBundle resourceBundle = property.getContainingFile().getResourceBundle();
    List<Property> properties = PropertiesUtil.findAllProperties(myProject, resourceBundle, property.getUnescapedKey());
    myAllRenames.clear();
    for (Property otherProperty : properties) {
      myAllRenames.put(otherProperty, newName);
    }
  }

  private void preparePackageRenaming(PsiPackage psiPackage, String newName) {
    final PsiDirectory[] directories = psiPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      if (!JavaDirectoryService.getInstance().isSourceRoot(directory)) {
        myAllRenames.put(directory, newName);
      }
    }
  }

  private String getHelpID() {
    return HelpID.getRenameHelpID(myPrimaryElement);
  }

  private void addExistingNameConflicts(final Collection<String> conflicts) {
    if (myPrimaryElement instanceof PsiCompiledElement) return;
    if (myPrimaryElement instanceof PsiMethod) {
      PsiMethod refactoredMethod = (PsiMethod)myPrimaryElement;
      if (myNewName.equals(refactoredMethod.getName())) return;
      final PsiMethod prototype = (PsiMethod)refactoredMethod.copy();
      try {
        prototype.setName(myNewName);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return;
      }

      ConflictsUtil.checkMethodConflicts(
        refactoredMethod.getContainingClass(),
        refactoredMethod,
        prototype,
        conflicts);
    }
    else if (myPrimaryElement instanceof PsiField) {
      PsiField refactoredField = (PsiField)myPrimaryElement;
      if (myNewName.equals(refactoredField.getName())) return;
      ConflictsUtil.checkFieldConflicts(
        refactoredField.getContainingClass(),
        myNewName,
        conflicts
      );
    }
    else if (myPrimaryElement instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)myPrimaryElement;
      if (myNewName.equals(aClass.getName())) return;
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass != null) { // innerClass
        PsiClass[] innerClasses = containingClass.getInnerClasses();
        for (PsiClass innerClass : innerClasses) {
          if (myNewName.equals(innerClass.getName())) {
            conflicts.add(RefactoringBundle.message("inner.class.0.is.already.defined.in.class.1", myNewName, containingClass.getQualifiedName()));
            break;
          }
        }
      }
      else {
        final String qualifiedNameAfterRename = RenameUtil.getQualifiedNameAfterRename(aClass, myNewName);
        final PsiClass conflictingClass = PsiManager.getInstance(myProject).findClass(qualifiedNameAfterRename, GlobalSearchScope.allScope(myProject));
        if (conflictingClass != null) {
          conflicts.add(RefactoringBundle.message("class.0.already.exists", qualifiedNameAfterRename));
        }
      }
    }
  }


  public boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    Set<String> conflicts = new HashSet<String>();

    conflicts.addAll(RenameUtil.getConflictDescriptions(usagesIn));
    addExistingNameConflicts(conflicts);
    if (!conflicts.isEmpty()) {
      ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
      conflictsDialog.show();
      if (!conflictsDialog.isOK()) {
        return false;
      }
    }
    Set<UsageInfo> usagesSet = new HashSet<UsageInfo>(Arrays.asList(usagesIn));
    RenameUtil.removeConflictUsages(usagesSet);

    final List<UsageInfo> variableUsages = new ArrayList<UsageInfo>();
    if (!myRenamers.isEmpty()) {
      if (!findRenamedVariables(variableUsages)) return false;
    }

    if (!variableUsages.isEmpty()) {
      usagesSet.addAll(variableUsages);
      refUsages.set(usagesSet.toArray(new UsageInfo[usagesSet.size()]));
    }

    prepareSuccessful();
    return true;
  }

  private boolean findRenamedVariables(final List<UsageInfo> variableUsages) {
    for (final AutomaticRenamer automaticVariableRenamer : myRenamers) {
      if (!automaticVariableRenamer.hasAnythingToRename()) continue;
      final AutomaticRenamingDialog dialog = new AutomaticRenamingDialog(myProject, automaticVariableRenamer);
      dialog.show();
      if (!dialog.isOK()) return false;
    }

    for (final AutomaticRenamer renamer : myRenamers) {
      final List<? extends PsiNamedElement> variables = renamer.getElements();
      for (final PsiNamedElement variable : variables) {
        final String newName = renamer.getNewName(variable);
        if (newName != null) {
          addElement(variable, newName);
        }
      }
    }

    Runnable runnable = new Runnable() {
      public void run() {
        for (final AutomaticRenamer renamer : myRenamers) {
          renamer.findUsages(variableUsages, mySearchInComments, mySearchTextOccurrences);
        }
      }
    };

    return ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(runnable, RefactoringBundle.message("searching.for.variables"), true, myProject);
  }

  public void addElement(PsiElement element, String newName) {
    myAllRenames.put(element, newName);
  }

  private void setNewName(String newName) {
    if (myPrimaryElement == null) {
      myCommandName = RefactoringBundle.message("renaming.something");
      return;
    }

    myNewName = newName;
    myAllRenames.put(myPrimaryElement, newName);
    myCommandName = RefactoringBundle.message("renaming.0.1.to.2",
                                              UsageViewUtil.getType(myPrimaryElement), UsageViewUtil.getDescriptiveName(myPrimaryElement),
                                              newName);
  }

  private void prepareFieldRenaming(PsiField field, String newName) {
    // search for getters/setters
    PsiClass aClass = field.getContainingClass();

    final JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(myProject);

    final String propertyName =
        manager.variableNameToPropertyName(field.getName(), VariableKind.FIELD);
    String newPropertyName = manager.variableNameToPropertyName(newName, VariableKind.FIELD);

    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, propertyName, isStatic, false);
    PsiMethod setter = PropertyUtil.findPropertySetter(aClass, propertyName, isStatic, false);

    boolean shouldRenameSetterParameter = false;

    if (setter != null) {
      String parameterName = manager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
      PsiParameter setterParameter = setter.getParameterList().getParameters()[0];
      shouldRenameSetterParameter = parameterName.equals(setterParameter.getName());
    }

    String newGetterName = "";

    if (getter != null) {
      String getterId = getter.getName();
      newGetterName = PropertyUtil.suggestGetterName(newPropertyName, field.getType(), getterId);
      if (newGetterName.equals(getterId)) {
        getter = null;
        newGetterName = null;
      }
    }

    String newSetterName = "";
    if (setter != null) {
      newSetterName = PropertyUtil.suggestSetterName(newPropertyName);
      final String newSetterParameterName = manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER);
      if (newSetterName.equals(setter.getName())) {
        setter = null;
        newSetterName = null;
        shouldRenameSetterParameter = false;
      } else if (newSetterParameterName.equals(setter.getParameterList().getParameters()[0].getName())) {
        shouldRenameSetterParameter = false;
      }
    }

    if (getter != null || setter != null) {
      if (askToRenameAccesors(getter, setter, newName)) {
        getter = null;
        setter = null;
        shouldRenameSetterParameter = false;
      }
    }

    if (getter != null) {
      addOverriddenAndImplemented(aClass, getter, newGetterName);
    }

    if (setter != null) {
      addOverriddenAndImplemented(aClass, setter, newSetterName);
    }

    if (shouldRenameSetterParameter) {
      PsiParameter parameter = setter.getParameterList().getParameters()[0];
      myAllRenames.put(parameter, manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER));
    }
  }

  private boolean askToRenameAccesors(PsiMethod getter, PsiMethod setter, String newName) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return false;
    String text = RefactoringMessageUtil.getGetterSetterMessage(newName, RefactoringBundle.message("rename.title"), getter, setter);
    return Messages.showYesNoDialog(myProject, text, RefactoringBundle.message("rename.title"), Messages.getQuestionIcon()) != 0;
  }

  private void addOverriddenAndImplemented(PsiClass aClass, PsiMethod methodPrototype, String newName) {
    final HashSet<PsiClass> superClasses = new HashSet<PsiClass>();
    RefactoringHierarchyUtil.getSuperClasses(aClass, superClasses, true);
    superClasses.add(aClass);

    for (PsiClass superClass : superClasses) {
      PsiMethod method = superClass.findMethodBySignature(methodPrototype, false);

      if (method != null) {
        myAllRenames.put(method, newName);
      }
    }
  }


  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new RenameViewDescriptor(myAllRenames);
  }

  @NotNull
  public UsageInfo[] findUsages() {
    myRenamers.clear();
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();

    List<PsiElement> elements = new ArrayList<PsiElement>(myAllRenames.keySet());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);
      final String newName = myAllRenames.get(element);
      final UsageInfo[] usages = RenameUtil.findUsages(element, newName, mySearchInComments, mySearchTextOccurrences, myAllRenames);
      result.addAll(Arrays.asList(usages));
      if (element instanceof PsiClass && myShouldRenameVariables) {
        myRenamers.add(new AutomaticVariableRenamer((PsiClass)element, newName, Arrays.asList(usages)));
      }
      if (element instanceof PsiClass && myShouldRenameInheritors) {
        if (((PsiClass)element).getName() != null) {
          myRenamers.add(new InheritorRenamer((PsiClass)element, newName));
        }
      }

      if (element instanceof PsiClass && myShouldRenameForms) {
        myRenamers.add(new FormsRenamer((PsiClass)element, newName));
      }

      if (element instanceof PsiMethod) {
        addOverriders((PsiMethod)element, newName, elements);
      }

      if (element instanceof PsiField) {
        myRenamers.add(new ConstructorParameterOnFieldRenameRenamer((PsiField)element, newName));
      }
    }
    UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    usageInfos = UsageViewUtil.removeDuplicatedUsages(usageInfos);
    return usageInfos;
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length > 0);
    if (myPrimaryElement != null) {
      myPrimaryElement = elements[0];
    }

    final Iterator<String> newNames = myAllRenames.values().iterator();
    LinkedHashMap<PsiElement, String> newAllRenames = new LinkedHashMap<PsiElement, String>();
    for (PsiElement resolved : elements) {
      newAllRenames.put(resolved, newNames.next());
    }
    myAllRenames.clear();
    myAllRenames.putAll(newAllRenames);
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    if (super.isPreviewUsages(usages)) return true;
    if (UsageViewUtil.hasNonCodeUsages(usages)) {
      WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("occurrences.found.in.comments.strings.and.non.java.files"));
      return true;
    }
    return false;
  }

  protected boolean markCommandAsComplex() {
    return false;
  }

  public void performRefactoring(UsageInfo[] usages) {
    String message = null;
    try {
      for (Map.Entry<PsiElement, String> entry : myAllRenames.entrySet()) {
        RenameUtil.checkRename(entry.getKey(), entry.getValue());
      }
    }
    catch (IncorrectOperationException e) {
      message = e.getMessage();
    }

    if (message != null) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("rename.title"), message, getHelpID(), myProject);
      return;
    }

    if (markCommandAsComplex()) {
      CommandProcessor.getInstance().markCurrentCommandAsComplex(myProject);
    }

    List<Pair<String, RefactoringElementListener>> listenersForPackages = new ArrayList<Pair<String,RefactoringElementListener>>();

    for (Map.Entry<PsiElement, String> entry : myAllRenames.entrySet()) {
      PsiElement element = entry.getKey();
      String newName = entry.getValue();

      final RefactoringElementListener elementListener = getTransaction().getElementListener(element);
      RenameUtil.doRename(element, newName, extractUsagesForElement(element, usages), myProject, elementListener);
      if (element instanceof PsiPackage) {
        final PsiPackage psiPackage = (PsiPackage)element;
        final String newQualifiedName = RenameUtil.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), newName);
        listenersForPackages.add(Pair.create(newQualifiedName, elementListener));
      }
    }

    final PsiManager psiManager = PsiManager.getInstance(myProject);
    for (Pair<String, RefactoringElementListener> pair : listenersForPackages) {
      String qualifiedName = pair.getFirst();
      RefactoringElementListener listener = pair.getSecond();
      final PsiPackage aPackage = psiManager.findPackage(qualifiedName);
      if (aPackage == null) {
        LOG.error("Package cannot be found: "+qualifiedName+"; listener="+listener);
      }
      listener.elementRenamed(aPackage);
    }

    List<NonCodeUsageInfo> nonCodeUsages = new ArrayList<NonCodeUsageInfo>();
    for (UsageInfo usage : usages) {
      if (usage instanceof NonCodeUsageInfo) {
        nonCodeUsages.add((NonCodeUsageInfo)usage);
      }
    }
    myNonCodeUsages = nonCodeUsages.toArray(new NonCodeUsageInfo[nonCodeUsages.size()]);
  }

  protected void performPsiSpoilingRefactoring() {
    RefactoringUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
  }

  protected String getCommandName() {
    return myCommandName;
  }

  private static UsageInfo[] extractUsagesForElement(PsiElement element, UsageInfo[] usages) {
    final ArrayList<UsageInfo> extractedUsages = new ArrayList<UsageInfo>(usages.length);
    for (UsageInfo usage : usages) {
      LOG.assertTrue(usage instanceof MoveRenameUsageInfo);
      if (usage.getReference() instanceof LightElement) continue; //filter out implicit references (e.g. from derived class to super class' default constructor)

      MoveRenameUsageInfo usageInfo = (MoveRenameUsageInfo)usage;
      if (element.equals(usageInfo.getReferencedElement())) {
        extractedUsages.add(usageInfo);
      }
    }
    return extractedUsages.toArray(new UsageInfo[extractedUsages.size()]);
  }

  private void addOverriders(final PsiMethod method, final String newName, final List<PsiElement> methods) {
    OverridingMethodsSearch.search(method, true).forEach(new Processor<PsiMethod>() {
      public boolean process(PsiMethod overrider) {
        final String overriderName = overrider.getName();
        final String baseName = method.getName();
        final String newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newName);
        if (newOverriderName != null) {
          myAllRenames.put(overrider, newOverriderName);
          methods.add(overrider);
        }
        return true;
      }
    });
  }

  protected void prepareTestRun() {
    if (!PsiElementRenameHandler.canRename(myPrimaryElement, myProject)) return;
    prepareRenaming();
  }

  public Collection<String> getNewNames() {
    return myAllRenames.values();
  }

  public void setSearchInComments(boolean value) {
    mySearchInComments = value;
  }

  public void setSearchTextOccurrences(boolean searchTextOccurrences) {
    mySearchTextOccurrences = searchTextOccurrences;
  }

  public boolean isSearchInComments() {
    return mySearchInComments;
  }

  public boolean isSearchTextOccurrences() {
    return mySearchTextOccurrences;
  }

  public void setCommandName(final String commandName) {
    myCommandName = commandName;
  }
}
