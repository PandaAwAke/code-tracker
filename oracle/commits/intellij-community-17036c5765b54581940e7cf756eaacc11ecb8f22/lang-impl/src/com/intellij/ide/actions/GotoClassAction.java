package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.navigation.NavigationItem;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;

public class GotoClassAction extends GotoActionBase implements DumbAware {
  public void gotoActionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    assert project != null;
    if (DumbService.getInstance(project).isDumb()) {
      project.getMessageBus().syncPublisher(Notifications.TOPIC).notify("dumb", "Goto Class action is not available until indices are built, using Goto File instead", "", NotificationType.INFORMATION, NotificationListener.REMOVE);


      myInAction = null;
      new GotoFileAction().gotoActionPerformed(e);
      return;
    }

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.class");
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, new GotoClassModel2(project), getPsiContext(e));

    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      public void onClose() {
        if (GotoClassAction.class.equals(myInAction)) myInAction = null;
      }

      public void elementChosen(Object element) {
        ((NavigationItem)element).navigate(true);
      }
    }, ModalityState.current(), true);
  }

  protected boolean hasContributors() {
    return ChooseByNameRegistry.getInstance().getClassModelContributors().length > 0;
  }
}