package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.ui.plaf.beg.IdeaMenuUI;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public final class ActionMenu extends JMenu{
  private String myPlace;
  private DataContext myContext;
  private ActionGroup myGroup;
  private PresentationFactory myPresentationFactory;
  private Presentation myPresentation;
  /**
   * Defines whether menu shows its mnemonic or not.
   */
  private boolean myMnemonicEnabled;
  private MenuItemSynchronizer myMenuItemSynchronizer;
  /**
   * This is PATCH!!!
   * Do not remove this STUPID code. Otherwise you will lose all keyboard navigation
   * at JMenuBar.
   */
  private StubItem myStubItem;

  public ActionMenu(DataContext context,String place,ActionGroup group, PresentationFactory presentationFactory){
    myContext=context;
    myPlace=place;
    myGroup=group;
    myPresentationFactory=presentationFactory;
    myPresentation=myPresentationFactory.getPresentation(group);
    init();
  }

  public void addNotify(){
    super.addNotify();
    if(myMenuItemSynchronizer==null){
      myMenuItemSynchronizer=new MenuItemSynchronizer();
      myGroup.addPropertyChangeListener(myMenuItemSynchronizer);
      myPresentation.addPropertyChangeListener(myMenuItemSynchronizer);
    }
  }

  public void removeNotify(){
    if(myMenuItemSynchronizer!=null){
      myGroup.removePropertyChangeListener(myMenuItemSynchronizer);
      myPresentation.removePropertyChangeListener(myMenuItemSynchronizer);
      myMenuItemSynchronizer=null;
    }
    super.removeNotify();
  }

  public void updateUI(){
    JPopupMenu popupMenu=getPopupMenu();
    if(popupMenu!=null){
      popupMenu.updateUI();
    }
    setUI(IdeaMenuUI.createUI(this));
    setFont(UIManager.getFont("Menu.font"));
  }

  private void init(){
    myMnemonicEnabled=true;
    boolean macSystemMenu = SystemInfo.isMacSystemMenu && myPlace == ActionPlaces.MAIN_MENU;

    myStubItem= macSystemMenu ? null : new StubItem();
    addStubItem();
    addMenuListener(new MenuListenerImpl());
    setBorderPainted(false);

    setVisible(myPresentation.isVisible());
    setEnabled(myPresentation.isEnabled());
    setMnemonic(myPresentation.getMnemonic());
    setText(myPresentation.getText());
    updateIcon();
    setMnemonic(myPresentation.getMnemonic());
   }

  private void addStubItem() {
    if (myStubItem != null) {
      add(myStubItem);
    }
  }

  public void setMnemonicEnabled(boolean enable){
    myMnemonicEnabled=enable;
    setMnemonic(myPresentation.getMnemonic());
  }

  public void setMnemonic(int mnemonic){
    if(myMnemonicEnabled){
      super.setMnemonic(mnemonic);
    }else{
      super.setMnemonic(0);
    }
  }

  private void updateIcon(){
    Presentation presentation=myPresentation;
    Icon icon=presentation.getIcon();
    setIcon(icon);
    if(presentation.getDisabledIcon()!=null){
      setDisabledIcon(presentation.getDisabledIcon());
    }else{
      setDisabledIcon(IconLoader.getDisabledIcon(icon));
    }
  }

  public void menuSelectionChanged(boolean isIncluded){
    super.menuSelectionChanged(isIncluded);
    IdeFrame frame = (IdeFrame)SwingUtilities.getAncestorOfClass(IdeFrame.class, this);
    if (frame != null) {
      StatusBar statusBar = frame.getStatusBar();
      if(isIncluded){
        statusBar.setInfo(myPresentation.getDescription());
      }else{
        statusBar.setInfo(null);
      }
    }
  }

  private class MenuListenerImpl implements MenuListener {
    public void menuCanceled(MenuEvent e){
      clearItems();
      addStubItem();
    }

    public void menuDeselected(MenuEvent e){
      clearItems();
      addStubItem();
    }


    public void menuSelected(MenuEvent e){
      fillMenu();
    }
  }

  private void clearItems() {
    if (SystemInfo.isMacSystemMenu && myPlace == ActionPlaces.MAIN_MENU) {
      Component[] menuComponents = getMenuComponents();
      for (int i = 0; i < menuComponents.length; i++) {
        Component menuComponent = menuComponents[i];
        if (menuComponent instanceof ActionMenu) {
          ((ActionMenu)menuComponent).clearItems();
        }
        else if (menuComponent instanceof ActionMenuItem) {
          ((ActionMenuItem)menuComponent).setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F24, 0));
        }
      }
    }

    removeAll();
    validate();
  }

  private void fillMenu() {
    DataContext context = myContext != null ? myContext : DataManager.getInstance().getDataContext();
    Utils.fillMenu(myGroup,ActionMenu.this, myPresentationFactory, context,myPlace);
  }

  private class MenuItemSynchronizer implements PropertyChangeListener{
    public void propertyChange(PropertyChangeEvent e){
      String name=e.getPropertyName();
      if(Presentation.PROP_VISIBLE.equals(name)){
        ActionMenu.this.setVisible(myPresentation.isVisible());
      }else if(Presentation.PROP_ENABLED.equals(name)){
        ActionMenu.this.setEnabled(myPresentation.isEnabled());
      }else if(Presentation.PROP_MNEMONIC_KEY.equals(name)){
        ActionMenu.this.setMnemonic(myPresentation.getMnemonic());
      }else if(Presentation.PROP_TEXT.equals(name)){
        ActionMenu.this.setText(myPresentation.getText());
      }else if(Presentation.PROP_ICON.equals(name)||Presentation.PROP_DISABLED_ICON.equals(name)){
        updateIcon();
      }
    }
  }
}
