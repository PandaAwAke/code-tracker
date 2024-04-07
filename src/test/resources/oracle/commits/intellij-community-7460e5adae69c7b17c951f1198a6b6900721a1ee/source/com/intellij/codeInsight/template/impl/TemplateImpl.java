package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;

import java.util.*;

/**
 *
 */
public class TemplateImpl implements Template {
  private String myKey;
  private String myString = null;
  private String myDescription;
  private String myGroupName;
  private char myShortcutChar = TemplateSettings.DEFAULT_CHAR;
  private ArrayList<Variable> myVariables = new ArrayList<Variable>();
  private ArrayList<Segment> mySegments = null;
  private String myTemplateText = null;

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TemplateImpl)) return false;

    final TemplateImpl template = (TemplateImpl) o;

    if (isToReformat != template.isToReformat) return false;
    if (isToShortenLongNames != template.isToShortenLongNames) return false;
    if (myShortcutChar != template.myShortcutChar) return false;
    if (myDescription != null ? !myDescription.equals(template.myDescription) : template.myDescription != null) return false;
    if (myGroupName != null ? !myGroupName.equals(template.myGroupName) : template.myGroupName != null) return false;
    if (myKey != null ? !myKey.equals(template.myKey) : template.myKey != null) return false;
    if (myString != null ? !myString.equals(template.myString) : template.myString != null) return false;
    if (myTemplateText != null ? !myTemplateText.equals(template.myTemplateText) : template.myTemplateText != null) return false;

    if (myVariables == null && template.myVariables == null) return true;
    if (myVariables == null || template.myVariables == null) return false;
    if (myVariables.size() != template.myVariables.size()) return false;
    for (Iterator<Variable> it = myVariables.iterator(); it.hasNext();) {
      Variable variable =  it.next();
      if (template.myVariables.indexOf(variable) < 0) return false;
    }

    return true;
  }

  public int hashCode() {
    int result;
    result = myKey.hashCode();
    result = 29 * result + myString.hashCode();
    result = 29 * result + myGroupName.hashCode();
    return result;
  }

  private boolean isToReformat = false;
  private boolean isToShortenLongNames = true;
  private boolean toParseSegments = true;
  private TemplateContext myTemplateContext = new TemplateContext();

  public static final String END = "END";
  public static final String SELECTION = "SELECTION";
  public static final String SELECTION_START = "SELECTION_START";
  public static final String SELECTION_END = "SELECTION_END";
  public static final Set<String> INTERNAL_VARS_SET = new HashSet<String>(Arrays.asList(
      new String[] {END, SELECTION, SELECTION_START, SELECTION_END}
  ));

  private boolean isDeactivated = false;

  public boolean isInline() {
    return myIsInline;
  }

  private boolean isToIndent = true;


  public void setInline(boolean isInline) {
    myIsInline = isInline;
  }

  private boolean myIsInline = false;



  public TemplateImpl(String key, String group) {
    toParseSegments = false;
    myKey = key;
    myGroupName = group;
    myTemplateText = "";
    mySegments = new ArrayList<Segment>();
  }

  public void addTextSegment(String text) {
    text = StringUtil.convertLineSeparators(text, "\n");
    myTemplateText = myTemplateText + text;
  }

  public void addVariableSegment (String name) {
    mySegments.add(new Segment(name, myTemplateText.length()));
  }

  public void addVariable(String name, Expression expression, Expression defaultValueExpression, boolean isAlwaysStopAt) {
    Segment segment = new Segment(name, myTemplateText.length());
    mySegments.add(segment);
    Variable variable = new Variable(name, expression, defaultValueExpression, isAlwaysStopAt);
    myVariables.add(variable);
  }

  public void addEndVariable() {
    Segment segment = new Segment(END, myTemplateText.length());
    mySegments.add(segment);
  }

  public void addSelectionStartVariable() {
    Segment segment = new Segment(SELECTION_START, myTemplateText.length());
    mySegments.add(segment);
  }

  public void addSelectionEndVariable() {
    Segment segment = new Segment(SELECTION_END, myTemplateText.length());
    mySegments.add(segment);
  }

  public TemplateImpl(String key, String string, String group) {
    myKey = key;
    myString = string;
    myGroupName = group;
  }

  public TemplateImpl copy() {
    TemplateImpl template = new TemplateImpl(myKey, myString, myGroupName);
    template.myDescription = myDescription;
    template.myShortcutChar = myShortcutChar;
    template.isToReformat = isToReformat;
    template.isToShortenLongNames = isToShortenLongNames;
    template.myIsInline = myIsInline;
    template.myTemplateContext = (TemplateContext)myTemplateContext.clone();
    template.isDeactivated = isDeactivated;
    for(int i = 0; i < myVariables.size(); i++){
      Variable variable = myVariables.get(i);
      template.addVariable(variable.getName(), variable.getExpressionString(), variable.getDefaultValueString(), variable.isAlwaysStopAt());
    }
    return template;
  }

  public boolean isToReformat() {
    return isToReformat;
  }

  public void setToReformat(boolean toReformat) {
    isToReformat = toReformat;
  }

  public void setToIndent(boolean toIndent) {
    isToIndent = toIndent;
  }

  public boolean isToIndent() {
    return isToIndent;
  }

  public boolean isToShortenLongNames() {
    return isToShortenLongNames;
  }

  public void setToShortenLongNames(boolean toShortenLongNames) {
    isToShortenLongNames = toShortenLongNames;
  }

  public void setDeactivated(boolean isDeactivated) {
    this.isDeactivated = isDeactivated;
  }

  public boolean isDeactivated() {
    return isDeactivated;
  }

  public TemplateContext getTemplateContext() {
    return myTemplateContext;
  }

  public int getEndSegmentNumber() {
    return getVariableSegmentNumber(END);
  }

  public int getSelectionStartSegmentNumber() {
    return getVariableSegmentNumber(SELECTION_START);
  }

  public int getSelectionEndSegmentNumber() {
    return getVariableSegmentNumber(SELECTION_END);
  }

  public int getVariableSegmentNumber(String variableName) {
    parseSegments();
    for (int i = 0; i < mySegments.size(); i++) {
      Segment segment = mySegments.get(i);
      if (segment.name.equals(variableName)) {
        return i;
      }
    }
    return -1;
  }

  public String getTemplateText() {
    parseSegments();
    return myTemplateText;
  }

  public String getSegmentName(int i) {
    parseSegments();
    return mySegments.get(i).name;
  }

  public int getSegmentOffset(int i) {
    parseSegments();
    return mySegments.get(i).offset;
  }

  public int getSegmentsCount() {
    parseSegments();
    return mySegments.size();
  }

  public void parseSegments() {
    if(!toParseSegments) {
      return;
    }
    if(mySegments != null) {
      return;
    }
    myString = StringUtil.convertLineSeparators(myString, "\n");
    mySegments = new ArrayList<Segment>();
    StringBuffer buffer = new StringBuffer("");
    TemplateTextLexer lexer = new TemplateTextLexer();
    lexer.start(myString.toCharArray());
    while(true){
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      int start = lexer.getTokenStart();
      int end = lexer.getTokenEnd();
      String token = myString.substring(start, end);
      if (tokenType == TemplateTokenType.VARIABLE){
        String name = token.substring(1, token.length() - 1);
        Segment segment = new Segment(name, buffer.length());
        mySegments.add(segment);
      }
      else if (tokenType == TemplateTokenType.ESCAPE_DOLLAR){
        buffer.append("$");
      }
      else{
        buffer.append(token);
      }
      lexer.advance();
    }
    myTemplateText = buffer.toString();
  }

  public void removeAllParsed() {
    myVariables.clear();
    mySegments = null;
  }

  public void addVariable(String name, String expression, String defaultValue, boolean isAlwaysStopAt) {
    Variable variable = new Variable(name, expression, defaultValue, isAlwaysStopAt);
    myVariables.add(variable);
  }

  public int getVariableCount() {
    return myVariables.size();
  }

  public String getVariableNameAt(int i) {
    return myVariables.get(i).getName();
  }

  public String getExpressionStringAt(int i) {
    return myVariables.get(i).getExpressionString();
  }

  public Expression getExpressionAt(int i) {
    return myVariables.get(i).getExpression();
  }

  public String getDefaultValueStringAt(int i) {
    return myVariables.get(i).getDefaultValueString();
  }

  public Expression getDefaultValueAt(int i) {
    return myVariables.get(i).getDefaultValueExpression();
  }

  public boolean isAlwaysStopAt(int i) {
    return myVariables.get(i).isAlwaysStopAt();
  }

  public String getKey() {
    return myKey;
  }

  public void setKey(String key) {
    myKey = key;
  }

  public String getString() {
    parseSegments();
    return myString;
  }

  public void setString(String string) {
    myString = string;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public char getShortcutChar() {
    return myShortcutChar;
  }

  public void setShortcutChar(char shortcutChar) {
    myShortcutChar = shortcutChar;
  }

  public String getGroupName() {
    return myGroupName;
  }

  public void setGroupName(String groupName) {
    myGroupName = groupName;
  }

  public boolean isSelectionTemplate() {
    for (Iterator<Variable> iterator = myVariables.iterator(); iterator.hasNext();) {
      Variable v = iterator.next();
      if (v.getName().equals(SELECTION)) return true;
    }

    return false;
  }

  private static class Segment {
    public String name;
    public int offset;

    public Segment(String name, int offset) {
      this.name = name;
      this.offset = offset;
    }
  }
}
