package com.intellij.packageDependencies.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.util.scopeChooser.PackageSetChooserCombo;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependencyRule;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.refactoring.ui.EditableRowTableManager;
import com.intellij.refactoring.ui.RowEditableTableModel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.CellEditorComponentWithBrowseButton;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.analysis.AnalysisScopeBundle;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DependencyConfigurable extends BaseConfigurable {
  private Project myProject;
  private MyTableModel myDenyRulesModel;
  private MyTableModel myAllowRulesModel;
  private TableView myDenyTable;
  private TableView myAllowTable;

  private final ColumnInfo<DependencyRule, NamedScope> DENY_USAGES_OF = new LeftColumn(AnalysisScopeBundle.message("dependency.configurable.deny.table.column1"));
  private final ColumnInfo<DependencyRule, NamedScope> DENY_USAGES_IN = new RightColumn(AnalysisScopeBundle.message("dependency.configurable.deny.table.column2"));
  private final ColumnInfo<DependencyRule, NamedScope> ALLOW_USAGES_OF = new LeftColumn(AnalysisScopeBundle.message("dependency.configurable.allow.table.column1"));
  private final ColumnInfo<DependencyRule, NamedScope> ALLOW_USAGES_ONLY_IN = new RightColumn(AnalysisScopeBundle.message("dependency.configurable.allow.table.column2"));

  public DependencyConfigurable(Project project) {
    myProject = project;
  }

  public String getDisplayName() {
    return AnalysisScopeBundle.message("dependency.configurable.display.name");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "editing.analyzeDependencies.validation";
  }

  public JComponent createComponent() {
    myDenyRulesModel = new MyTableModel(new ColumnInfo[]{DENY_USAGES_OF, DENY_USAGES_IN}, true);
    myDenyRulesModel.setSortable(false);

    myAllowRulesModel = new MyTableModel(new ColumnInfo[]{ALLOW_USAGES_OF, ALLOW_USAGES_ONLY_IN}, false);
    myAllowRulesModel.setSortable(false);

    JPanel wholePanel = new JPanel(new GridLayout(2, 1, 10, 10));
    myDenyTable = new TableView(myDenyRulesModel);
    wholePanel.add(createRulesPanel(myDenyRulesModel, myDenyTable));
    myAllowTable = new TableView(myAllowRulesModel);
    wholePanel.add(createRulesPanel(myAllowRulesModel, myAllowTable));
    return wholePanel;
  }

  private JPanel createRulesPanel(MyTableModel model, TableView table) {
    JPanel panel = new JPanel(new BorderLayout());
    table.setSurrendersFocusOnKeystroke(true);

    JPanel buttonsPanel = new EditableRowTableManager(table, model, true).getButtonsPanel();
    panel.add(buttonsPanel, BorderLayout.EAST);

    table.setShowGrid(true);
    table.setRowHeight(new PackageSetChooserCombo(myProject, null).getPreferredSize().height);

    panel.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);
    table.setPreferredScrollableViewportSize(new Dimension(300, 150));
    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myDenyTable;
  }

  public void apply() throws ConfigurationException {
    stopTableEditing();
    DependencyValidationManager validationManager = DependencyValidationManager.getInstance(myProject);
    validationManager.removeAllRules();
    List<DependencyRule> modelItems = new ArrayList<DependencyRule>();
    modelItems.addAll(myDenyRulesModel.getItems());
    modelItems.addAll(myAllowRulesModel.getItems());
    for (int i = 0; i < modelItems.size(); i++) {
      DependencyRule rule = modelItems.get(i);
      if (isRuleValid(rule)) {
        validationManager.addRule(rule);
      }
    }

    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  private void stopTableEditing() {
    myDenyTable.stopEditing();
    myAllowTable.stopEditing();
  }

  private boolean isRuleValid(DependencyRule rule) {
    return scopeExists(rule.getFromScope()) && scopeExists(rule.getToScope());
  }

  private boolean scopeExists(NamedScope scope) {
    return scope != null && DependencyValidationManager.getInstance(myProject).getScope(scope.getName()) != null;
  }

  public void reset() {
    DependencyRule[] rules = DependencyValidationManager.getInstance(myProject).getAllRules();
    final ArrayList<DependencyRule> denyList = new ArrayList<DependencyRule>();
    final ArrayList<DependencyRule> allowList = new ArrayList<DependencyRule>();
    for (int i = 0; i < rules.length; i++) {
      DependencyRule rule = rules[i];
      if (rule.isDenyRule()) {
        denyList.add(rule.createCopy());
      }
      else {
        allowList.add(rule.createCopy());
      }
    }
    myDenyRulesModel.setItems(denyList);
    myAllowRulesModel.setItems(allowList);
  }

  public boolean isModified() {
    final List<DependencyRule> rules = new ArrayList<DependencyRule>();
    rules.addAll(myDenyRulesModel.getItems());
    rules.addAll(myAllowRulesModel.getItems());
    return !Arrays.asList(DependencyValidationManager.getInstance(myProject).getAllRules()).equals(rules);
  }

  public void disposeUIResources() {
  }

  private static final DefaultTableCellRenderer
    CELL_RENDERER = new DefaultTableCellRenderer() {
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setText(value == null ? "" : ((NamedScope)value).getName());
        return this;
      }
    };

  public abstract class MyColumnInfo extends ColumnInfo<DependencyRule, NamedScope> {
    protected MyColumnInfo(String name) {
      super(name);
    }

    public boolean isCellEditable(DependencyRule rule) {
      return true;
    }

    public TableCellRenderer getRenderer(DependencyRule rule) {
      return CELL_RENDERER;
    }

    public TableCellEditor getEditor(DependencyRule packageSetDependencyRule) {
      return new AbstractTableCellEditor() {
        private PackageSetChooserCombo myCombo;

        public Object getCellEditorValue() {
          return myCombo.getSelectedScope();
        }

        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
          myCombo = new PackageSetChooserCombo(myProject, value == null ? null : ((NamedScope)value).getName());
          return new CellEditorComponentWithBrowseButton(myCombo, this);
        }
      };
    }

    public abstract void setValue(DependencyRule rule, NamedScope packageSet);
  }


  private class RightColumn extends MyColumnInfo {
    public RightColumn(final String name) {
      super(name);
    }

    public NamedScope valueOf(DependencyRule rule) {
      return rule.getFromScope();
    }

    public void setValue(DependencyRule rule, NamedScope set) {
      rule.setFromScope(set);
    }
  }

  private class LeftColumn extends MyColumnInfo {
    public LeftColumn(final String name) {
      super(name);
    }

    public NamedScope valueOf(DependencyRule rule) {
      return rule.getToScope();
    }

    public void setValue(DependencyRule rule, NamedScope set) {
      rule.setToScope(set);
    }
  }

  private class MyTableModel extends ListTableModel<DependencyRule> implements RowEditableTableModel {
    private boolean myDenyRule;

    public MyTableModel(final ColumnInfo[] columnInfos, final boolean isDenyRule) {
      super(columnInfos);
      myDenyRule = isDenyRule;
    }

    public void addRow() {
      ArrayList<DependencyRule> newList = new ArrayList<DependencyRule>(getItems());
      newList.add(new DependencyRule(null, null, myDenyRule));
      setItems(newList);
    }

    public void exchangeRows(int index1, int index2) {
      ArrayList<DependencyRule> newList = new ArrayList<DependencyRule>(getItems());
      DependencyRule r1 = newList.get(index1);
      DependencyRule r2 = newList.get(index2);
      newList.set(index1, r2);
      newList.set(index2, r1);
      setItems(newList);
    }
  }
}