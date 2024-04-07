package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public final class ToggleFullScreenModeAction extends ToggleAction{
  private static final String PROP_BOUNDS_BEFORE_FULL_SCREEN="boundsBeforeFullScreen";

  private static IdeFrame getFrame(AnActionEvent e){
    WindowManagerEx windowManagerEx=WindowManagerEx.getInstanceEx();
    Project project=(Project)e.getDataContext().getData(DataConstants.PROJECT);
    return windowManagerEx.getFrame(project);
  }

  public boolean isSelected(AnActionEvent e){
    IdeFrame frame=getFrame(e);
    return frame != null && frame.getGraphicsConfiguration().getDevice().getFullScreenWindow()==frame;
  }

  public void setSelected(AnActionEvent e,boolean state){
    // Hide all all visible floating tool windows.
    Project project=(Project)e.getDataContext().getData(DataConstants.PROJECT);
    if(project!=null){
      ToolWindowManager toolWindowManager=ToolWindowManager.getInstance(project);
      String[] ids=toolWindowManager.getToolWindowIds();
      for(int i=0;i<ids.length;i++){
        String id=ids[i];
        ToolWindow toolWindow=toolWindowManager.getToolWindow(id);
        if(ToolWindowType.FLOATING==toolWindow.getType() && toolWindow.isVisible()){
          toolWindow.hide(null);
        }
      }
    }
    // Toggle full screen mode.
    IdeFrame frame=getFrame(e);
    final Component focusedComponent=WindowManagerEx.getInstanceEx().getFocusedComponent(frame);
    GraphicsConfiguration graphicsConfiguration=frame.getGraphicsConfiguration();
    Rectangle bounds=graphicsConfiguration.getBounds();
    Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);
    if(state){ // toggle full screen on
      frame.getRootPane().putClientProperty(PROP_BOUNDS_BEFORE_FULL_SCREEN,frame.getBounds());
      frame.dispose();
      frame.setUndecorated(true);
      frame.setBounds(bounds);
      frame.show();
      graphicsConfiguration.getDevice().setFullScreenWindow(frame);
    }else{ // toggle full screen off
      Rectangle boundsBeforeFullScreen=(Rectangle)frame.getRootPane().getClientProperty(PROP_BOUNDS_BEFORE_FULL_SCREEN);
      frame.dispose();
      graphicsConfiguration.getDevice().setFullScreenWindow(null);
      frame.setUndecorated(false);
      if(boundsBeforeFullScreen!=null){
        frame.setBounds(boundsBeforeFullScreen);
        frame.show();
      }else{
        frame.setBounds(
          bounds.x+insets.left,
          bounds.y+insets.top,
          bounds.width-insets.left-insets.right,
          bounds.height-insets.top-insets.bottom
        );
        frame.show();
        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
      }
    }
    if(focusedComponent!=null){
      SwingUtilities.invokeLater(
        new Runnable(){
          public void run(){
            focusedComponent.requestFocus();
          }
        }
      );
    }
  }

  public void update(AnActionEvent e){
    super.update(e);
    IdeFrame frame=getFrame(e);
    final boolean operational = !SystemInfo.isMac && // Disabled full screen mode for Mac since it doesn't work anyway
                                frame != null &&
                                frame.getGraphicsConfiguration().getDevice().isFullScreenSupported();
    e.getPresentation().setVisible(operational);
    e.getPresentation().setEnabled(operational);
  }
}
