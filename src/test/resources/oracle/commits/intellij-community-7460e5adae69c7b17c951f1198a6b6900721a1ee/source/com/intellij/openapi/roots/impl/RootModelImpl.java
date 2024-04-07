package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.watcher.OrderEntryProperties;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.*;
import org.jdom.Element;

import java.util.*;

/**
 * @author dsl
 */
class RootModelImpl implements ModifiableRootModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.RootModelImpl");
  static final Key<RootModelImpl> ORIGINATING_ROOT_MODEL = Key.create("ORIGINATING_ROOT_MODEL");

  private TreeSet<ContentEntry> myContent = new TreeSet<ContentEntry>(ContentComparator.INSTANCE);

  private List<OrderEntry> myOrder = new Order();

  private final ModuleLibraryTable myModuleLibraryTable;
  final ModuleRootManagerImpl myModuleRootManager;
  private boolean myWritable;
  private final VirtualFilePointerListener myVirtualFilePointerListener;
  private VirtualFilePointerManager myFilePointerManager;
  VirtualFilePointer myCompilerOutputPath;
  VirtualFilePointer myCompilerOutputPathForTests;
  VirtualFilePointer myExplodedDirectory;
  ArrayList<RootModelComponentBase> myComponents = new ArrayList<RootModelComponentBase>();
  private List<VirtualFilePointer> myPointersToDispose = new ArrayList<VirtualFilePointer>();

  private boolean myExcludeOutput;
  private boolean myExcludeExploded;

  private static final String OUTPUT_TAG = "output";
  private static final String TEST_OUTPUT_TAG = "output-test";
  private static final String EXPLODED_TAG = "exploded";
  private static final String URL_ATTR = "url";
  private static final String EXCLUDE_OUTPUT_TAG = "exclude-output";
  private static final String EXCLUDE_EXPLODED_TAG = "exclude-exploded";
  private boolean myDisposed = false;
  private final OrderEntryProperties myOrderEntryProperties;
  private final VirtualFilePointerContainer myJavadocPointerContainer;

  private VirtualFilePointerFactory myVirtualFilePointerFactory = new VirtualFilePointerFactory() {
    public VirtualFilePointer create(VirtualFile file) {
      final VirtualFilePointer pointer = myFilePointerManager.create(file, getFileListener());
      annotatePointer(pointer);
      return pointer;
    }

    public VirtualFilePointer create(String url) {
      final VirtualFilePointer pointer = myFilePointerManager.create(url, getFileListener());
      annotatePointer(pointer);
      return pointer;
    }

    public VirtualFilePointer duplicate(VirtualFilePointer virtualFilePointer) {
      final VirtualFilePointer pointer = myFilePointerManager.duplicate(virtualFilePointer, getFileListener());
      annotatePointer(pointer);
      return pointer;
    }
  };
  private static final String PROPERTIES_CHILD_NAME = "orderEntryProperties";
  private static final String JAVADOC_PATHS_NAME = "javadoc-paths";
  private static final String JAVADOC_ROOT_ELEMENT = "root";
  private ProjectRootManagerImpl myProjectRootManager;


  public String getCompilerOutputPathUrl() {
    return getCompilerOutputUrl();
  }

  public String getCompilerOutputPathForTestsUrl() {
    return getCompilerOutputUrlForTests();
  }

  RootModelImpl(ModuleRootManagerImpl moduleRootManager,
                ProjectRootManagerImpl projectRootManager,
                VirtualFilePointerManager filePointerManager) {
    myModuleRootManager = moduleRootManager;
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;

    myWritable = false;
    myVirtualFilePointerListener = null;
    addSourceOrderEntries();
    myOrderEntryProperties = new OrderEntryProperties();
    myJavadocPointerContainer = myFilePointerManager.createContainer(myVirtualFilePointerFactory);
    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager, myFilePointerManager);
  }

  private void addSourceOrderEntries() {
    myOrder.add(new ModuleSourceOrderEntryImpl(this));
  }

  RootModelImpl(Element element,
                ModuleRootManagerImpl moduleRootManager,
                ProjectRootManagerImpl projectRootManager,
                VirtualFilePointerManager filePointerManager) throws InvalidDataException {
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;
    myModuleRootManager = moduleRootManager;

    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager, myFilePointerManager);

    myVirtualFilePointerListener = null;
    final List contentChildren = element.getChildren(ContentEntryImpl.ELEMENT_NAME);
    for (int i = 0; i < contentChildren.size(); i++) {
      Element child = (Element)contentChildren.get(i);
      ContentEntryImpl contentEntry = new ContentEntryImpl(child, this);
      myContent.add(contentEntry);
    }

    final List orderElements = element.getChildren(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME);
    boolean moduleSourceAdded = false;
    for (int i = 0; i < orderElements.size(); i++) {
      Element child = (Element)orderElements.get(i);
      final OrderEntry orderEntry = OrderEntryFactory.createOrderEntryByElement(child, this, myProjectRootManager, myFilePointerManager);
      if (orderEntry instanceof ModuleSourceOrderEntry) {
        if (moduleSourceAdded) continue;
        moduleSourceAdded = true;
      }
      myOrder.add(orderEntry);
    }

    if (!moduleSourceAdded) {
      myOrder.add(new ModuleSourceOrderEntryImpl(this));
    }

    myExcludeOutput = element.getChild(EXCLUDE_OUTPUT_TAG) != null;
    myExcludeExploded = element.getChild(EXCLUDE_EXPLODED_TAG) != null;

    myCompilerOutputPath = getOutputPathValue(element, OUTPUT_TAG);
    myCompilerOutputPathForTests = getOutputPathValue(element, TEST_OUTPUT_TAG);
    myExplodedDirectory = getOutputPathValue(element, EXPLODED_TAG);


    myWritable = false;
    myOrderEntryProperties = new OrderEntryProperties();
    final Element propertiesChild = element.getChild(PROPERTIES_CHILD_NAME);
    if (propertiesChild != null) {
      myOrderEntryProperties.readExternal(propertiesChild);
    }

    myJavadocPointerContainer = myFilePointerManager.createContainer(myVirtualFilePointerFactory);
    final Element javaDocPaths = element.getChild(JAVADOC_PATHS_NAME);
    if (javaDocPaths != null) {
      myJavadocPointerContainer.readExternal(javaDocPaths, JAVADOC_ROOT_ELEMENT);
    }
  }

  public boolean isWritable() {
    return myWritable;
  }

  RootModelImpl(RootModelImpl rootModel,
                ModuleRootManagerImpl moduleRootManager,
                final boolean writable,
                final VirtualFilePointerListener virtualFilePointerListener,
                VirtualFilePointerManager filePointerManager,
                ProjectRootManagerImpl projectRootManager) {
    myFilePointerManager = filePointerManager;
    myModuleRootManager = moduleRootManager;
    myProjectRootManager = projectRootManager;

    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager, myFilePointerManager);

    myWritable = writable;
    LOG.assertTrue(!writable || virtualFilePointerListener == null);
    myVirtualFilePointerListener = virtualFilePointerListener;

    if (rootModel.myCompilerOutputPath != null) {
      myCompilerOutputPath = pointerFactory().duplicate(rootModel.myCompilerOutputPath);
    }

    if (rootModel.myCompilerOutputPathForTests != null) {
      myCompilerOutputPathForTests = pointerFactory().duplicate(rootModel.myCompilerOutputPathForTests);
    }

    if (rootModel.myExplodedDirectory != null) {
      myExplodedDirectory = pointerFactory().duplicate(rootModel.myExplodedDirectory);
    }

    myExcludeOutput = rootModel.myExcludeOutput;
    myExcludeExploded = rootModel.myExcludeExploded;

    final TreeSet<ContentEntry> thatContent = rootModel.myContent;
    for (Iterator<ContentEntry> iterator = thatContent.iterator(); iterator.hasNext();) {
      ContentEntry contentEntry = iterator.next();
      if (contentEntry instanceof ClonableContentEntry) {
        myContent.add(((ClonableContentEntry)contentEntry).cloneEntry(this));
      }
    }

    final List<OrderEntry> order = rootModel.myOrder;
    for (int i = 0; i < order.size(); i++) {
      OrderEntry orderEntry = order.get(i);
      if (orderEntry instanceof ClonableOrderEntry) {
        myOrder.add(((ClonableOrderEntry)orderEntry).cloneEntry(this, myProjectRootManager, myFilePointerManager));
      }
    }
    myOrderEntryProperties = rootModel.myOrderEntryProperties.copy(this);
    myJavadocPointerContainer = myFilePointerManager.createContainer(myVirtualFilePointerFactory);
    myJavadocPointerContainer.addAll(rootModel.myJavadocPointerContainer);
  }

  public VirtualFile[] getOrderedRoots(OrderRootType type) {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();

    for (Iterator<OrderEntry> iterator = myOrder.iterator(); iterator.hasNext();) {
      OrderEntry orderEntry = iterator.next();
      result.addAll(Arrays.asList(orderEntry.getFiles(type)));
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  public String[] getOrderedRootUrls(OrderRootType type) {
    final ArrayList<String> result = new ArrayList<String>();

    for (Iterator<OrderEntry> iterator = myOrder.iterator(); iterator.hasNext();) {
      OrderEntry orderEntry = iterator.next();
      result.addAll(Arrays.asList(orderEntry.getUrls(type)));
    }
    return result.toArray(new String[result.size()]);
  }

  public VirtualFile[] getContentRoots() {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();

    for (Iterator<ContentEntry> iterator = myContent.iterator(); iterator.hasNext();) {
      ContentEntry contentEntry = iterator.next();
      final VirtualFile file = contentEntry.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  public String[] getContentRootUrls() {
    final ArrayList<String> result = new ArrayList<String>();

    for (Iterator<ContentEntry> iterator = myContent.iterator(); iterator.hasNext();) {
      ContentEntry contentEntry = iterator.next();
      result.add(contentEntry.getUrl());
    }
    return result.toArray(new String[result.size()]);
  }

  public String[] getExcludeRootUrls() {
    final ArrayList<String> result = new ArrayList<String>();
    for (Iterator<ContentEntry> iterator = myContent.iterator(); iterator.hasNext();) {
      ContentEntry contentEntry = iterator.next();
      final ExcludeFolder[] excludeFolders = contentEntry.getExcludeFolders();
      for (int i = 0; i < excludeFolders.length; i++) {
        ExcludeFolder excludeFolder = excludeFolders[i];
        result.add(excludeFolder.getUrl());
      }
    }
    return result.toArray(new String[result.size()]);
  }

  public VirtualFile[] getExcludeRoots() {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Iterator<ContentEntry> iterator = myContent.iterator(); iterator.hasNext();) {
      ContentEntry contentEntry = iterator.next();
      final ExcludeFolder[] excludeFolders = contentEntry.getExcludeFolders();
      for (int i = 0; i < excludeFolders.length; i++) {
        ExcludeFolder excludeFolder = excludeFolders[i];
        final VirtualFile file = excludeFolder.getFile();
        if (file != null) {
          result.add(file);
        }
      }
    }
    return result.toArray(new VirtualFile[result.size()]);
  }


  public String[] getSourceRootUrls() {
    final ArrayList<String> result = new ArrayList<String>();
    for (Iterator<ContentEntry> iterator = myContent.iterator(); iterator.hasNext();) {
      ContentEntry contentEntry = iterator.next();
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (int i = 0; i < sourceFolders.length; i++) {
        SourceFolder sourceFolder = sourceFolders[i];
        result.add(sourceFolder.getUrl());
      }
    }
    return result.toArray(new String[result.size()]);
  }

  public String[] getSourceRootUrls(boolean testFlagValue) {
    final ArrayList<String> result = new ArrayList<String>();
    for (Iterator<ContentEntry> iterator = myContent.iterator(); iterator.hasNext();) {
      ContentEntry contentEntry = iterator.next();
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (int i = 0; i < sourceFolders.length; i++) {
        SourceFolder sourceFolder = sourceFolders[i];
        if (sourceFolder.isTestSource() == testFlagValue) {
          result.add(sourceFolder.getUrl());
        }
      }
    }
    return result.toArray(new String[result.size()]);
  }

  public VirtualFile[] getSourceRoots() {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Iterator<ContentEntry> iterator = myContent.iterator(); iterator.hasNext();) {
      ContentEntry contentEntry = iterator.next();
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (int i = 0; i < sourceFolders.length; i++) {
        SourceFolder sourceFolder = sourceFolders[i];
        final VirtualFile file = sourceFolder.getFile();
        if (file != null) {
          result.add(file);
        }
      }
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  public ContentEntry[] getContentEntries() {
    return myContent.toArray(new ContentEntry[myContent.size()]);
  }

  public OrderEntry[] getOrderEntries() {
    return myOrder.toArray(new OrderEntry[myOrder.size()]);
  }

  Iterator<OrderEntry> getOrderIterator() {
    return Collections.unmodifiableList(myOrder).iterator();
  }

  public void removeContentEntry(ContentEntry entry) {
    assertWritable();
    LOG.assertTrue(myContent.contains(entry));
    myContent.remove(entry);
    if (myComponents.contains(entry)) {
      ((RootModelComponentBase)entry).dispose();
    }
  }

  public void addOrderEntry(OrderEntry entry) {
    assertWritable();
    LOG.assertTrue(!myOrder.contains(entry));
    myOrder.add(entry);
  }

  public LibraryOrderEntry addLibraryEntry(Library library) {
    assertWritable();
    final LibraryOrderEntry libraryOrderEntry = new LibraryOrderEntryImpl(library, this, myProjectRootManager, myFilePointerManager);
    myOrder.add(libraryOrderEntry);
    return libraryOrderEntry;
  }

  public LibraryOrderEntry addInvalidLibrary(String name, String level) {
    assertWritable();
    final LibraryOrderEntry libraryOrderEntry = new LibraryOrderEntryImpl(name, level, this, myProjectRootManager, myFilePointerManager);
    myOrder.add(libraryOrderEntry);
    return libraryOrderEntry;
  }

  public ModuleOrderEntry addModuleOrderEntry(Module module) {
    assertWritable();
    LOG.assertTrue(!module.equals(getModule()));
    LOG.assertTrue(Comparing.equal(myModuleRootManager.getModule().getProject(), module.getProject()));
    final ModuleOrderEntryImpl moduleOrderEntry = new ModuleOrderEntryImpl(module, this);
    myOrder.add(moduleOrderEntry);
    return moduleOrderEntry;
  }

  public ModuleOrderEntry addInvalidModuleEntry(String name) {
    assertWritable();
    LOG.assertTrue(!name.equals(getModule().getName()));
    final ModuleOrderEntryImpl moduleOrderEntry = new ModuleOrderEntryImpl(name, this);
    myOrder.add(moduleOrderEntry);
    return moduleOrderEntry;
  }

  public LibraryOrderEntry findLibraryOrderEntry(Library library) {
    for (Iterator<OrderEntry> iterator = myOrder.iterator(); iterator.hasNext();) {
      OrderEntry orderEntry = iterator.next();
      if (orderEntry instanceof LibraryOrderEntry && library.equals(((LibraryOrderEntry)orderEntry).getLibrary())) {
        return (LibraryOrderEntry)orderEntry;
      }
    }
    return null;
  }

  public void removeOrderEntry(OrderEntry entry) {
    assertWritable();
    removeOrderEntryInternal(entry);
  }

  void removeOrderEntryInternal(OrderEntry entry) {
    LOG.assertTrue(myOrder.contains(entry));
    myOrder.remove(entry);
  }

  public void rearrangeOrderEntries(OrderEntry[] newEntries) {
    assertWritable();
    assertValidRearrangement(newEntries);
    myOrder.clear();
    for (int i = 0; i < newEntries.length; i++) {
      OrderEntry newEntry = newEntries[i];
      myOrder.add(newEntry);
    }
  }

  private void assertValidRearrangement(OrderEntry[] newEntries) {
    LOG.assertTrue(newEntries.length == myOrder.size(), "Invalid rearranged order");
    Set<OrderEntry> set = new com.intellij.util.containers.HashSet<OrderEntry>();
    for (int i = 0; i < newEntries.length; i++) {
      OrderEntry newEntry = newEntries[i];
      LOG.assertTrue(myOrder.contains(newEntry), "Invalid rearranged order");
      LOG.assertTrue(!set.contains(newEntry), "Invalid rearranged order");
      set.add(newEntry);
    }
  }

  public void clear() {
    final ProjectJdk jdk = getJdk();
    myContent.clear();
    myOrder.clear();
    setJdk(jdk);
    addSourceOrderEntries();
  }

  public void commit() {
    myModuleRootManager.commitModel(this);
    myWritable = false;
  }

  public LibraryTable getModuleLibraryTable() {
    return myModuleLibraryTable;
  }

  public <R> R processOrder(RootPolicy<R> policy, R initialValue) {
    R result = initialValue;
    for (Iterator<OrderEntry> iterator = myOrder.iterator(); iterator.hasNext();) {
      OrderEntry orderEntry = iterator.next();
      result = orderEntry.accept(policy, result);
    }
    return result;
  }

  public ContentEntry addContentEntry(VirtualFile file) {
    ContentEntry entry = new ContentEntryImpl(file, this);
    myContent.add(entry);
    return entry;
  }

  private VirtualFilePointer getOutputPathValue(Element element, String tag) {
    final Element outputPathChild = element.getChild(tag);
    VirtualFilePointer vptr = null;
    if (outputPathChild != null) {
      String outputPath = outputPathChild.getAttributeValue("url");
      vptr = pointerFactory().create(outputPath);
    }
    return vptr;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (myCompilerOutputPath != null) {
      final Element pathElement = new Element(OUTPUT_TAG);
      pathElement.setAttribute(URL_ATTR, myCompilerOutputPath.getUrl());
      element.addContent(pathElement);
    }

    if (myExcludeOutput) {
      element.addContent(new Element(EXCLUDE_OUTPUT_TAG));
    }

    if (myExplodedDirectory != null) {
      final Element pathElement = new Element(EXPLODED_TAG);
      pathElement.setAttribute(URL_ATTR, myExplodedDirectory.getUrl());
      element.addContent(pathElement);
    }

    if (myExcludeExploded) {
      element.addContent(new Element(EXCLUDE_EXPLODED_TAG));
    }

    if (myCompilerOutputPathForTests != null) {
      final Element pathElement = new Element(TEST_OUTPUT_TAG);
      pathElement.setAttribute("url", myCompilerOutputPathForTests.getUrl());
      element.addContent(pathElement);
    }

    for (Iterator<ContentEntry> iterator = myContent.iterator(); iterator.hasNext();) {
      ContentEntry contentEntry = iterator.next();
      if (contentEntry instanceof ContentEntryImpl) {
        final Element subElement = new Element(ContentEntryImpl.ELEMENT_NAME);
        ((ContentEntryImpl)contentEntry).writeExternal(subElement);
        element.addContent(subElement);
      }
    }

    for (int i = 0; i < myOrder.size(); i++) {
      OrderEntry orderEntry = myOrder.get(i);
      if (orderEntry instanceof WritableOrderEntry) {
        ((WritableOrderEntry)orderEntry).writeExternal(element);
      }
    }

    final Element propertiesChild = new Element(PROPERTIES_CHILD_NAME);
    myOrderEntryProperties.writeExternal(propertiesChild, this);
    element.addContent(propertiesChild);

    if (myJavadocPointerContainer.size() > 0) {
      final Element javaDocPaths = new Element(JAVADOC_PATHS_NAME);
      myJavadocPointerContainer.writeExternal(javaDocPaths, JAVADOC_ROOT_ELEMENT);
      element.addContent(javaDocPaths);
    }
  }

  public void setJdk(ProjectJdk jdk) {
    assertWritable();
    final JdkOrderEntry jdkLibraryEntry;
    if (jdk != null) {
      jdkLibraryEntry = new ModuleJdkOrderEntryImpl(jdk, this, myProjectRootManager, myFilePointerManager);
    }
    else {
      jdkLibraryEntry = null;
    }
    replaceJdkEntry(jdkLibraryEntry);

  }

  private void replaceJdkEntry(final JdkOrderEntry jdkLibraryEntry) {
    for (int i = 0; i < myOrder.size(); i++) {
      OrderEntry orderEntry = myOrder.get(i);
      if (orderEntry instanceof JdkOrderEntry) {
        myOrder.remove(i);
        if (jdkLibraryEntry != null) {
          myOrder.add(i, jdkLibraryEntry);
        }
        return;
      }
    }

    if (jdkLibraryEntry != null) {
      myOrder.add(0, jdkLibraryEntry);
    }
  }

  public void inheritJdk() {
    assertWritable();
    replaceJdkEntry(new InheritedJdkOrderEntryImpl(this, myProjectRootManager, myFilePointerManager));
  }

  public ProjectJdk getJdk() {
    for (int i = 0; i < myOrder.size(); i++) {
      OrderEntry orderEntry = myOrder.get(i);
      if (orderEntry instanceof JdkOrderEntry) {
        return ((JdkOrderEntry)orderEntry).getJdk();
      }
    }
    return null;
  }

  public boolean isJdkInherited() {
    for (int i = 0; i < myOrder.size(); i++) {
      OrderEntry orderEntry = myOrder.get(i);
      if (orderEntry instanceof InheritedJdkOrderEntry) {
        return true;
      }
    }
    return false;
  }

  public void assertWritable() {
    LOG.assertTrue(myWritable);
  }

  private static class ContentComparator implements Comparator<ContentEntry> {
    public static final ContentComparator INSTANCE = new ContentComparator();

    public int compare(final ContentEntry o1, final ContentEntry o2) {
      return o1.getUrl().compareTo(o2.getUrl());
    }
  }

  public VirtualFile getCompilerOutputPath() {
    if (myCompilerOutputPath == null) {
      return null;
    }
    else {
      return myCompilerOutputPath.getFile();
    }
  }

  public VirtualFile getCompilerOutputPathForTests() {
    if (myCompilerOutputPathForTests == null) {
      return null;
    }
    else {
      return myCompilerOutputPathForTests.getFile();
    }
  }

  public VirtualFile getExplodedDirectory() {
    if (myExplodedDirectory == null) {
      return null;
    }
    else {
      return myExplodedDirectory.getFile();
    }
  }

  public void setCompilerOutputPath(VirtualFile file) {
    assertWritable();
    if (file != null) {
      myCompilerOutputPath = pointerFactory().create(file);
    }
    else {
      myCompilerOutputPath = null;
    }
  }

  public void setCompilerOutputPath(String url) {
    assertWritable();
    if (url != null) {
      myCompilerOutputPath = pointerFactory().create(url);
    }
    else {
      myCompilerOutputPath = null;
    }
  }

  public void setCompilerOutputPathForTests(VirtualFile file) {
    assertWritable();
    if (file != null) {
      myCompilerOutputPathForTests = pointerFactory().create(file);
    }
    else {
      myCompilerOutputPathForTests = null;
    }
  }

  public void setCompilerOutputPathForTests(String url) {
    assertWritable();
    if (url != null) {
      myCompilerOutputPathForTests = pointerFactory().create(url);
    }
    else {
      myCompilerOutputPathForTests = null;
    }
  }

  public void setExplodedDirectory(VirtualFile file) {
    assertWritable();
    if (file != null) {
      myExplodedDirectory = pointerFactory().create(file);
    }
    else {
      myExplodedDirectory = null;
    }
  }

  public void setExplodedDirectory(String url) {
    assertWritable();
    if (url != null) {
      myExplodedDirectory = pointerFactory().create(url);
    }
    else {
      myExplodedDirectory = null;
    }
  }

  public Module getModule() {
    return myModuleRootManager.getModule();
  }

  VirtualFilePointerListener getFileListener() {
    return myVirtualFilePointerListener;
  }

  public String getCompilerOutputUrl() {
    if (myCompilerOutputPath == null) {
      return null;
    }
    else {
      return myCompilerOutputPath.getUrl();
    }
  }

  public String getCompilerOutputUrlForTests() {
    if (myCompilerOutputPathForTests == null) {
      return null;
    }
    else {
      return myCompilerOutputPathForTests.getUrl();
    }
  }

  public String getExplodedDirectoryUrl() {
    if (myExplodedDirectory == null) {
      return null;
    }
    else {
      return myExplodedDirectory.getUrl();
    }
  }

  private static boolean vptrEqual(VirtualFilePointer p1, VirtualFilePointer p2) {
    if (p1 == null && p2 == null) return true;
    if (p1 == null || p2 == null) return false;
    return Comparing.equal(p1.getUrl(), p2.getUrl());
  }


  public boolean isChanged() {
    if (!myWritable) return false;
//    if (myJdkChanged) return true;

    if (!vptrEqual(myCompilerOutputPath, getSourceModel().myCompilerOutputPath)) {
      return true;
    }
    if (!vptrEqual(myCompilerOutputPathForTests, getSourceModel().myCompilerOutputPathForTests)) {
      return true;
    }
    if (!vptrEqual(myExplodedDirectory, getSourceModel().myExplodedDirectory)) {
      return true;
    }

    if (myExcludeOutput != getSourceModel().myExcludeOutput) return true;
    if (myExcludeExploded != getSourceModel().myExcludeExploded) return true;

    OrderEntry[] orderEntries = getOrderEntries();
    OrderEntry[] sourceOrderEntries = getSourceModel().getOrderEntries();
    if (orderEntries.length != sourceOrderEntries.length) return true;
    for (int i = 0; i < orderEntries.length; i++) {
      OrderEntry orderEntry = orderEntries[i];
      OrderEntry sourceOrderEntry = sourceOrderEntries[i];
      if (!orderEntriesEquals(orderEntry, sourceOrderEntry)) {
        return true;
      }
    }

    final String[] contentRootUrls = getContentRootUrls();
    final String[] thatContentRootUrls = getSourceModel().getContentRootUrls();
    if (!Arrays.equals(contentRootUrls, thatContentRootUrls)) return true;

    final String[] excludeRootUrls = getExcludeRootUrls();
    final String[] thatExcludeRootUrls = getSourceModel().getExcludeRootUrls();
    if (!Arrays.equals(excludeRootUrls, thatExcludeRootUrls)) return true;

    final String[] sourceRootForMainUrls = getSourceRootUrls(false);
    final String[] thatSourceRootForMainUrls = getSourceModel().getSourceRootUrls(false);
    if (!Arrays.equals(sourceRootForMainUrls, thatSourceRootForMainUrls)) return true;

    final String[] sourceRootForTestUrls = getSourceRootUrls(true);
    final String[] thatSourceRootForTestUrls = getSourceModel().getSourceRootUrls(true);
    if (!Arrays.equals(sourceRootForTestUrls, thatSourceRootForTestUrls)) return true;

    final ContentEntry[] contentEntries = getContentEntries();
    final ContentEntry[] thatContentEntries = getSourceModel().getContentEntries();
    if (contentEntries.length != thatContentEntries.length) return true;
    for (int i = 0; i < contentEntries.length; i++) {
      final ContentEntry contentEntry = contentEntries[i];
      final ContentEntry thatContentEntry = thatContentEntries[i];
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      final SourceFolder[] thatSourceFolders = thatContentEntry.getSourceFolders();
      if (sourceFolders.length != thatSourceFolders.length) return true;
      for (int j = 0; j < sourceFolders.length; j++) {
        final SourceFolder sourceFolder = sourceFolders[j];
        final SourceFolder thatSourceFolder = thatSourceFolders[j];
        if (!sourceFolder.getUrl().equals(thatSourceFolder.getUrl())
            || !sourceFolder.getPackagePrefix().equals(thatSourceFolder.getPackagePrefix())) {
          return true;
        }
      }
    }
    final String[] urls = myJavadocPointerContainer.getUrls();
    final String[] thatUrls = getSourceModel().myJavadocPointerContainer.getUrls();
    if (!Arrays.equals(urls, thatUrls)) return true;
    return false;
  }

  void addExportedFiles(OrderRootType type, List<VirtualFile> result, Set<Module> processed) {
    for (Iterator<OrderEntry> iterator = myOrder.iterator(); iterator.hasNext();) {
      final OrderEntry orderEntry = iterator.next();
      if (orderEntry instanceof ModuleSourceOrderEntryImpl) {
        ((ModuleSourceOrderEntryImpl)orderEntry).addExportedFiles(type, result);
      }
      else if (orderEntry instanceof ExportableOrderEntry && ((ExportableOrderEntry)orderEntry).isExported()) {
        if (orderEntry instanceof ModuleOrderEntryImpl) {
          result.addAll(Arrays.asList(((ModuleOrderEntryImpl)orderEntry).getFiles(type, processed)));
        }
        else {
          result.addAll(Arrays.asList(orderEntry.getFiles(type)));
        }
      }
    }
  }

  void addExportedUrs(OrderRootType type, List<String> result, Set<Module> processed) {
    for (Iterator<OrderEntry> iterator = myOrder.iterator(); iterator.hasNext();) {
      final OrderEntry orderEntry = iterator.next();
      if (orderEntry instanceof ModuleSourceOrderEntry) {
        ((ModuleSourceOrderEntryImpl)orderEntry).addExportedUrls(type, result);
      }
      else if (orderEntry instanceof ExportableOrderEntry && ((ExportableOrderEntry)orderEntry).isExported()) {
        if (orderEntry instanceof ModuleOrderEntryImpl) {
          result.addAll(Arrays.asList(((ModuleOrderEntryImpl)orderEntry).getUrls(type, processed)));
        }
        else {
          result.addAll(Arrays.asList(orderEntry.getUrls(type)));
        }
      }
    }
  }

  private static boolean orderEntriesEquals(OrderEntry orderEntry1, OrderEntry orderEntry2) {
    if (!((OrderEntryBaseImpl)orderEntry1).sameType(orderEntry2)) return false;
    if (orderEntry1 instanceof JdkOrderEntry) {
      if (!(orderEntry2 instanceof JdkOrderEntry)) return false;
      if (orderEntry1 instanceof InheritedJdkOrderEntry && orderEntry2 instanceof ModuleJdkOrderEntry) {
        return false;
      }
      if (orderEntry2 instanceof InheritedJdkOrderEntry && orderEntry1 instanceof ModuleJdkOrderEntry) {
        return false;
      }
      if (orderEntry1 instanceof ModuleJdkOrderEntry && orderEntry2 instanceof ModuleJdkOrderEntry) {
        String name1 = ((ModuleJdkOrderEntry)orderEntry1).getJdkName();
        String name2 = ((ModuleJdkOrderEntry)orderEntry2).getJdkName();
        if (!name1.equals(name2)) {
          return false;
        }
      }
    }
    if (orderEntry1 instanceof ExportableOrderEntry) {
      if (!(((ExportableOrderEntry)orderEntry1).isExported() == ((ExportableOrderEntry)orderEntry2).isExported())) {
        return false;
      }
    }
    if (orderEntry1 instanceof ModuleOrderEntry) {
      LOG.assertTrue(orderEntry2 instanceof ModuleOrderEntryImpl);
      final String name1 = ((ModuleOrderEntryImpl)orderEntry1).getModuleName();
      final String name2 = ((ModuleOrderEntryImpl)orderEntry2).getModuleName();
      return Comparing.equal(name1, name2);
    }

    if (orderEntry1 instanceof LibraryOrderEntry) {
      LOG.assertTrue(orderEntry2 instanceof LibraryOrderEntry);
      LibraryOrderEntry libraryOrderEntry1 = (LibraryOrderEntry)orderEntry1;
      LibraryOrderEntry libraryOrderEntry2 = (LibraryOrderEntry)orderEntry2;
      boolean equal = Comparing.equal(libraryOrderEntry1.getLibraryName(), libraryOrderEntry2.getLibraryName())
                      && Comparing.equal(libraryOrderEntry1.getLibraryLevel(), libraryOrderEntry2.getLibraryLevel());
      if (!equal) return false;
    }

    final OrderRootType[] allTypes = OrderRootType.ALL_TYPES;
    for (int i = 0; i < allTypes.length; i++) {
      OrderRootType type = allTypes[i];
      final String[] orderedRootUrls1 = orderEntry1.getUrls(type);
      final String[] orderedRootUrls2 = orderEntry2.getUrls(type);
      if (!Arrays.equals(orderedRootUrls1, orderedRootUrls2)) {
        return false;
      }
    }
    return true;
  }

  void fireBeforeExternalChange() {
    if (myWritable || myDisposed) return;
    myModuleRootManager.fireBeforeRootsChange();
  }

  void fireAfterExternalChange() {
    if (myWritable || myDisposed) return;
    myModuleRootManager.fireRootsChanged();
  }

  public void dispose() {
    assertWritable();
    disposeModel();
    myWritable = false;
  }

  boolean isDisposed() {
    return myDisposed;
  }

  void disposeModel() {
    final RootModelComponentBase[] rootModelComponentBases = myComponents.toArray(
      new RootModelComponentBase[myComponents.size()]);
    for (int i = 0; i < rootModelComponentBases.length; i++) {
      RootModelComponentBase rootModelComponentBase = rootModelComponentBases[i];
      rootModelComponentBase.dispose();
    }

    for (Iterator<VirtualFilePointer> iterator = myPointersToDispose.iterator(); iterator.hasNext();) {
      VirtualFilePointer pointer = iterator.next();
      myFilePointerManager.kill(pointer);
    }
    myDisposed = true;
  }

  public boolean isExcludeOutput() { return myExcludeOutput; }

  public boolean isExcludeExplodedDirectory() { return myExcludeExploded; }

  public void setExcludeOutput(boolean excludeOutput) { myExcludeOutput = excludeOutput; }

  public void setExcludeExplodedDirectory(boolean excludeExplodedDir) { myExcludeExploded = excludeExplodedDir; }

  private void annotatePointer(VirtualFilePointer pointer) {
    pointer.putUserData(ORIGINATING_ROOT_MODEL, this);
    myPointersToDispose.add(pointer);
  }

  private class Order extends ArrayList<OrderEntry> {
    public OrderEntry set(int i, OrderEntry orderEntry) {
      super.set(i, orderEntry);
      ((OrderEntryBaseImpl)orderEntry).setIndex(i);
      return orderEntry;
    }

    public boolean add(OrderEntry orderEntry) {
      super.add(orderEntry);
      ((OrderEntryBaseImpl)orderEntry).setIndex(size() - 1);
      return true;
    }

    public void add(int i, OrderEntry orderEntry) {
      super.add(i, orderEntry);
      setIndicies(i);
    }

    public OrderEntry remove(int i) {
      OrderEntry entry = super.remove(i);
      setIndicies(i);
      if (myComponents.contains(entry)) {
        ((RootModelComponentBase)entry).dispose();
      }
      return entry;
    }

    public boolean remove(Object o) {
      int index = indexOf(o);
      if (index < 0) return false;
      remove(index);
      return true;
    }

    public boolean addAll(Collection<? extends OrderEntry> collection) {
      int startSize = size();
      boolean result = super.addAll(collection);
      setIndicies(startSize);
      return result;
    }

    public boolean addAll(int i, Collection<? extends OrderEntry> collection) {
      boolean result = super.addAll(i, collection);
      setIndicies(i);
      return result;
    }

    public void removeRange(int i, int i1) {
      super.removeRange(i, i1);
      setIndicies(i);
    }

    public boolean removeAll(Collection<?> collection) {
      boolean result = super.removeAll(collection);
      setIndicies(0);
      return result;
    }

    public boolean retainAll(Collection<?> collection) {
      boolean result = super.retainAll(collection);
      setIndicies(0);
      return result;
    }

    private void setIndicies(int startIndex) {
      for (int j = startIndex; j < size(); j++) {
        ((OrderEntryBaseImpl)get(j)).setIndex(j);
      }
    }
  }

  public String[] getDependencyModuleNames() {
    List<String> result = processOrder(new CollectDependentModules(), new ArrayList<String>());
    return result.toArray(new String[result.size()]);
  }

  public Module[] getModuleDependencies() {
    final List<Module> result = new ArrayList<Module>();

    for (int i = 0; i < myOrder.size(); i++) {
      OrderEntry entry = myOrder.get(i);
      if (entry instanceof ModuleOrderEntry) {
        final Module module1 = ((ModuleOrderEntry)entry).getModule();
        if (module1 != null) {
          result.add(module1);
        }
      }
    }

    return result.toArray(new Module[result.size()]);
  }

  VirtualFilePointerFactory pointerFactory() {
    return myVirtualFilePointerFactory;
  }

  private RootModelImpl getSourceModel() {
    assertWritable();
    return myModuleRootManager.getRootModel();
  }


  private static class CollectDependentModules extends RootPolicy<List<String>> {
    public List<String> visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, List<String> arrayList) {
      arrayList.add(moduleOrderEntry.getModuleName());
      return arrayList;
    }
  }


  public VirtualFile[] getJavadocPaths() {
    return myJavadocPointerContainer.getDirectories();
  }

  public String[] getJavadocUrls() {
    return myJavadocPointerContainer.getUrls();
  }

  public void setJavadocUrls(String[] urls) {
    assertWritable();
    myJavadocPointerContainer.clear();
    for (int i = 0; i < urls.length; i++) {
      final String url = urls[i];
      myJavadocPointerContainer.add(url);
    }
  }
}
