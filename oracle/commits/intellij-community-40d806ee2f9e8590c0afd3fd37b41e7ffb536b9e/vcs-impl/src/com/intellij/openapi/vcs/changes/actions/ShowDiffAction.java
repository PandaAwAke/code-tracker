package com.intellij.openapi.vcs.changes.actions;

import com.intellij.CommonBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NullableFactory;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author max
 */
public class ShowDiffAction extends AnAction {
  private static final String ourText = ActionsBundle.actionText("ChangesView.Diff");

  public ShowDiffAction() {
    super(ourText,
          ActionsBundle.actionDescription("ChangesView.Diff"),
          IconLoader.getIcon("/actions/diff.png"));
  }

  public void update(AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && canShowDiff(changes));
  }

  private static boolean canShowDiff(Change[] changes) {
    if (changes == null || changes.length == 0) return false;
    return !ChangesUtil.getFilePath(changes [0]).isDirectory();
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if (project == null || changes == null) return;

    final boolean needsConvertion = checkIfThereAreFakeRevisions(project, changes);
    final List<Change> changesInList = e.getData(VcsDataKeys.CHANGES_IN_LIST_KEY);

    // this trick is essential since we are under some conditions to refresh changes;
    // but we can only rely on callback after refresh
    final Runnable performer = new Runnable() {
      public void run() {
        Change[] convertedChanges;
        if (needsConvertion) {
          convertedChanges = loadFakeRevisions(project, changes);
        } else {
          convertedChanges = changes;
        }

        if (convertedChanges == null || convertedChanges.length == 0) {
          return;
        }

        List<Change> changesInListCopy = changesInList;

        int index = 0;
        if (convertedChanges.length == 1) {
          final Change selectedChange = convertedChanges[0];
          if (checkNotifyBinaryDiff(selectedChange)) {
            return;
          }
          ChangeList changeList = ((ChangeListManagerImpl) ChangeListManager.getInstance(project)).getIdentityChangeList(selectedChange);
          if (changeList != null) {
            if (changesInListCopy == null) {
              changesInListCopy = new ArrayList<Change>(changeList.getChanges());
              Collections.sort(changesInListCopy, new Comparator<Change>() {
                public int compare(final Change o1, final Change o2) {
                  return ChangesUtil.getFilePath(o1).getName().compareToIgnoreCase(ChangesUtil.getFilePath(o2).getName());
                }
              });
            }
            convertedChanges = changesInListCopy.toArray(new Change[changesInListCopy.size()]);
            for(int i=0; i<convertedChanges.length; i++) {
              if (convertedChanges [i] == selectedChange) {
                index = i;
                break;
              }
            }
          }
        }

        showDiffForChange(convertedChanges, index, project);
      }
    };

    if (needsConvertion) {
      ChangeListManager.getInstance(project).invokeAfterUpdate(performer, InvokeAfterUpdateMode.BACKGROUND_CANCELLABLE, ourText, ModalityState.current());
    }  else {
      performer.run();
    }
  }

  public static void showDiffForChange(final Change[] changes, final int index, final Project project) {
    showDiffForChange(changes, index, project, DiffExtendUIFactory.NONE, true);
  }

  private boolean checkIfThereAreFakeRevisions(final Project project, final Change[] changes) {
    boolean needsConvertion = false;
    for(Change change: changes) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      final ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision instanceof FakeRevision) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(beforeRevision.getFile());
        needsConvertion = true;
      }
      if (afterRevision instanceof FakeRevision) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(afterRevision.getFile());
        needsConvertion = true;
      }
    }
    return needsConvertion;
  }

  @Nullable
  private static Change[] loadFakeRevisions(final Project project, final Change[] changes) {
    List<Change> matchingChanges = new ArrayList<Change>();
    for(Change change: changes) {
      matchingChanges.addAll(ChangeListManager.getInstance(project).getChangesIn(ChangesUtil.getFilePath(change)));
    }
    return matchingChanges.toArray(new Change[matchingChanges.size()]);
  }

  public interface DiffExtendUIFactory {
    DiffExtendUIFactory NONE = new DiffExtendUIFactory() {
      public List<? extends AnAction> createActions(Change change) {
        return Collections.emptyList();
      }

      @Nullable
      public JComponent createBottomComponent() {
        return null;
      }
    };
    List<? extends AnAction> createActions(Change change);

    @Nullable
    JComponent createBottomComponent();
  }

  public static void showDiffForChange(Change[] changes, int index, final Project project, @Nullable DiffExtendUIFactory actionsFactory,
                                       final boolean showFrame) {
    Change selectedChange = changes [index];
    changes = filterDirectoryAndBinaryChanges(changes);
    if (changes.length == 0) {
      return;
    }
    index = 0;
    for(int i=0; i<changes.length; i++) {
      if (changes [i] == selectedChange) {
        index = i;
        break;
      }
    }
    final DiffTool tool = DiffManager.getInstance().getDiffTool();

    final SimpleDiffRequest diffReq = createDiffRequest(changes, index, project, actionsFactory);
    if (diffReq != null) {
      if (showFrame) {
        diffReq.addHint(DiffTool.HINT_SHOW_FRAME);
      }
      else {
        diffReq.addHint(DiffTool.HINT_SHOW_MODAL_DIALOG);
      }
      if (changes.length > 1) {
        diffReq.addHint(DiffTool.HINT_ALLOW_NO_DIFFERENCES);
      }
      tool.show(diffReq);
    }
  }

  private static Change[] filterDirectoryAndBinaryChanges(final Change[] changes) {
    ArrayList<Change> changesList = new ArrayList<Change>();
    Collections.addAll(changesList, changes);
    for(int i=changesList.size()-1; i >= 0; i--) {
      final FilePath path = ChangesUtil.getFilePath(changesList.get(i));
      if (path.isDirectory() || path.getFileType().isBinary()) {
        changesList.remove(i);
      }
    }
    return changesList.toArray(new Change[changesList.size()]);
  }

  static void showDiffForChange(AnActionEvent e,
                                        final Change[] changes,
                                        final int index,
                                        final Project project,
                                        DiffExtendUIFactory actionsFactory) {
    DiffViewer diffViewer = e.getData(PlatformDataKeys.DIFF_VIEWER);
    if (diffViewer != null) {
      final SimpleDiffRequest diffReq = createDiffRequest(changes, index, project, actionsFactory);
      if (diffReq != null) {
        diffViewer.setDiffRequest(diffReq);
      }
    }
  }

  @Nullable
  private static SimpleDiffRequest createDiffRequest(final Change[] changes,
                                                     final int index,
                                                     final Project project,
                                                     final DiffExtendUIFactory actionsFactory) {
    final Change change = changes[index];

    final ContentRevision bRev = change.getBeforeRevision();
    final ContentRevision aRev = change.getAfterRevision();

    if ((bRev != null && (bRev.getFile().getFileType().isBinary() || bRev.getFile().isDirectory())) ||
        (aRev != null && (aRev.getFile().getFileType().isBinary() || aRev.getFile().isDirectory()))) {
      if (bRev != null && bRev.getFile().getFileType() == FileTypes.UNKNOWN && !bRev.getFile().isDirectory()) {
        if (!checkAssociate(project, bRev.getFile())) return null;
      }
      else if (aRev != null && aRev.getFile().getFileType() == FileTypes.UNKNOWN && !aRev.getFile().isDirectory()) {
        if (!checkAssociate(project, aRev.getFile())) return null;
      }
      else {
        return null;
      }
    }

    String beforePath = bRev != null ? bRev.getFile().getPath() : null;
    String afterPath = aRev != null ? aRev.getFile().getPath() : null;
    String title;
    if (beforePath != null && afterPath != null && !beforePath.equals(afterPath)) {
      title = beforePath + " -> " + afterPath;
    }
    else if (beforePath != null) {
      title = beforePath;
    }
    else if (afterPath != null) {
      title = afterPath;
    }
    else {
      title = VcsBundle.message("diff.unknown.path.title");
    }
    final ChangeDiffRequest diffReq = new ChangeDiffRequest(project, title, changes, index, actionsFactory);

    if (changes.length > 1 || actionsFactory != null) {
      diffReq.setToolbarAddons(new DiffRequest.ToolbarAddons() {
        public void customize(DiffToolbar toolbar) {
          if (changes.length > 1) {
            toolbar.addSeparator();
            toolbar.addAction(ActionManager.getInstance().getAction("Diff.PrevChange"));
            toolbar.addAction(ActionManager.getInstance().getAction("Diff.NextChange"));
          }
          if (actionsFactory != null) {
            toolbar.addSeparator();
            for (AnAction action : actionsFactory.createActions(change)) {
              toolbar.addAction(action);
            }
          }
        }
      });
    }

    if (actionsFactory != null) {
      diffReq.setBottomComponentFactory(new NullableFactory<JComponent>() {
        public JComponent create() {
          return actionsFactory.createBottomComponent();
        }
      });
    }

    boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        diffReq.setContents(createContent(project, (ContentRevision) bRev), createContent(project, (ContentRevision) aRev));
      }
    }, VcsBundle.message("progress.loading.diff.revisions"), true, project);
    if (!result) return null;

    String beforeRevisionTitle = (bRev != null) ? bRev.getRevisionNumber().asString() : "";
    String afterRevisionTitle = (aRev != null) ? aRev.getRevisionNumber().asString() : "";
    if (beforeRevisionTitle.length() == 0) {
      beforeRevisionTitle = "Base version";
    }
    if (afterRevisionTitle.length() == 0) {
      afterRevisionTitle = "Your version";
    }
    diffReq.setContentTitles(beforeRevisionTitle, afterRevisionTitle);
    return diffReq;
  }

  private static boolean checkAssociate(final Project project, final FilePath file) {
    int rc = Messages.showDialog(project,
                                 VcsBundle.message("diff.unknown.file.type.prompt", file.getName()),
                                 VcsBundle.message("diff.unknown.file.type.title"),
                                 new String[] {
                                   VcsBundle.message("diff.unknown.file.type.associate"),
                                   CommonBundle.getCancelButtonText()
                                 }, 0, Messages.getQuestionIcon());
    if (rc == 0) {
      FileType fileType = FileTypeChooser.associateFileType(file.getName());
      return fileType != null && !fileType.isBinary();
    }
    return false;
  }

  @NotNull
  private static DiffContent createContent(Project project, ContentRevision revision) {
    ProgressManager.getInstance().checkCanceled();
    if (revision == null) return new SimpleContent("");
    if (revision instanceof CurrentContentRevision) {
      final CurrentContentRevision current = (CurrentContentRevision)revision;
      final VirtualFile vFile = current.getVirtualFile();
      return vFile != null ? new FileContent(project, vFile) : new SimpleContent("");
    }

    String revisionContent;
    try {
      revisionContent = revision.getContent();
    }
    catch(VcsException ex) {
      // TODO: correct exception handling
      revisionContent = null;           
    }
    SimpleContent content = revisionContent == null
                            ? new SimpleContent("")
                            : new SimpleContent(revisionContent, revision.getFile().getFileType());
    VirtualFile vFile = revision.getFile().getVirtualFile();
    if (vFile != null) {
      content.setCharset(vFile.getCharset());
      content.setBOM(vFile.getBOM());
    }
    content.setReadOnly(true);
    return content;
  }

  private static boolean checkNotifyBinaryDiff(final Change selectedChange) {
    final ContentRevision beforeRevision = selectedChange.getBeforeRevision();
    final ContentRevision afterRevision = selectedChange.getAfterRevision();
    if (beforeRevision instanceof BinaryContentRevision &&
        afterRevision instanceof BinaryContentRevision) {
      try {
        byte[] beforeContent = ((BinaryContentRevision)beforeRevision).getBinaryContent();
        byte[] afterContent = ((BinaryContentRevision)afterRevision).getBinaryContent();
        if (Arrays.equals(beforeContent, afterContent)) {
          Messages.showInfoMessage(VcsBundle.message("message.text.binary.versions.are.identical"), VcsBundle.message("message.title.diff"));
        } else {
          Messages.showInfoMessage(VcsBundle.message("message.text.binary.versions.are.different"), VcsBundle.message("message.title.diff"));
        }
      }
      catch (VcsException e) {
        Messages.showInfoMessage(e.getMessage(), VcsBundle.message("message.title.diff"));
      }
      return true;
    }
    return false;
  }
}
