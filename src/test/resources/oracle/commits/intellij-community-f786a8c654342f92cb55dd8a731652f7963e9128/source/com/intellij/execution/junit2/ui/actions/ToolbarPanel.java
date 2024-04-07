package com.intellij.execution.junit2.ui.actions;

import com.intellij.execution.Location;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.FailedTestsNavigator;
import com.intellij.execution.junit2.ui.TestsUIUtil;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.junit2.ui.properties.ScrollToTestSourceAction;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.actions.CollapseAllToolbarAction;
import com.intellij.ide.actions.ExpandAllToolbarAction;
import com.intellij.ide.actions.NextOccurenceToolbarAction;
import com.intellij.ide.actions.PreviousOccurenceToolbarAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.config.ToggleBooleanProperty;

import javax.swing.*;
import java.awt.*;

import org.jetbrains.annotations.NonNls;

public class ToolbarPanel extends JPanel implements OccurenceNavigator {
  //private final DefaultActionGroup myActions = new DefaultActionGroup(null, false);
  private final TestTreeExpander myTreeExpander = new TestTreeExpander();
  private final FailedTestsNavigator myOccurenceNavigator = new FailedTestsNavigator();
  private final ScrollToTestSourceAction myScrollToSource;
  @NonNls protected static final String TEST_SUITE_CLASS_NAME = "junit.framework.TestSuite";

  public ToolbarPanel(final JUnitConsoleProperties properties) {
    super (new BorderLayout());
    add(new JLabel(IconLoader.getIcon("/general/inactiveSeparator.png")), BorderLayout.WEST);
    final DefaultActionGroup actionGroup = new DefaultActionGroup(null, false);
    actionGroup.addSeparator();
    actionGroup.add(new ToggleBooleanProperty(ExecutionBundle.message("junit.run.hide.passed.action.name"),
                                              ExecutionBundle.message("junit.run.hide.passed.action.description"),
                                              TestsUIUtil.loadIcon("hidePassed"),
                                              properties, JUnitConsoleProperties.HIDE_PASSED_TESTS));
    actionGroup.add(new ToggleBooleanProperty(ExecutionBundle.message("junit.runing.info.track.test.action.name"),
                                              ExecutionBundle.message("junit.runing.info.track.test.action.description"),
                                              TestsUIUtil.loadIcon("trackTests"),
                                              properties, JUnitConsoleProperties.TRACK_RUNNING_TEST));
    actionGroup.addSeparator();
    actionGroup.add(new CollapseAllToolbarAction(myTreeExpander, ExecutionBundle.message("junit.runing.info.collapse.test.action.name")));
    actionGroup.add(new ExpandAllToolbarAction(myTreeExpander, ExecutionBundle.message("junit.runing.info.expand.test.action.name")));

    actionGroup.addSeparator();
    actionGroup.add(new PreviousOccurenceToolbarAction(myOccurenceNavigator));
    actionGroup.add(new NextOccurenceToolbarAction(myOccurenceNavigator));
    actionGroup.addSeparator();
    actionGroup.add(new ToggleBooleanProperty(ExecutionBundle.message("junit.runing.info.select.first.failed.action.name"),
                                              null,
                                              TestsUIUtil.loadIcon("selectFirstDefect"),
                                              properties, JUnitConsoleProperties.SELECT_FIRST_DEFECT));
    actionGroup.add(new ToggleBooleanProperty(ExecutionBundle.message("junit.runing.info.scroll.to.stacktrace.action.name"),
                                              ExecutionBundle.message("junit.runing.info.scroll.to.stacktrace.action.description"),
                                              IconLoader.getIcon("/runConfigurations/scrollToStackTrace.png"),
                                              properties, JUnitConsoleProperties.SCROLL_TO_STACK_TRACE));
    myScrollToSource = new ScrollToTestSourceAction(properties);
    actionGroup.add(myScrollToSource);
    actionGroup.add(new ToggleBooleanProperty(ExecutionBundle.message("junit.runing.info.open.source.at.exception.action.name"),
                                              ExecutionBundle.message("junit.runing.info.open.source.at.exception.action.description"),
                                              IconLoader.getIcon("/runConfigurations/sourceAtException.png"),
                                              properties, JUnitConsoleProperties.OPEN_FAILURE_LINE));
    add(ActionManager.getInstance().
        createActionToolbar(ActionPlaces.TESTTREE_VIEW_TOOLBAR, actionGroup, true).
        getComponent(), BorderLayout.CENTER);
  }

  public void setModel(final JUnitRunningModel model) {
    JUnitActions.installFilterAction(model);
    myScrollToSource.setModel(model);
    RunningTestTracker.install(model);
    myTreeExpander.setModel(model);
    myOccurenceNavigator.setModel(model);
    JUnitActions.installAutoacrollToFirstDefect(model);
    model.addListener(new LvcsLabeler(model));
    model.addListener(new JUnitAdapter() {
      public void onTestSelected(final TestProxy test) {
        if (test == null) return;
        final Project project = model.getProject();
        if (!ScrollToTestSourceAction.isScrollEnabled(model)) return;
        final Location location = test.getInfo().getLocation(project);
        if (location != null) {
          final PsiClass aClass = PsiTreeUtil.getParentOfType(location.getPsiElement(), PsiClass.class, false);
          if (aClass != null && TEST_SUITE_CLASS_NAME.equals(aClass.getQualifiedName())) return;
        }
        final Navigatable descriptor = TestsUIUtil.getOpenFileDescriptor(test, model);
        if (descriptor != null && descriptor.canNavigate()) {
          descriptor.navigate(false);
        }
      }
    });
  }

  public boolean hasNextOccurence() {
    return myOccurenceNavigator.hasNextOccurence();
  }

  public boolean hasPreviousOccurence() {
    return myOccurenceNavigator.hasPreviousOccurence();
  }

  public OccurenceNavigator.OccurenceInfo goNextOccurence() {
    return myOccurenceNavigator.goNextOccurence();
  }

  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigator.goPreviousOccurence();
  }

  public String getNextOccurenceActionName() {
    return myOccurenceNavigator.getNextOccurenceActionName();
  }

  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigator.getPreviousOccurenceActionName();
  }
}
