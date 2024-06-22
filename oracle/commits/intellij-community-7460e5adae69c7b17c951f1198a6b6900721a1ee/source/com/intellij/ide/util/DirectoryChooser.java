package com.intellij.ide.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ui.util.CellAppearanceUtils;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRootPackageType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public class DirectoryChooser extends DialogWrapper {
  private final DirectoryChooserView myView;

  public DirectoryChooser(Project project){
    this(project, new DirectoryChooserModuleTreeView(project));
  }

  public DirectoryChooser(Project project, DirectoryChooserView view){
    super(project, true);
    myView = view;
    init();
  }


  protected JComponent createCenterPanel(){
    final Runnable runnable = new Runnable() {
      public void run() {
        enableButtons();
      }
    };
    myView.onSelectionChange(runnable);
    final JComponent component = myView.getComponent();
    final JScrollPane jScrollPane = new JScrollPane(component);
    int prototypeWidth = component.getFontMetrics(component.getFont()).stringWidth("X:\\1234567890\\1234567890\\com\\company\\system\\subsystem");
    jScrollPane.setPreferredSize(new Dimension(Math.max(300, prototypeWidth),300));

    installEnterAction(component);

    return jScrollPane;
  }

  private void installEnterAction(final JComponent component) {
    final KeyStroke enterKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0);
    final InputMap inputMap = component.getInputMap();
    final ActionMap actionMap = component.getActionMap();
    final Object oldActionKey = inputMap.get(enterKeyStroke);
    final Action oldAction = oldActionKey != null ? actionMap.get(oldActionKey) : null;
    inputMap.put(enterKeyStroke, "clickButton");
    actionMap.put("clickButton", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (isOKActionEnabled()) {
          doOKAction();
        }
        else if (oldAction != null) {
          oldAction.actionPerformed(e);
        }
      }
    });
  }

  protected String getDimensionServiceKey() {
    return "chooseDestDirectoryDialog";
  }

  private String[] splitPath(String path) {
    ArrayList<String> list = new ArrayList<String>();
    int index = 0;
    int nextSeparator;
    while ((nextSeparator = path.indexOf(File.separatorChar, index)) != -1) {
      list.add(path.substring(index, nextSeparator));
      index = nextSeparator + 1;
    }
    list.add(path.substring(index, path.length()));
    return list.toArray(new String[list.size()]);
  }

  private void buildFragments() {
    ArrayList<String[]> pathes = new ArrayList<String[]>();
    for (int i = 0; i < myView.getItemsSize(); i++) {
      ItemWrapper item = myView.getItemByIndex(i);
      pathes.add(splitPath(item.getPresentableUrl()));
    }
    FragmentBuilder headBuilder = new FragmentBuilder(pathes){
        protected void append(String fragment, StringBuffer buffer) {
          buffer.append(mySeparator);
          buffer.append(fragment);
        }

        protected int getFragmentIndex(String[] path, int index) {
          return path.length > index ? index : -1;
        }
      };
    String commonHead = headBuilder.execute();
    final int headLimit = headBuilder.getIndex();
    FragmentBuilder tailBuilder = new FragmentBuilder(pathes){
        protected void append(String fragment, StringBuffer buffer) {
          buffer.insert(0, fragment + mySeparator);
        }

        protected int getFragmentIndex(String[] path, int index) {
          int result = path.length - 1 - index;
          return result > headLimit ? result : -1;
        }
      };
    String commonTail = tailBuilder.execute();
    int tailLimit = tailBuilder.getIndex();
    for (int i = 0; i < myView.getItemsSize(); i++) {
      ItemWrapper item = myView.getItemByIndex(i);
      String special = concat(pathes.get(i), headLimit, tailLimit);
      item.setFragments(createFragments(commonHead, special, commonTail));
    }
  }

  private String concat(String[] strings, int headLimit, int tailLimit) {
    if (strings.length <= headLimit + tailLimit) return null;
    StringBuffer buffer = new StringBuffer();
    String separator = "";
    for (int i = headLimit; i < strings.length - tailLimit; i++) {
      buffer.append(separator);
      buffer.append(strings[i]);
      separator = File.separator;
    }
    return buffer.toString();
  }

  private PathFragment[] createFragments(String head, String special, String tail) {
    ArrayList<PathFragment> list = new ArrayList<PathFragment>(3);
    if (head != null) {
      if (special != null || tail != null) list.add(new PathFragment(head + File.separatorChar, true));
      else return new PathFragment[]{new PathFragment(head, true)};
    }
    if (special != null) {
      if (tail != null) list.add(new PathFragment(special + File.separatorChar, false));
      else list.add(new PathFragment(special, false));
    }
    if (tail != null) list.add(new PathFragment(tail, true));
    return list.toArray(new PathFragment[list.size()]);
  }

  private static abstract class FragmentBuilder {
    private final ArrayList<String[]> myPathes;
    private final StringBuffer myBuffer = new StringBuffer();
    private int myIndex;
    protected String mySeparator = "";

    public FragmentBuilder(ArrayList<String[]> pathes) {
      myPathes = pathes;
      myIndex = 0;
    }

    public int getIndex() { return myIndex; }

    public String execute() {
      while (true) {
        String commonHead = getCommonFragment(myIndex);
        if (commonHead == null) break;
        append(commonHead, myBuffer);
        mySeparator = File.separator;
        myIndex++;
      }
      return myIndex > 0 ? myBuffer.toString() : null;
    }

    protected abstract void append(String fragment, StringBuffer buffer);

    private String getCommonFragment(int count) {
      String commonFragment = null;
      for (Iterator<String[]> iterator = myPathes.iterator(); iterator.hasNext();) {
        String[] path = iterator.next();
        int index = getFragmentIndex(path, count);
        if (index == -1) return null;
        if (commonFragment == null) {
          commonFragment = path[index];
          continue;
        }
        if (!Comparing.strEqual(commonFragment, path[index], SystemInfo.isFileSystemCaseSensitive)) return null;
      }
      return commonFragment;
    }

    protected abstract int getFragmentIndex(String[] path, int index);
  }

  public class ItemWrapper {
    final PsiDirectory myDirectory;
    private PathFragment[] myFragments;
    private final String myPostfix;

    ItemWrapper(PsiDirectory directory, String postfix) {
      myDirectory = directory;
      myPostfix = postfix != null && postfix.length() > 0 ? postfix : null;
    }

    public PathFragment[] getFragments() { return myFragments; }

    public void setFragments(PathFragment[] fragments) {
      myFragments = fragments;
    }

    public Icon getIcon(FileIndex fileIndex) {
      if (myDirectory != null) {
        VirtualFile virtualFile = myDirectory.getVirtualFile();
        if (fileIndex.isInTestSourceContent(virtualFile)){
          return CellAppearanceUtils.TEST_SOURCE_FOLDER;
        }
        else if (fileIndex.isInSourceContent(virtualFile)){
          return CellAppearanceUtils.SOURCE_FOLDERS_ICON;
        }
      }
      return CellAppearanceUtils.FOLDER_ICON;
    }

    public String getPresentableUrl() {
      String directoryUrl = myDirectory != null ? myDirectory.getVirtualFile().getPresentableUrl() : "";
      return myPostfix != null ? directoryUrl + myPostfix : directoryUrl;
    }

    public PsiDirectory getDirectory() {
      return myDirectory;
    }
  }

  public JComponent getPreferredFocusedComponent(){
    return myView.getComponent();
  }

  public void fillList(PsiDirectory[] directories, PsiDirectory defaultSelection, Project project, String postfixToShow) {
    fillList(directories, defaultSelection, project, postfixToShow, null);
  }

  public void fillList(PsiDirectory[] directories, PsiDirectory defaultSelection, Project project, Map<PsiDirectory,String> postfixes) {
    fillList(directories, defaultSelection, project, null, postfixes);
  }

  void fillList(PsiDirectory[] directories, PsiDirectory defaultSelection, Project project, String postfixToShow, Map<PsiDirectory,String> postfixes) {
    if (myView.getItemsSize() > 0){
      myView.clearItems();
    }
    if (defaultSelection == null && directories.length > 0) {
      defaultSelection = directories[0];
    }
    int selectionIndex = -1;
    for(int i = 0; i < directories.length; i++){
      PsiDirectory directory = directories[i];
      if (directory.equals(defaultSelection)) {
        selectionIndex = i;
        break;
      }
    }
    if (selectionIndex < 0 && directories.length == 1) {
      selectionIndex = 0;
    }

    if (selectionIndex < 0) {
      // find source root corresponding to defaultSelection
      PsiDirectory[] sourceDirectories = PsiManager.getInstance(project).getRootDirectories(PsiRootPackageType.SOURCE_PATH);
      for(int i = 0; i < sourceDirectories.length; i++){
        PsiDirectory directory = sourceDirectories[i];
        if (isParent(defaultSelection, directory)) {
          defaultSelection = directory;
          break;
        }
      }
    }

    for(int i = 0; i < directories.length; i++){
      PsiDirectory directory = directories[i];
      final String postfixForDirectory;
      if (postfixes == null) {
        postfixForDirectory = postfixToShow;
      }
      else {
        postfixForDirectory = postfixes.get(directory);
      }
      final ItemWrapper itemWrapper = new ItemWrapper(directory, postfixForDirectory);
      myView.addItem(itemWrapper);
      if (selectionIndex < 0 && isParent(directory, defaultSelection)) {
        selectionIndex = i;
      }
    }
    buildFragments();
    myView.listFilled();
    if (directories.length > 0) {
      myView.selectItemByIndex(selectionIndex);
    }
    else {
      myView.clearSelection();
    }
    enableButtons();
    myView.getComponent().repaint();
  }

  private boolean isParent(PsiDirectory directory, PsiDirectory parentCandidate) {
    while (directory != null) {
      if (directory.equals(parentCandidate)) return true;
      directory = directory.getParentDirectory();
    }
    return false;
  }

  private void enableButtons() {
    setOKActionEnabled(myView.getSelectedItem() != null);
  }

  public PsiDirectory getSelectedDirectory() {
    ItemWrapper wrapper = myView.getSelectedItem();
    if (wrapper == null) return null;
    return wrapper.myDirectory;
  }


  public static class PathFragment {
    private final String myText;
    private final boolean myCommon;

    public PathFragment(String text, boolean isCommon) {
      myText = text;
      myCommon = isCommon;
    }

    public String getText() {
      return myText;
    }

    public boolean isCommon() {
      return myCommon;
    }
  }


}
