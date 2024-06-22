package com.intellij.util.indexing;

import com.intellij.AppTopics;
import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.CollectingContentIterator;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentTransactionListener;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.io.IOUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */

public class FileBasedIndex implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.FileBasedIndex");
  @NonNls
  private static final String CORRUPTION_MARKER_NAME = "corruption.marker";
  private final Map<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>> myIndices = new HashMap<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>>();
  private final Map<ID<?, ?>, Semaphore> myUnsavedDataIndexingSemaphores = new HashMap<ID<?,?>, Semaphore>();
  private final TObjectIntHashMap<ID<?, ?>> myIndexIdToVersionMap = new TObjectIntHashMap<ID<?, ?>>();
  private final Set<ID<?, ?>> myNeedContentLoading = new HashSet<ID<?, ?>>();

  private final PerIndexDocumentMap<Long> myLastIndexedDocStamps = new PerIndexDocumentMap<Long>() {
    @Override
    protected Long createDefault(Document document) {
      return 0L;
    }
  };
  private final PerIndexDocumentMap<CharSequence> myLastIndexedUnsavedContent = new PerIndexDocumentMap<CharSequence>() {
    @Override
    protected CharSequence createDefault(Document document) {
      return loadContent(FileDocumentManager.getInstance().getFile(document));
    }
  };

  private final ChangedFilesUpdater myChangedFilesUpdater;

  private final List<IndexableFileSet> myIndexableSets = new CopyOnWriteArrayList<IndexableFileSet>();

  public static final int OK = 1;
  public static final int REQUIRES_REBUILD = 2;
  public static final int REBUILD_IN_PROGRESS = 3;
  private final Map<ID<?, ?>, AtomicInteger> myRebuildStatus = new HashMap<ID<?,?>, AtomicInteger>();

  private final VirtualFileManagerEx myVfManager;
  private final ConcurrentHashSet<ID<?, ?>> myUpToDateIndices = new ConcurrentHashSet<ID<?, ?>>();
  private final Map<Document, PsiFile> myTransactionMap = new HashMap<Document, PsiFile>();

  private final FileContentStorage myFileContentAttic;
  private final Map<ID<?, ?>, FileBasedIndexExtension<?, ?>> myExtentions = new HashMap<ID<?,?>, FileBasedIndexExtension<?,?>>();

  private static final int ALREADY_PROCESSED = 0x02;

  public void requestReindex(final VirtualFile file) {
    myChangedFilesUpdater.scheduleInvalidation(file, true);
  }

  public interface InputFilter {
    boolean acceptInput(VirtualFile file);
  }

  public FileBasedIndex(final VirtualFileManagerEx vfManager, MessageBus bus) throws IOException {
    myVfManager = vfManager;

    final MessageBusConnection connection = bus.connect();
    connection.subscribe(PsiDocumentTransactionListener.TOPIC, new PsiDocumentTransactionListener() {
      public void transactionStarted(final Document doc, final PsiFile file) {
        if (file != null) {
          myTransactionMap.put(doc, file);
          myUpToDateIndices.clear();
        }
      }

      public void transactionCompleted(final Document doc, final PsiFile file) {
        myTransactionMap.remove(doc);
      }
    });

    connection.subscribe(AppTopics.FILE_TYPES, new FileTypeListener() {
      public void beforeFileTypesChanged(final FileTypeEvent event) {
        cleanupProcessedFlag();
        // TODO: temporary solution for tests to avoid 'twin stubs' problem
        // Correct index update will require more information from FileType events
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          requestRebuild(StubUpdatingIndex.INDEX_ID);
        }
      }

      public void fileTypesChanged(final FileTypeEvent event) {
      }
    });

    ApplicationManager.getApplication().addApplicationListener(new ApplicationAdapter() {
      public void writeActionStarted(Object action) {
        myUpToDateIndices.clear();
      }
    });

    final File workInProgressFile = getMarkerFile();
    if (workInProgressFile.exists()) {
      // previous IDEA session was closed incorrectly, so drop all indices
      FileUtil.delete(PathManager.getIndexRoot());
    }

    try {
      final FileBasedIndexExtension[] extensions = Extensions.getExtensions(FileBasedIndexExtension.EXTENSION_POINT_NAME);
      for (FileBasedIndexExtension<?, ?> extension : extensions) {
        myRebuildStatus.put(extension.getName(), new AtomicInteger(OK));
      }

      final File corruptionMarker = new File(PathManager.getIndexRoot(), CORRUPTION_MARKER_NAME);
      final boolean currentVersionCorrupted = corruptionMarker.exists();
      for (FileBasedIndexExtension<?, ?> extension : extensions) {
        registerIndexer(extension, currentVersionCorrupted);
      }
      FileUtil.delete(corruptionMarker);
      dropUnregisteredIndices();

      // check if rebuild was requested for any index during registration
      for (ID<?, ?> indexId : myIndices.keySet()) {
        if (myRebuildStatus.get(indexId).compareAndSet(REQUIRES_REBUILD, OK)) {
          try {
            clearIndex(indexId);
          }
          catch (StorageException e) {
            requestRebuild(indexId);
            LOG.error(e);
          }
        }
      }

      myChangedFilesUpdater = new ChangedFilesUpdater();
      vfManager.addVirtualFileListener(myChangedFilesUpdater);
      vfManager.registerRefreshUpdater(myChangedFilesUpdater);
      myFileContentAttic = new FileContentStorage(new File(PathManager.getIndexRoot(), "updates.tmp"));
    }
    finally {
      workInProgressFile.createNewFile();
      saveRegisteredInices(myIndices.keySet());
    }
  }

  private static FileBasedIndex ourInstance = CachedSingletonsRegistry.markCachedField(FileBasedIndex.class);
  public static FileBasedIndex getInstance() {
    if (ourInstance == null) {
      ourInstance = ApplicationManager.getApplication().getComponent(FileBasedIndex.class);
    }

    return ourInstance;
  }

  /**
   * @return true if registered index requires full rebuild for some reason, e.g. is just created or corrupted @param extension
   * @param isCurrentVersionCorrupted
   */
  private <K, V> void registerIndexer(final FileBasedIndexExtension<K, V> extension, final boolean isCurrentVersionCorrupted) throws IOException {
    final ID<K, V> name = extension.getName();
    final int version = extension.getVersion();
    if (extension.dependsOnFileContent()) {
      myNeedContentLoading.add(name);
    }
    myIndexIdToVersionMap.put(name, version);
    final File versionFile = IndexInfrastructure.getVersionFile(name);
    if (isCurrentVersionCorrupted || IndexInfrastructure.versionDiffers(versionFile, version)) {
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(name));
      IndexInfrastructure.rewriteVersion(versionFile, version);
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final MapIndexStorage<K, V> storage = new MapIndexStorage<K, V>(IndexInfrastructure.getStorageFile(name), extension.getKeyDescriptor(), extension.getValueExternalizer(), extension.getCacheSize());
        final IndexStorage<K, V> memStorage = new MemoryIndexStorage<K, V>(storage);
        final UpdatableIndex<K, V, FileContent> index = createIndex(name, extension, memStorage);
        myIndices.put(name, new Pair<UpdatableIndex<?,?, FileContent>, InputFilter>(index, new IndexableFilesFilter(extension.getInputFilter())));
        myUnsavedDataIndexingSemaphores.put(name, new Semaphore());
        myExtentions.put(name, extension);
        break;
      }
      catch (IOException e) {
        FileUtil.delete(IndexInfrastructure.getIndexRootDir(name));
        IndexInfrastructure.rewriteVersion(versionFile, version);
      }
    }
  }

  private static void saveRegisteredInices(Collection<ID<?, ?>> ids) {
    final File file = getRegisteredIndicesFile();
    try {
      file.getParentFile().mkdirs();
      file.createNewFile();
      final DataOutputStream os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
      try {
        os.writeInt(ids.size());
        for (ID<?, ?> id : ids) {
          IOUtil.writeString(id.toString(), os);
        }
      }
      finally {
        os.close();
      }
    }
    catch (IOException ignored) {
    }
  }

  private static Set<String> readRegistsredIndexNames() {
    final Set<String> result = new HashSet<String>();
    try {
      final DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(getRegisteredIndicesFile())));
      try {
        final int size = in.readInt();
        for (int idx = 0; idx < size; idx++) {
          result.add(IOUtil.readString(in));
        }
      }
      finally {
        in.close();
      }
    }
    catch (IOException ignored) {
    }
    return result;
  }

  private static File getRegisteredIndicesFile() {
    return new File(PathManager.getIndexRoot(), "registered");
  }

  private static File getMarkerFile() {
    return new File(PathManager.getIndexRoot(), "work_in_progress");
  }

  private <K, V> UpdatableIndex<K, V, FileContent> createIndex(final ID<K, V> indexId, final FileBasedIndexExtension<K, V> extension, final IndexStorage<K, V> storage) {
    if (extension instanceof CustomImplementationFileBasedIndexExtension) {
      return ((CustomImplementationFileBasedIndexExtension<K, V, FileContent>)extension).createIndexImplementation(indexId, this, storage);
    }
    return new MapReduceIndex<K, V, FileContent>(indexId, extension.getIndexer(), storage);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "FileBasedIndex";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myChangedFilesUpdater.forceUpdate();

    for (ID<?, ?> indexId : myIndices.keySet()) {
      final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
      assert index != null;
      index.dispose();
    }

    myVfManager.removeVirtualFileListener(myChangedFilesUpdater);
    myVfManager.unregisterRefreshUpdater(myChangedFilesUpdater);

    FileUtil.delete(getMarkerFile());
  }

  public void flushCaches() {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        for (ID<?, ?> indexId : myIndices.keySet()) {
          //noinspection ConstantConditions
          try {
            getIndex(indexId).flush();
          }
          catch (StorageException e) {
            LOG.info(e);
            requestRebuild(indexId);
          }
        }
      }
    });
  }

  @NotNull
  public <K> Collection<K> getAllKeys(final ID<K, ?> indexId) {
    Set<K> allKeys = new HashSet<K>();
    processAllKeys(indexId, new CommonProcessors.CollectProcessor<K>(allKeys));
    return allKeys;
  }

  public <K> boolean processAllKeys(final ID<K, ?> indexId, Processor<K> processor) {
    try {
      ensureUpToDate(indexId);
      final UpdatableIndex<K, ?, FileContent> index = getIndex(indexId);
      if (index == null) return true;
      return index.processAllKeys(processor);
    }
    catch (StorageException e) {
      scheduleRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        scheduleRebuild(indexId, cause);
      }
      else {
        throw e;
      }
    }

    return false;
  }

  private static final ThreadLocal<Integer> myUpToDateCheckState = new ThreadLocal<Integer>();

  public void disableUpToDateCheckForCurrentThread() {
    final Integer currentValue = myUpToDateCheckState.get();
    myUpToDateCheckState.set(currentValue == null? 1 : currentValue.intValue() + 1);
  }

  public void enableUpToDateCheckForCurrentThread() {
    final Integer currentValue = myUpToDateCheckState.get();
    if (currentValue != null) {
      final int newValue = currentValue.intValue() - 1;
      if (newValue != 0) {
        myUpToDateCheckState.set(newValue);
      }
      else {
        myUpToDateCheckState.remove();
      }
    }
  }

  private boolean isUpToDateCheckEnabled() {
    final Integer value = myUpToDateCheckState.get();
    return value == null || value.intValue() == 0;
  }


  private final ThreadLocal<Boolean> myReentrancyGuard = new ThreadLocal<Boolean>() {
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };

  /**
   * DO NOT CALL DIRECTLY IN CLIENT CODE
   * The method is internal to indexing engine end is called internally. The method is public due to implementation details
   */
  public <K> void ensureUpToDate(final ID<K, ?> indexId) {
    if (myReentrancyGuard.get().booleanValue()) {
      //assert false : "ensureUpToDate() is not reentrant!";
      return;
    }
    myReentrancyGuard.set(Boolean.TRUE);

    try {
      myChangedFilesUpdater.ensureAllInvalidateTasksCompleted();

      if (isUpToDateCheckEnabled()) {
        try {
          checkRebuild(indexId);
          indexUnsavedDocuments(indexId);
        }
        catch (StorageException e) {
          scheduleRebuild(indexId, e);
        }
        catch (RuntimeException e) {
          final Throwable cause = e.getCause();
          if (cause instanceof StorageException || cause instanceof IOException) {
            scheduleRebuild(indexId, e);
          }
          else {
            throw e;
          }
        }
      }
    }
    finally {
      myReentrancyGuard.set(Boolean.FALSE);
    }
  }

  @NotNull
  public <K, V> List<V> getValues(final ID<K, V> indexId, @NotNull K dataKey, final VirtualFileFilter filter) {
    final List<V> values = new ArrayList<V>();
    processValuesImpl(indexId, dataKey, true, null, new ValueProcessor<V>() {
      public void process(final VirtualFile file, final V value) {
        values.add(value);
      }
    }, filter);
    return values;
  }

  @NotNull
  public <K, V> Collection<VirtualFile> getContainingFiles(final ID<K, V> indexId, @NotNull K dataKey, final VirtualFileFilter filter) {
    final Set<VirtualFile> files = new HashSet<VirtualFile>();
    processValuesImpl(indexId, dataKey, false, null, new ValueProcessor<V>() {
      public void process(final VirtualFile file, final V value) {
        files.add(file);
      }
    }, filter);
    return files;
  }


  public interface ValueProcessor<V> {
    void process(VirtualFile file, V value);
  }

  public <K, V> void processValues(final ID<K, V> indexId, final @NotNull K dataKey, @Nullable final VirtualFile inFile,
                                   ValueProcessor<V> processor, final VirtualFileFilter filter) {
    processValuesImpl(indexId, dataKey, false, inFile, processor, filter);
  }

  private <K, V> void processValuesImpl(final ID<K, V> indexId, final K dataKey, boolean ensureValueProcessedOnce,
                                        @Nullable final VirtualFile restrictToFile, ValueProcessor<V> processor,
                                        final VirtualFileFilter filter) {
    try {
      ensureUpToDate(indexId);
      final UpdatableIndex<K, V, FileContent> index = getIndex(indexId);
      if (index == null) {
        return;
      }

      final Lock readLock = index.getReadLock();
      try {
        readLock.lock();
        final ValueContainer<V> container = index.getData(dataKey);

        if (restrictToFile != null) {
          if (!(restrictToFile instanceof VirtualFileWithId)) return;

          final int restrictedFileId = getFileId(restrictToFile);
          for (final Iterator<V> valueIt = container.getValueIterator(); valueIt.hasNext();) {
            final V value = valueIt.next();
            if (container.isAssociated(value, restrictedFileId)) {
              processor.process(restrictToFile, value);
            }
          }
        }
        else {
          final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
          for (final Iterator<V> valueIt = container.getValueIterator(); valueIt.hasNext();) {
            final V value = valueIt.next();
            for (final ValueContainer.IntIterator inputIdsIterator = container.getInputIdsIterator(value); inputIdsIterator.hasNext();) {
              final int id = inputIdsIterator.next();
              VirtualFile file = IndexInfrastructure.findFileById(fs, id);
              if (file != null && filter.accept(file)) {
                processor.process(file, value);
                if (ensureValueProcessedOnce) {
                  break; // continue with the next value
                }
              }
            }
          }
        }
      }
      finally {
        index.getReadLock().unlock();
      }
    }
    catch (StorageException e) {
      scheduleRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        scheduleRebuild(indexId, cause);
      }
      else {
        throw e;
      }
    }
  }

  public interface AllValuesProcessor<V> {
    void process(final int inputId, V value);
  }

  public <K, V> void processAllValues(final ID<K, V> indexId, AllValuesProcessor<V> processor) {
    try {
      ensureUpToDate(indexId);
      final UpdatableIndex<K, V, FileContent> index = getIndex(indexId);
      if (index == null) {
        return;
      }
      try {
        index.getReadLock().lock();
        for (K dataKey : index.getAllKeys()) {
          final ValueContainer<V> container = index.getData(dataKey);
          for (final Iterator<V> it = container.getValueIterator(); it.hasNext();) {
            final V value = it.next();
            for (final ValueContainer.IntIterator inputsIt = container.getInputIdsIterator(value); inputsIt.hasNext();) {
              processor.process(inputsIt.next(), value);
            }
          }
        }
      }
      finally {
        index.getReadLock().unlock();
      }
    }
    catch (StorageException e) {
      scheduleRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        scheduleRebuild(indexId, e);
      }
      else {
        throw e;
      }
    }
  }

  private <K> void scheduleRebuild(final ID<K, ?> indexId, final Throwable e) {
    requestRebuild(indexId);
    LOG.info(e);
    checkRebuild(indexId);
  }

  private void checkRebuild(final ID<?, ?> indexId) {
    if (myRebuildStatus.get(indexId).compareAndSet(REQUIRES_REBUILD, REBUILD_IN_PROGRESS)) {
      cleanupProcessedFlag();

      final Runnable rebuildRunnable = new Runnable() {
        public void run() {

          final FileSystemSynchronizer synchronizer = new FileSystemSynchronizer();
          synchronizer.setCancelable(false);
          for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            synchronizer.registerCacheUpdater(new UnindexedFilesUpdater(project, ProjectRootManager.getInstance(project), FileBasedIndex.this));
          }

          try {
            clearIndex(indexId);
            synchronizer.execute();
          }
          catch (StorageException e) {
            requestRebuild(indexId);
            LOG.info(e);
          }
          finally {
            myRebuildStatus.get(indexId).compareAndSet(REBUILD_IN_PROGRESS, OK);
          }
        }
      };

      final Application application = ApplicationManager.getApplication();
      if (application.isUnitTestMode()) {
        rebuildRunnable.run();
      }
      else {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            new Task.Modal(null, "Updating index", false) {
              public void run(@NotNull final ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                rebuildRunnable.run();
              }
            }.queue();
          }
        });
      }
    }

    if (myRebuildStatus.get(indexId).get() == REBUILD_IN_PROGRESS) {
      throw new ProcessCanceledException();
    }
  }

  private void clearIndex(final ID<?, ?> indexId) throws StorageException {
    final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
    assert index != null;
    index.clear();
    try {
      IndexInfrastructure.rewriteVersion(IndexInfrastructure.getVersionFile(indexId), myIndexIdToVersionMap.get(indexId));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private Set<Document> getUnsavedOrTransactedDocuments() {
    Set<Document> docs = new HashSet<Document>(Arrays.asList(FileDocumentManager.getInstance().getUnsavedDocuments()));
    docs.addAll(myTransactionMap.keySet());
    return docs;
  }

  private void indexUnsavedDocuments(ID<?, ?> indexId) throws StorageException {
    myChangedFilesUpdater.forceUpdate();

    if (myUpToDateIndices.contains(indexId)) {
      return; // no need to index unsaved docs
    }

    final Set<Document> documents = getUnsavedOrTransactedDocuments();
    if (!documents.isEmpty()) {
      // now index unsaved data
      final StorageGuard.Holder guard = setDataBufferingEnabled(true);
      try {
        final Semaphore semaphore = myUnsavedDataIndexingSemaphores.get(indexId);
        semaphore.down();
        try {
          for (Document document : documents) {
            indexUnsavedDocument(document, indexId);
          }
        }
        finally {
          semaphore.up();

          while (!semaphore.waitFor(500)) { // may need to wait until another thread is done with indexing
            if (Thread.holdsLock(PsiLock.LOCK)) {
              break; // hack. Most probably that other indexing threads is waiting for PsiLock, which we're are holding.
            }
          }
          myUpToDateIndices.add(indexId); // safe to set the flag here, becase it will be cleared under the WriteAction
        }
      }
      finally {
        guard.leave();
      }
    }
  }

  private interface DocumentContent {
    String getText();
    long getModificationStamp();
  }

  private static class AuthenticContent implements DocumentContent {
    private final Document myDocument;

    private AuthenticContent(final Document document) {
      myDocument = document;
    }

    public String getText() {
      return myDocument.getText();
    }

    public long getModificationStamp() {
      return myDocument.getModificationStamp();
    }
  }

  private static class PsiContent implements DocumentContent {
    private final Document myDocument;
    private final PsiFile myFile;

    private PsiContent(final Document document, final PsiFile file) {
      myDocument = document;
      myFile = file;
    }

    public String getText() {
      if (myFile.getModificationStamp() != myDocument.getModificationStamp()) {
        final ASTNode node = myFile.getNode();
        assert node != null;
        return node.getText();
      }
      return myDocument.getText();
    }

    public long getModificationStamp() {
      return myFile.getModificationStamp();
    }
  }

  private void indexUnsavedDocument(final Document document, final ID<?, ?> requestedIndexId) throws StorageException {
    final VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
    if (!(vFile instanceof VirtualFileWithId) || !vFile.isValid()) {
      return;
    }

    PsiFile dominantContentFile = findDominantPsiForDocument(document);

    DocumentContent content;
    if (dominantContentFile != null && dominantContentFile.getModificationStamp() != document.getModificationStamp()) {
      content = new PsiContent(document, dominantContentFile);
    }
    else {
      content = new AuthenticContent(document);
    }

    final long currentDocStamp = content.getModificationStamp();
    if (currentDocStamp != myLastIndexedDocStamps.getAndSet(document, requestedIndexId, currentDocStamp).longValue()) {
      final FileContent newFc = new FileContent(vFile, content.getText(), vFile.getCharset());

      if (dominantContentFile != null) {
        dominantContentFile.putUserData(PsiFileImpl.BUILDING_STUB, true);
        newFc.putUserData(PSI_FILE, dominantContentFile);
      }

      if (content instanceof AuthenticContent) {
        newFc.putUserData(EDITOR_HIGHLIGHTER, document instanceof DocumentImpl
                                              ? ((DocumentImpl)document).getEditorHighlighterForCachesBuilding() : null);
      }

      CharSequence lastIndexed = myLastIndexedUnsavedContent.get(document, requestedIndexId);
      final FileContent oldFc = new FileContent(vFile, lastIndexed, vFile.getCharset());
      if (getInputFilter(requestedIndexId).acceptInput(vFile)) {
        final int inputId = Math.abs(getFileId(vFile));
        getIndex(requestedIndexId).update(inputId, newFc, oldFc);
      }

      if (dominantContentFile != null) {
        dominantContentFile.putUserData(PsiFileImpl.BUILDING_STUB, null);
      }

      myLastIndexedUnsavedContent.put(document, requestedIndexId, newFc.getContentAsText());
    }
  }

  public static final Key<PsiFile> PSI_FILE = new Key<PsiFile>("PSI for stubs");
  public static final Key<EditorHighlighter> EDITOR_HIGHLIGHTER = new Key<EditorHighlighter>("Editor");
  public static final Key<Project> PROJECT = new Key<Project>("Context project");
  public static final Key<VirtualFile> VIRTUAL_FILE = new Key<VirtualFile>("Context virtual file");

  @Nullable
  private PsiFile findDominantPsiForDocument(final Document document) {
    if (myTransactionMap.containsKey(document)) {
      return myTransactionMap.get(document);
    }

    return findLatestKnownPsiForUncomittedDocument(document);
  }

  private final StorageGuard myStorageLock = new StorageGuard();

  private StorageGuard.Holder setDataBufferingEnabled(final boolean enabled) {
    final StorageGuard.Holder holder = myStorageLock.enter(enabled);
    if (!enabled) {
      synchronized (myLastIndexedDocStamps) {
        myLastIndexedDocStamps.clear();
        myLastIndexedUnsavedContent.clear();
      }
    }
    for (ID<?, ?> indexId : myIndices.keySet()) {
      final MapReduceIndex index = (MapReduceIndex)getIndex(indexId);
      assert index != null;
      final IndexStorage indexStorage = index.getStorage();
      ((MemoryIndexStorage)indexStorage).setBufferingEnabled(enabled);
    }
    return holder;
  }

  private void dropUnregisteredIndices() {
    final Set<String> indicesToDrop = readRegistsredIndexNames();
    for (ID<?, ?> key : myIndices.keySet()) {
      indicesToDrop.remove(key.toString());
    }
    for (String s : indicesToDrop) {
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(ID.create(s)));
    }
  }

  public void requestRebuild(ID<?, ?> indexId) {
    cleanupProcessedFlag();
    LOG.info("Rebuild requested for index " + indexId, new Throwable());
    myRebuildStatus.get(indexId).set(REQUIRES_REBUILD);
  }

  private <K, V> UpdatableIndex<K, V, FileContent> getIndex(ID<K, V> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);
    //noinspection unchecked
    return pair != null? (UpdatableIndex<K,V, FileContent>)pair.getFirst() : null;
  }

  private InputFilter getInputFilter(ID<?, ?> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);
    return pair != null? pair.getSecond() : null;
  }

  public void indexFileContent(com.intellij.ide.startup.FileContent content) {
    myChangedFilesUpdater.ensureAllInvalidateTasksCompleted();
    final VirtualFile file = content.getVirtualFile();
    FileContent fc = null;
    FileContent oldContent = null;
    final byte[] oldBytes = myFileContentAttic.remove(file);
    final boolean forceIndexing = oldBytes != null;

    PsiFile psiFile = null;
    for (ID<?, ?> indexId : myIndices.keySet()) {
      if (forceIndexing ? getInputFilter(indexId).acceptInput(file) : shouldIndexFile(file, indexId)) {
        if (fc == null) {
          byte[] currentBytes;
          try {
            currentBytes = content.getBytes();
          }
          catch (IOException e) {
            currentBytes = ArrayUtil.EMPTY_BYTE_ARRAY;
          }
          fc = new FileContent(file, currentBytes);

          psiFile = content.getUserData(PSI_FILE);
          if (psiFile != null) {
            psiFile.putUserData(PsiFileImpl.BUILDING_STUB, true);
            fc.putUserData(PSI_FILE, psiFile);
          }
          Project project = content.getUserData(PROJECT);
          fc.putUserData(PROJECT, project);
          oldContent = oldBytes != null? new FileContent(file, oldBytes) : null;
        }

        try {
          updateSingleIndex(indexId, file, fc, oldContent);
        }
        catch (StorageException e) {
          requestRebuild(indexId);
          LOG.info(e);
        }
      }
    }

    if (psiFile != null) {
      psiFile.putUserData(PsiFileImpl.BUILDING_STUB, null);
    }

    IndexingStamp.flushCache();
  }

  private void updateSingleIndex(final ID<?, ?> indexId, final VirtualFile file, final FileContent currentFC, final FileContent oldFC)
    throws StorageException {

    final StorageGuard.Holder lock = setDataBufferingEnabled(false);

    try {
      final int inputId = Math.abs(getFileId(file));

      final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
      assert index != null;

      index.update(inputId, currentFC, oldFC);
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          if (file.isValid()) {
            if (currentFC != null) {
              IndexingStamp.update(file, indexId, IndexInfrastructure.getIndexCreationStamp(indexId));
            }
            else {
              // mark the file as unindexed
              IndexingStamp.update(file, indexId, -1L);
            }
          }
        }
      });
    }
    finally {
      lock.leave();
    }
  }

  public static int getFileId(final VirtualFile file) {
    if (file instanceof VirtualFileWithId) {
      return ((VirtualFileWithId)file).getId();
    }

    throw new IllegalArgumentException("Virtual file doesn't support id: " + file + ", implementation class: " + file.getClass().getName());
  }

  private static CharSequence loadContent(VirtualFile file) {
    return LoadTextUtil.loadText(file, true);
  }

  private abstract static class InvalidationTask implements Runnable {
    private final VirtualFile mySubj;

    protected InvalidationTask(final VirtualFile subj) {
      mySubj = subj;
    }

    public VirtualFile getSubj() {
      return mySubj;
    }
  }

  private final class ChangedFilesUpdater extends VirtualFileAdapter implements CacheUpdater{
    private final Set<VirtualFile> myFilesToUpdate = Collections.synchronizedSet(new HashSet<VirtualFile>());
    private final Queue<InvalidationTask> myFutureInvalidations = new LinkedList<InvalidationTask>();
    private final ManagingFS myManagingFS = ManagingFS.getInstance();
    // No need to react on movement events since files stay valid, their ids don't change and all associated attributes remain intact.

    public void fileCreated(final VirtualFileEvent event) {
      markDirty(event);
    }

    public void fileDeleted(final VirtualFileEvent event) {
      myFilesToUpdate.remove(event.getFile()); // no need to update it anymore
    }

    public void fileCopied(final VirtualFileCopyEvent event) {
      markDirty(event);
    }

    public void beforeFileDeletion(final VirtualFileEvent event) {
      scheduleInvalidation(event.getFile(), false);
    }

    public void beforeContentsChange(final VirtualFileEvent event) {
      scheduleInvalidation(event.getFile(), true);
    }

    public void contentsChanged(final VirtualFileEvent event) {
      markDirty(event);
    }

    public void beforePropertyChange(final VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        // indexes may depend on file name
        final VirtualFile file = event.getFile();
        if (!file.isDirectory()) {
          // name change may lead to filetype change so the file might become not indexable
          // in general case have to 'unindex' the file and index it again if needed after the name has been changed
          scheduleInvalidation(file, false);
        }
      }
    }

    public void propertyChanged(final VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        // indexes may depend on file name
        if (!event.getFile().isDirectory()) {
          markDirty(event);
        }
      }
    }

    private void markDirty(final VirtualFileEvent event) {
      cleanProcessedFlag(event.getFile());
      iterateIndexableFiles(event.getFile(), new Processor<VirtualFile>() {
        public boolean process(final VirtualFile file) {
          FileContent fileContent = null;
          final boolean isTooLarge = SingleRootFileViewProvider.isTooLarge(file);
          for (ID<?, ?> indexId : myIndices.keySet()) {
            if (getInputFilter(indexId).acceptInput(file)) {
              if (myNeedContentLoading.contains(indexId)) {
                if (!isTooLarge) {
                  myFilesToUpdate.add(file);
                }
              }
              else {
                try {
                  if (fileContent == null) {
                    fileContent = new FileContent(file);
                  }
                  updateSingleIndex(indexId, file, fileContent, null);
                }
                catch (StorageException e) {
                  LOG.info(e);
                  requestRebuild(indexId);
                }
              }
            }
          }

          IndexingStamp.flushCache();
          return true;
        }
      });
    }

    public void scheduleInvalidation(final VirtualFile file, final boolean saveContent) {
      if (file.isDirectory()) {
        if (isMock(file) || myManagingFS.wereChildrenAccessed(file)) {
          for (VirtualFile child : file.getChildren()) {
            scheduleInvalidation(child, saveContent);
          }
        }
      }
      else {
        cleanProcessedFlag(file);

        final boolean isTooLarge = SingleRootFileViewProvider.isTooLarge(file);
        final List<ID<?, ?>> affectedIndices = new ArrayList<ID<?, ?>>(myIndices.size());
        FileContent fileContent = null;

        //final int fileId = getFileId(file);
        //LOG.assertTrue(fileId >= 0);
        //synchronized (myInvalidationInProgress) {
        //  myInvalidationInProgress.add(fileId);
        //}

        for (ID<?, ?> indexId : myIndices.keySet()) {
          if (shouldUpdateIndex(file, indexId)) {
            if (myNeedContentLoading.contains(indexId)) {
              if (!isTooLarge) {
                affectedIndices.add(indexId);
              }
            }
            else {
              // invalidate it synchronously
              try {
                if (fileContent == null) {
                  fileContent = new FileContent(file);
                }
                updateSingleIndex(indexId, file, null, fileContent);
              }
              catch (StorageException e) {
                LOG.info(e);
                requestRebuild(indexId);
              }
            }
          }
        }
        IndexingStamp.flushCache();

        if (!affectedIndices.isEmpty()) {
          if (saveContent) {
            myFileContentAttic.offer(file);
            iterateIndexableFiles(file, new Processor<VirtualFile>() {
              public boolean process(final VirtualFile file) {
                myFilesToUpdate.add(file);
                return true;
              }
            });
          }
          else {
            // first check if there is an unprocessed content from previous events
            byte[] content = myFileContentAttic.remove(file);
            try {
              if (content == null) {
                content = file.contentsToByteArray();
              }
            }
            catch (IOException e) {
              content = ArrayUtil.EMPTY_BYTE_ARRAY;
            }
            final FileContent fc = new FileContent(file, content);
            synchronized (myFutureInvalidations) {
              final InvalidationTask invalidator = new InvalidationTask(file) {
                public void run() {
                  Throwable unexpectedError = null;
                  for (ID<?, ?> indexId : affectedIndices) {
                    try {
                      updateSingleIndex(indexId, file, null, fc);
                    }
                    catch (StorageException e) {
                      LOG.info(e);
                      requestRebuild(indexId);
                    }
                    catch (ProcessCanceledException ignored) {
                    }
                    catch (Throwable e) {
                      LOG.info(e);
                      if (unexpectedError == null) {
                        unexpectedError = e;
                      }
                    }
                  }

                  IndexingStamp.flushCache();
                  if (unexpectedError != null) {
                    LOG.error(unexpectedError);
                  }
                }
              };

              myFutureInvalidations.offer(invalidator);
            }
          }
        }
      }
    }

    public void ensureAllInvalidateTasksCompleted() {
      final boolean doProgressThing;

      final int size;
      synchronized (myFutureInvalidations) {
        size = myFutureInvalidations.size();
        if (size == 0) return;

        doProgressThing = size > 1 && ApplicationManager.getApplication().isDispatchThread();
      }

      final Task.Modal task = new Task.Modal(null, "Invalidating Index Entries", false) {
        public void run(@NotNull final ProgressIndicator indicator) {
          indicator.setText("");
          int count = 0;
          while (true) {
            InvalidationTask r;
            synchronized (myFutureInvalidations) {
              r = myFutureInvalidations.poll();
            }

            if (r == null) return;
            indicator.setFraction(((double)count++)/size);
            indicator.setText2(r.getSubj().getPresentableUrl());
            r.run();
          }
        }
      };

      if (doProgressThing) {
        task.queue();
      }
      else {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        task.run(indicator != null ? indicator : new EmptyProgressIndicator());
      }
    }

    private void iterateIndexableFiles(final VirtualFile file, final Processor<VirtualFile> processor) {
      if (file.isDirectory()) {
        final ContentIterator iterator = new ContentIterator() {
          public boolean processFile(final VirtualFile fileOrDir) {
            if (!fileOrDir.isDirectory()) {
              processor.process(fileOrDir);
            }
            return true;
          }
        };

        for (IndexableFileSet set : myIndexableSets) {
          set.iterateIndexableFilesIn(file, iterator);
        }
      }
      else {
        for (IndexableFileSet set : myIndexableSets) {
          if (set.isInSet(file)) {
            processor.process(file);
            break;
          }
        }
      }
    }

    public VirtualFile[] queryNeededFiles() {
      synchronized (myFilesToUpdate) {
        return myFilesToUpdate.toArray(new VirtualFile[myFilesToUpdate.size()]);
      }
    }

    public void processFile(final com.intellij.ide.startup.FileContent fileContent) {
      ensureAllInvalidateTasksCompleted();
      processFileImpl(fileContent);
    }

    private final Semaphore myForceUpdateSemaphore = new Semaphore();

    public void forceUpdate() {
      ensureAllInvalidateTasksCompleted();

      final VirtualFile[] files = queryNeededFiles();
      if (files.length > 0) {
        myForceUpdateSemaphore.down();
        try {
          for (VirtualFile file: files) {
            processFileImpl(new com.intellij.ide.startup.FileContent(file));
          }
        }
        finally {
          myForceUpdateSemaphore.up();
          myForceUpdateSemaphore.waitFor(); // possibly wait until another thread completes indexing
        }
      }
    }

    public void updatingDone() {
    }

    public void canceled() {
    }
    
    private void processFileImpl(final com.intellij.ide.startup.FileContent fileContent) {
      final VirtualFile file = fileContent.getVirtualFile();
      final boolean reallyRemoved = myFilesToUpdate.remove(file);
      if (reallyRemoved && file.isValid()) {
        indexFileContent(fileContent);
      }
    }
  }

  private class UnindexedFilesFinder implements CollectingContentIterator {
    private final List<VirtualFile> myFiles = new ArrayList<VirtualFile>();
    private final Collection<ID<?, ?>> myIndexIds;
    private final Collection<ID<?, ?>> mySkipContentLoading;
    private final ProgressIndicator myProgressIndicator;

    private UnindexedFilesFinder(final Collection<ID<?, ?>> indexIds) {
      myIndexIds = new ArrayList<ID<?, ?>>();
      mySkipContentLoading = new ArrayList<ID<?, ?>>();
      for (ID<?, ?> indexId : indexIds) {
        if (myNeedContentLoading.contains(indexId))  {
          myIndexIds.add(indexId);
        }
        else {
          mySkipContentLoading.add(indexId);
        }
      }
      myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
    }

    public List<VirtualFile> getFiles() {
      return myFiles;
    }

    public boolean processFile(final VirtualFile file) {
      if (!file.isDirectory()) {
        if (file instanceof NewVirtualFile && ((NewVirtualFile)file).getFlag(ALREADY_PROCESSED)) {
          return true;
        }

        if (file instanceof VirtualFileWithId) {
          boolean oldStuff = true;
          if (!SingleRootFileViewProvider.isTooLarge(file)) {
            for (ID<?, ?> indexId : myIndexIds) {
              try {
                if (myFileContentAttic.containsContent(file) ? getInputFilter(indexId).acceptInput(file) : shouldIndexFile(file, indexId)) {
                  myFiles.add(file);
                  oldStuff = false;
                  break;
                }
              }
              catch (RuntimeException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof IOException || cause instanceof StorageException) {
                  LOG.info(e);
                  requestRebuild(indexId);
                }
                else {
                  throw e;
                }
              }
            }
          }
          FileContent fileContent = null;
          for (ID<?, ?> indexId : mySkipContentLoading) {
            if (shouldIndexFile(file, indexId)) {
              oldStuff = false;
              try {
                if (fileContent == null) {
                  fileContent = new FileContent(file);
                }
                updateSingleIndex(indexId, file, fileContent, null);
              }
              catch (StorageException e) {
                LOG.info(e);
                requestRebuild(indexId);
              }
            }
          }
          IndexingStamp.flushCache();

          if (oldStuff && file instanceof NewVirtualFile) {
            ((NewVirtualFile)file).setFlag(ALREADY_PROCESSED, true);
          }
        }
      }
      else {
        if (myProgressIndicator != null) {
          myProgressIndicator.setText("Scanning files to index");
          myProgressIndicator.setText2(file.getPresentableUrl());
        }
      }
      return true;
    }
  }

  private boolean shouldUpdateIndex(final VirtualFile file, final ID<?, ?> indexId) {
    return getInputFilter(indexId).acceptInput(file) &&
           (isMock(file) || IndexingStamp.isFileIndexed(file, indexId, IndexInfrastructure.getIndexCreationStamp(indexId)));
  }

  private boolean shouldIndexFile(final VirtualFile file, final ID<?, ?> indexId) {
    return getInputFilter(indexId).acceptInput(file) &&
           (isMock(file) || !IndexingStamp.isFileIndexed(file, indexId, IndexInfrastructure.getIndexCreationStamp(indexId)));
  }

  private static boolean isMock(final VirtualFile file) {
    return !(file instanceof NewVirtualFile);
  }

  public CollectingContentIterator createContentIterator() {
    return new UnindexedFilesFinder(myIndices.keySet());
  }

  public void registerIndexableSet(IndexableFileSet set) {
    myIndexableSets.add(set);
  }

  public void removeIndexableSet(IndexableFileSet set) {
    myChangedFilesUpdater.forceUpdate();
    myIndexableSets.remove(set);
  }

  @Nullable
  private static PsiFile findLatestKnownPsiForUncomittedDocument(Document doc) {
    PsiFile target = null;
    long modStamp = -1L;

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      final PsiDocumentManager pdm = PsiDocumentManager.getInstance(project);
      final PsiFile file = pdm.getCachedPsiFile(doc);
      if (file != null && file.getModificationStamp() > modStamp) {
        final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        final VirtualFile vFile = file.getVirtualFile();
        if (vFile != null && (index.isInContent(vFile) || index.isInLibrarySource(vFile))) {
          target = file;
          modStamp = file.getModificationStamp();
        }
      }
      else if (file != null && file.getModificationStamp() == modStamp) {
        if (target instanceof PsiPlainTextFile && !(file instanceof PsiPlainTextFile)) {
          target = file;
        }
      }
    }

    return target;
  }
  
  private static class IndexableFilesFilter implements InputFilter {
    private final InputFilter myDelegate;

    private IndexableFilesFilter(InputFilter delegate) {
      myDelegate = delegate;
    }

    public boolean acceptInput(final VirtualFile file) {
      return file instanceof VirtualFileWithId && myDelegate.acceptInput(file);
    }
  }

  private static void cleanupProcessedFlag() {
    final VirtualFile[] roots = ManagingFS.getInstance().getRoots();
    for (VirtualFile root : roots) {
      cleanProcessedFlag(root);
    }
  }

  private static void cleanProcessedFlag(final VirtualFile file) {
    if (!(file instanceof NewVirtualFile)) return;
    
    final NewVirtualFile nvf = (NewVirtualFile)file;
    if (file.isDirectory()) {
      for (VirtualFile child : nvf.getCachedChildren()) {
        cleanProcessedFlag(child);
      }
    }
    else {
/*      nvf.clearCachedFileType(); */
      nvf.setFlag(ALREADY_PROCESSED, false);
    }
  }

  private static class StorageGuard {
    private int myHolds = 0;

    public interface Holder {
      void leave();
    }

    private final Holder myTrueHolder = new Holder() {
      public void leave() {
        StorageGuard.this.leave(true);
      }
    };
    private final Holder myFalseHolder = new Holder() {
      public void leave() {
        StorageGuard.this.leave(false);
      }
    };

    public synchronized Holder enter(boolean mode) {
      if (mode) {
        while (myHolds < 0) {
          try {
            wait();
          }
          catch (InterruptedException ignored) {
          }
        }
        myHolds++;
        return myTrueHolder;
      }
      else {
        while (myHolds > 0) {
          try {
            wait();
          }
          catch (InterruptedException ignored) {
          }
        }
        myHolds--;
        return myFalseHolder;
      }
    }

    private synchronized void leave(boolean mode) {
      myHolds += (mode? -1 : 1);
      if (myHolds == 0) {
        notifyAll();
      }
    }

  }
}
