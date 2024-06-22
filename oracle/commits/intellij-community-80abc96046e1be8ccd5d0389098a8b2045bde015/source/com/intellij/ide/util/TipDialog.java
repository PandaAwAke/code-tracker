
package com.intellij.ide.util;

import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class TipDialog extends DialogWrapper{
  private TipPanel myTipPanel;

  public TipDialog(){
    super(true);
    setModal(false);
    setTitle("Tip of the Day");
    setCancelButtonText("&Close");
    myTipPanel = new TipPanel();
    myTipPanel.nextTip();
    setHorizontalStretch(1.33f);
    setVerticalStretch(1.25f);
    init();
  }

  protected Action[] createActions(){
    return new Action[]{new PreviousTipAction(),new NextTipAction(),getCancelAction()};
  }

  protected JComponent createCenterPanel(){
    return myTipPanel;
  }

  public void dispose(){
    super.dispose();
  }

  private class PreviousTipAction extends AbstractAction{
    public PreviousTipAction(){
      super("&Previous Tip");
    }

    public void actionPerformed(ActionEvent e){
      myTipPanel.prevTip();
    }
  }

  private class NextTipAction extends AbstractAction{
    public NextTipAction(){
      super("&Next Tip");
      putValue(DialogWrapper.DEFAULT_ACTION,Boolean.TRUE);
    }

    public void actionPerformed(ActionEvent e){
      myTipPanel.nextTip();
    }
  }
}