package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.event.IMessageListener;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * author: lesya
 */
public class UpdatedFilesManager implements IMessageListener {
  @NonNls private final static Pattern MERGE_PATTERN = Pattern.compile("(cvs server: revision )(.*)( from repository is now in )(.*)");
  @NonNls private final static Pattern MERGING_DIFFERENCES_PATTERN = Pattern.compile("(Merging differences between )(.*)( and )(.*)( into )(.*)");

  @NonNls private final static String MERGED_FILE_MESSAGE_PREFIX = "RCS file: ";
  @NonNls private final static String MERGED_FILE_MESSAGE_POSTFIX = ",v";

  private final Map<File, CurrentMergedFileInfo> myMergedFiles = new HashMap<File, CurrentMergedFileInfo>();
  private final Set<File> myCreatedBySecondParty = new HashSet<File>();

  private ICvsFileSystem myCvsFileSystem;
  @NonNls public static final String CREATED_BY_SECOND_PARTY_PREFIX = "cvs server: conflict: ";
  @NonNls public static final String CREATED_BY_SECOND_PARTY_POSTFIX1 = " created independently by second party";
  @NonNls public static final String CREATED_BY_SECOND_PARTY_POSTFIX2 = " has been added, but already exists";


  private CurrentMergedFileInfo myCurrentMergedFile;
  private final Collection<Entry> myNewlyCreatedEntries = new HashSet<Entry>();
  private Collection<File> myNonUpdatedFiles = new HashSet<File>();

  public static class CurrentMergedFileInfo {
    private final List<String> myRevisions = new ArrayList<String>();
    private Entry myCurrentRevision;

    public CurrentMergedFileInfo() {
    }

    public void addRevisions(final String firstRevision, final String secondRevision) {
      addRevision(firstRevision);
      addRevision(secondRevision);
    }

    private void addRevision(final String firstRevision) {
      if (!myRevisions.contains(firstRevision)) {
        myRevisions.add(firstRevision);
      }
    }

    public List<String> getRevisions() {
      return myRevisions;
    }

    public void registerNewRevision(Entry previousEntry) {
      if (myCurrentRevision == null) {
        myCurrentRevision = previousEntry;
      }
    }

    public Entry getOriginalEntry() {
      return myCurrentRevision;
    }
  }

  public UpdatedFilesManager() {
  }

  public void setCvsFileSystem(ICvsFileSystem cvsFileSystem) {
    myCvsFileSystem = cvsFileSystem;
  }

  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
    if (message.startsWith(MERGED_FILE_MESSAGE_PREFIX)) {
      String pathInRepository = message.substring(MERGED_FILE_MESSAGE_PREFIX.length(),
                                                  message.length()
                                                  -
                                                  MERGED_FILE_MESSAGE_POSTFIX.length());
      String relativeRepositoryPath = myCvsFileSystem.getRelativeRepositoryPath(normalizePath(pathInRepository));
      final File file = myCvsFileSystem.getLocalFileSystem().getFile(removeModuleNameFrom(relativeRepositoryPath));
      ensureFileIsInMap(file);
      myCurrentMergedFile = myMergedFiles.get(file);
    }
    else if (message.startsWith(CREATED_BY_SECOND_PARTY_PREFIX)) {
      processMessageWithPostfix(message, CREATED_BY_SECOND_PARTY_POSTFIX1);
      processMessageWithPostfix(message, CREATED_BY_SECOND_PARTY_POSTFIX2);

    }
    else if (MERGE_PATTERN.matcher(message).matches()) {
      Matcher matcher = MERGE_PATTERN.matcher(message);
      if (matcher.matches()) {
        String relativeFileName = matcher.group(4);
        File file = myCvsFileSystem.getLocalFileSystem().getFile(relativeFileName);
        ensureFileIsInMap(file);
      }
    }
    else if (MERGING_DIFFERENCES_PATTERN.matcher(message).matches()) {
      Matcher matcher = MERGING_DIFFERENCES_PATTERN.matcher(message);
      if (matcher.matches()) {
        String firstRevision = matcher.group(2);
        String secondRevision = matcher.group(4);
        myCurrentMergedFile.addRevisions(firstRevision, secondRevision);
      }
    }
  }

  private String normalizePath(final String pathInRepository) {
    return pathInRepository.replace(File.separatorChar, '/');
  }

  private void processMessageWithPostfix(final String message, final String postfix) {
    if (message.endsWith(postfix)) {
      String pathInRepository = message.substring(CREATED_BY_SECOND_PARTY_PREFIX.length(),
                                                  message.length()
                                                  -
                                                  postfix.length());
      File ioFile = myCvsFileSystem.getLocalFileSystem().getFile(pathInRepository);
      myCreatedBySecondParty.add(ioFile);
    }
  }

  private void ensureFileIsInMap(final File file) {
    if (!myMergedFiles.containsKey(file)) {
      myMergedFiles.put(file, new CurrentMergedFileInfo());
    }
  }

  private String removeModuleNameFrom(String relativeRepositoryPath) {
    String moduleName = getModuleNameFor(relativeRepositoryPath);
    if (moduleName == null) return relativeRepositoryPath;
    return relativeRepositoryPath.substring(moduleName.length() + 1);
  }

  private String getModuleNameFor(String relativeRepositoryPath) {
    File file = new File(relativeRepositoryPath);
    if (file.getParentFile() == null) return null;
    while (file.getParentFile() != null) file = file.getParentFile();
    return file.getName();
  }

  public boolean isMerged(File file) {
    return myMergedFiles.containsKey(file);
  }


  public boolean isCreatedBySecondParty(File file) {
    return myCreatedBySecondParty.contains(file);
  }

  public CurrentMergedFileInfo getInfo(File file) {
    if (myMergedFiles.containsKey(file)) {
      return myMergedFiles.get(file);
    }
    else {
      return new CurrentMergedFileInfo();
    }
  }

  public boolean isNewlyCreatedEntryFor(VirtualFile parent, String name) {
    Entry entry = CvsEntriesManager.getInstance().getEntryFor(parent, name);
    if (entry == null) return true;
    if (entry.getConflictStringWithoutConflict().equals(Entry.DUMMY_TIMESTAMP)) return true;
    return myNewlyCreatedEntries.contains(entry);
  }

  public void addNewlyCreatedEntry(Entry entry) {
    myNewlyCreatedEntries.add(entry);
  }

  public void couldNotUpdateFile(File file) {
    myMergedFiles.remove(file);
    myNonUpdatedFiles.add(file);
  }

  public boolean fileIsNotUpdated(File file) {
    return myNonUpdatedFiles.contains(file);
  }

  public void binaryMessageSent(final byte[] bytes) {
  }
}
