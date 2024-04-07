package com.intellij.application.options;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.Icons;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

public class TagListDialog extends DialogWrapper{
  private JPanel myPanel = new JPanel(new BorderLayout());
  private final JList myList = new JList(new DefaultListModel());
  private ArrayList<String> myData;

  public TagListDialog(String title) {
    super(true);
    myPanel.add(createToolbal(), BorderLayout.NORTH);
    myPanel.add(createList(), BorderLayout.CENTER);
    setTitle(title);
    init();
  }

  public void setData(ArrayList<String> data) {
    myData = data;
    updateData();
    if (!myData.isEmpty()) {
      myList.setSelectedIndex(0);
    }
  }

  private void updateData() {
    final DefaultListModel model = ((DefaultListModel)myList.getModel());
    model.clear();
    for (Iterator<String> iterator = myData.iterator(); iterator.hasNext();) {
      model.addElement(iterator.next());
    }
  }

  public ArrayList<String> getData(){
    return myData;
  }

  private JComponent createList() {
    return new JScrollPane(myList);
  }

  private JComponent createToolbal() {
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN,
                                                           createActionGroup(),
                                                           true).getComponent();
  }

  private ActionGroup createActionGroup() {
    final DefaultActionGroup result = new DefaultActionGroup();
    final AnAction addAction = createAddAction();
    addAction.registerCustomShortcutSet(CommonShortcuts.INSERT, myList);
    result.add(addAction);

    final AnAction deleteAction = createDeleteAction();
    deleteAction.registerCustomShortcutSet(CommonShortcuts.DELETE, myList);
    result.add(deleteAction);
    return result;
  }

  private AnAction createDeleteAction() {
    return new IconWithTextAction("Remove", null, Icons.DELETE_ICON) {
      public void update(AnActionEvent e) {
        final int selectedIndex = myList.getSelectedIndex();
        if (selectedIndex >= 0) {
          e.getPresentation().setEnabled(true);
        } else {
          e.getPresentation().setEnabled(false);
        }
      }

      public void actionPerformed(AnActionEvent e) {
        int selectedIndex = myList.getSelectedIndex();
        if (selectedIndex >= 0) {
          myData.remove(selectedIndex);
          updateData();
          if (selectedIndex >= myData.size()) {
            selectedIndex = selectedIndex - 1;
          }
          if (selectedIndex >= 0) {
            myList.setSelectedIndex(selectedIndex);
          }
        }
      }
    };
  }

  private AnAction createAddAction() {
    return new IconWithTextAction("Add", null, Icons.ADD_ICON){
      public void actionPerformed(AnActionEvent e) {
        final String tagName = Messages.showInputDialog("Enter tag name:", "Tag Name", Messages.getQuestionIcon());
        if (tagName != null) {
          while (myData.contains(tagName)) {
            myData.remove(tagName);
          }
          myData.add(tagName);
          updateData();
          myList.setSelectedIndex(myData.size() - 1);
        }
      }
    };
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myList;
  }
}
