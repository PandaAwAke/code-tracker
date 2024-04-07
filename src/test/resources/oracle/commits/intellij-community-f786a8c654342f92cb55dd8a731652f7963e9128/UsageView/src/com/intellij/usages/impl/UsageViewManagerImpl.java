/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import com.intellij.usageView.UsageViewBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 4:49:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageViewManagerImpl extends UsageViewManager implements ProjectComponent {
  private Project myProject;
  private static final Key<UsageView> USAGE_VIEW_KEY = Key.create("USAGE_VIEW");

  public UsageViewManagerImpl(Project project) {
    myProject = project;
  }

  @NotNull
  public UsageView createUsageView(UsageTarget[] targets, Usage[] usages, UsageViewPresentation presentation) {
    UsageViewImpl usageView = new UsageViewImpl(presentation, targets, null, myProject);
    appendUsages(usages, usageView);
    usageView.setSearchInProgress(false);
    return usageView;
  }

  @NotNull
  public UsageView showUsages(UsageTarget[] searchedFor, Usage[] foundUsages, UsageViewPresentation presentation) {
    UsageView usageView = createUsageView(searchedFor, foundUsages, presentation);
    addContent((UsageViewImpl)usageView, presentation);
    showToolWindow(true);
    return usageView;
  }

  private Content addContent(UsageViewImpl usageView, UsageViewPresentation presentation) {
    Content content = com.intellij.usageView.UsageViewManager.getInstance(myProject).addContent(
      presentation.getTabText(),
      true,
      usageView.getComponent(),
      presentation.isOpenInNewTab(),
      true
    );
    usageView.setContent(content);
    content.putUserData(USAGE_VIEW_KEY, usageView);
    return content;
  }

  public UsageView searchAndShowUsages(final UsageTarget[] searchFor,
                                       final Factory<UsageSearcher> searcherFactory,
                                       final boolean showPanelIfOnlyOneUsage,
                                       final boolean showNotFoundMessage, final UsageViewPresentation presentation,
                                       UsageViewStateListener listener) {

    final UsageViewImpl[] usageView = new UsageViewImpl[]{null};

    FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation();
    processPresentation.setShowNotFoundMessage(showNotFoundMessage);
    processPresentation.setShowPanelIfOnlyOneUsage(showPanelIfOnlyOneUsage);

    final Application application = ApplicationManager.getApplication();
    application.runProcessWithProgressSynchronously(
      new SearchForUsagesRunnable(usageView, presentation, searchFor, searcherFactory, processPresentation, listener),
      getProgressTitle(presentation),
      true,
      myProject
    );

    return usageView[0];
  }

  public void searchAndShowUsages(UsageTarget[] searchFor,
                                  Factory<UsageSearcher> searcherFactory,
                                  FindUsagesProcessPresentation processPresentation,
                                  UsageViewPresentation presentation,
                                  UsageViewManager.UsageViewStateListener listener
                                       ) {
    final UsageViewImpl[] usageView = new UsageViewImpl[]{null};
    final SearchForUsagesRunnable runnable = new SearchForUsagesRunnable(usageView, presentation, searchFor, searcherFactory, processPresentation, listener);
    final Factory<ProgressIndicator> progressIndicatorFactory = processPresentation.getProgressIndicatorFactory();

    UsageViewImplUtil.runProcessWithProgress(
      (progressIndicatorFactory != null)?progressIndicatorFactory.create():null,
      new Runnable() {
        public void run() {
          runnable.searchUsages();
        }
      },
      new Runnable() {
        public void run() {
          runnable.endSearchForUsages();
        }
      }
    );
  }

  public UsageView getSelectedUsageView() {
    final Content content = com.intellij.usageView.UsageViewManager.getInstance(myProject).getSelectedContent();
    if (content != null) {
      return content.getUserData(USAGE_VIEW_KEY);
    }

    return null;
  }

  static String getProgressTitle(UsageViewPresentation presentation) {
    final String scopeText = presentation.getScopeText();
    if (scopeText == null)
      return UsageViewBundle.message("progress.searching.for", StringUtil.capitalize(presentation.getUsagesString()));
    return UsageViewBundle.message("progress.searching.for.in", StringUtil.capitalize(presentation.getUsagesString()), scopeText);
  }

  private void showToolWindow(boolean activateWindow) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.FIND);
    toolWindow.show(null);
    if (activateWindow && !toolWindow.isActive()) {
      toolWindow.activate(null);
    }
  }

  private void appendUsages(final Usage[] foundUsages, final UsageViewImpl usageView) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (int i = 0; i < foundUsages.length; i++) {
          usageView.appendUsage(foundUsages[i]);
        }
      }
    });
  }


  public String getComponentName() {
    return "NewUsageViewManager";
  }

  public void initComponent() { }

  public void disposeComponent() { }

  public void projectOpened() { }

  public void projectClosed() { }

  private class SearchForUsagesRunnable implements Runnable {
    private int myUsageCount = 0;
    private Usage myFirstUsage = null;
    private final UsageViewImpl[] myUsageView;
    private final UsageViewPresentation myPresentation;
    private final UsageTarget[] mySearchFor;
    private final Factory<UsageSearcher> mySearcherFactory;
    private final FindUsagesProcessPresentation myProcessPresentation;
    private UsageViewStateListener myListener;

    public SearchForUsagesRunnable(final UsageViewImpl[] usageView,
                                   final UsageViewPresentation presentation,
                                   final UsageTarget[] searchFor,
                                   final Factory<UsageSearcher> searcherFactory,
                                   FindUsagesProcessPresentation processPresentation,
                                   final UsageViewManager.UsageViewStateListener listener) {
      myUsageView = usageView;
      myPresentation = presentation;
      mySearchFor = searchFor;
      mySearcherFactory = searcherFactory;
      myProcessPresentation = processPresentation;
      myListener = listener;
    }

    private void openView() {
      if (myUsageView[0] != null) {
        return;
      }

      final UsageViewImpl usageView = new UsageViewImpl(myPresentation, mySearchFor, mySearcherFactory, myProject);
      myUsageView[0] = usageView;
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          addContent(usageView, myPresentation);
          if (myListener!=null) {
            myListener.usageViewCreated(usageView);
          }
          showToolWindow(false);
          
          myUsageView[0].setProgressIndicatorFactory(myProcessPresentation.getProgressIndicatorFactory());
        }
      });
    }

    public void run() {
      searchUsages();

      endSearchForUsages();
    }

    private void searchUsages() {
      UsageSearcher usageSearcher = mySearcherFactory.create();
      usageSearcher.generate(new Processor<Usage>() {
        public boolean process(final Usage usage) {
          myUsageCount++;
          if (myUsageCount == 1 && !myProcessPresentation.isShowPanelIfOnlyOneUsage()) {
            myFirstUsage = usage;
          }
          if (myUsageCount == 2 || (myProcessPresentation.isShowPanelIfOnlyOneUsage() && myUsageCount == 1)) {
            openView();
            if (myFirstUsage != null) {
              myUsageView[0].appendUsageLater(myFirstUsage);
            }
            myUsageView[0].appendUsageLater(usage);
          }
          else if (myUsageCount > 2) {
            myUsageView[0].appendUsageLater(usage);
          }

          final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          return indicator != null ? !indicator.isCanceled() : true;
        }
      });
      if (myUsageView[0] != null) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            showToolWindow(true);
          }
        });
      }
    }

    private void endSearchForUsages() {
      if (myUsageCount == 0 && myProcessPresentation.isShowNotFoundMessage()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final List<Action> notFoundActions = myProcessPresentation.getNotFoundActions();
            final String message = UsageViewBundle.message("dialog.no.usages.found.in",
                                                           StringUtil.decapitalize(myPresentation.getUsagesString()),
                                                           myPresentation.getScopeText());

            if (notFoundActions == null || notFoundActions.size() == 0) {
              Messages.showMessageDialog(myProject, message, UsageViewBundle.message("dialog.title.information"),
                                         Messages.getInformationIcon());
            } else {
              List<String> titles = new ArrayList<String>(notFoundActions.size()+1);
              titles.add(UsageViewBundle.message("dialog.button.ok"));
              for (int i = 0; i < notFoundActions.size(); i++) {
                Action action = notFoundActions.get(i);
                Object value = action.getValue(FindUsagesProcessPresentation.NAME_WITH_MNEMONIC_KEY);
                if (value == null) value = action.getValue(Action.NAME);

                titles.add((String)value);
              }

              int option = Messages.showDialog(myProject,
                                               message,
                                               UsageViewBundle.message("dialog.title.information"),
                                               titles.toArray(new String[titles.size()]),
                                               0,
                                               Messages.getInformationIcon());

              if (option > 0) {
                notFoundActions.get(option-1).actionPerformed(new ActionEvent(this,0,titles.get(option)));
              }
            }
          }
        }, ModalityState.NON_MMODAL);
      }
      else if (myUsageCount == 1 && !myProcessPresentation.isShowPanelIfOnlyOneUsage()) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (myFirstUsage.canNavigate()) {
              myFirstUsage.navigate(true);
            }
          }
        });
      }
      else {
        if (myUsageView[0] != null) myUsageView[0].setSearchInProgress(false);
      }

      if (myListener != null) {
        myListener.findingUsagesFinished(myUsageView[0]);
      }
    }
  }
}
