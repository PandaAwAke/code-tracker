/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.Pair;

import java.util.ArrayList;

/**
 * A default implementation of {@link ActionGroup}. Provides the ability
 * to add children actions and separators between them. In most of the
 * cases you will be using this implementation but note that there are
 * cases (for example "Recent files" dialog) where children are determined
 * on rules different than just positional constraints, that's when you need
 * to implement your own <code>ActionGroup</code>.
 *
 * @see Constraints
 */
public class DefaultActionGroup extends ActionGroup {
  /**
   * Contains instances of AnAction
   */
  private ArrayList<AnAction> mySortedChildren;
  /**
   * Contains instances of Pair
   */
  private ArrayList<Pair<AnAction,Constraints>> myPairs;

  public DefaultActionGroup(){
    this(null, false);
  }

  public DefaultActionGroup(String shortName, boolean popup){
    super(shortName, popup);
    mySortedChildren = new ArrayList<AnAction>();
    myPairs = new ArrayList<Pair<AnAction, Constraints>>();
  }

  /**
   * Adds the specified action to the tail.
   *
   * @param action Action to be added
   */
  public final void add(AnAction action){
    add(action, new Constraints(Anchor.LAST, null));
  }

  /**
   * Adds a separator to the tail.
   */
  public final void addSeparator(){
    add(Separator.getInstance());
  }

  /**
   * Adds the specified action with the specified constraint.
   *
   * @param action Action to be added; cannot be null
   * @param constraint Constraint to be used for determining action's position; cannot be null
   * @throws IllegalArgumentException in case when:
   * <li>action is null
   * <li>constraint is null
   * <li>action is already in the group
   */
  public final void add(AnAction action, Constraints constraint){
    if (action == null) {
      throw new IllegalArgumentException("action cannot be null");
    }
    if (constraint == null) {
      throw new IllegalArgumentException("constraint cannot be null");
    }

    // Check that action isn't already registered

    if (!(action instanceof Separator)) {
      if (mySortedChildren.contains(action)){
        throw new IllegalArgumentException("cannot add an action twice");
      }
      for(int i = 0; i < myPairs.size(); i++){
        Pair<AnAction, Constraints> pair = myPairs.get(i);
        if (action.equals(pair.first)){
          throw new IllegalArgumentException("cannot add an action twice");
        }
      }
    }

    constraint = (Constraints)constraint.clone();

    if (constraint.myAnchor == Anchor.FIRST){
      mySortedChildren.add(0, action);
    }else if (constraint.myAnchor == Anchor.LAST){
      mySortedChildren.add(action);
    }else{
      if (addToSortedList(action, constraint)){
        actionAdded(action);
      }else{
        myPairs.add(new Pair<AnAction, Constraints>(action, constraint));
      }
    }
  }

  private void actionAdded(AnAction addedAction){
    String addedActionId;
    if(addedAction instanceof ActionStub){
      addedActionId=((ActionStub)addedAction).getId();
    }else{
      addedActionId=ActionManager.getInstance().getId(addedAction);
    }
    if (addedActionId == null){
      return;
    }
    outer: while(myPairs.size() > 0){
      for(int i = 0; i < myPairs.size(); i++){
        Pair<AnAction, Constraints> pair = myPairs.get(i);
        if (addToSortedList(pair.first, pair.second)){
          myPairs.remove(i);
          continue outer;
        }
      }
      break;
    }
  }

  private final boolean addToSortedList(AnAction action, Constraints constraint){
    int index = findIndex(constraint.myRelativeToActionId, mySortedChildren);
    if (index == -1){
      return false;
    }
    if (constraint.myAnchor == Anchor.BEFORE){
      mySortedChildren.add(index, action);
    }
    else{
      mySortedChildren.add(index + 1, action);
    }
    return true;
  }

  private final int findIndex(String actionId, ArrayList<AnAction> actions){
    ActionManager actionManager = ActionManager.getInstance();
    for(int i = 0; i < actions.size(); i++){
      AnAction action = actions.get(i);
      if(action instanceof ActionStub){
        if(((ActionStub)action).getId().equals(actionId)){
          return i;
        }
      }else{
        String id = actionManager.getId(action);
        if (id != null && id.equals(actionId)){
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Removes specified action from group.
   *
   * @param action Action to be removed
   */
  public final void remove(AnAction action){
    if (!mySortedChildren.remove(action)){
      for(int i = 0; i < myPairs.size(); i++){
        Pair<AnAction, Constraints> pair = myPairs.get(i);
        if (pair.first.equals(action)){
          myPairs.remove(i);
          break;
        }
      }
    }
  }

  /**
   * Removes all children actions (separators as well) from the group.
   */
  public final void removeAll(){
    mySortedChildren.clear();
    myPairs.clear();
  }

  /**
   * Returns group's children in the order determined by constraints.
   *
   * @param e not used
   *
   * @return An array of children actions
   */
  public final AnAction[] getChildren(AnActionEvent e){
    // Mix sorted actions and pairs
    int sortedSize = mySortedChildren.size();
    AnAction[] children = new AnAction[sortedSize + myPairs.size()];
    for(int i = 0; i < sortedSize; i++){
      children[i] = mySortedChildren.get(i);
    }
    for(int i = 0; i < myPairs.size(); i++){
      children[i + sortedSize] = myPairs.get(i).first;
    }
    // Replace ActionStubs with real actions
    outer: for(int i=0;i<children.length;i++){
      AnAction action=children[i];
      if(!(action instanceof ActionStub)){
        continue;
      }
      ActionStub stub=(ActionStub)action;
      // resolve action
             ActionManager actionManager = e != null ? e.getActionManager() : ActionManager.getInstance();
             AnAction actualAction = actionManager.getAction(stub.getId());

      // Find in sorted children first
      int index=mySortedChildren.indexOf(stub);
      if(index!=-1){
        children[i]=actualAction;
        mySortedChildren.set(index,actualAction);
        continue;
      }
      // Try to find action within pairs
      for(int j=0;j<myPairs.size();j++){
        Pair<AnAction, Constraints> pair=myPairs.get(j);
        if(pair.first.equals(stub)){
          children[i]=actualAction;
          myPairs.set(j,new Pair<AnAction, Constraints>(actualAction,pair.second));
          continue outer;
        }
      }
      throw new IllegalStateException("unknown stub: "+stub.getId());
    }
    return children;
  }

  /**
   * Returns the number of contained children (including separators).
   *
   * @return number of children in the group
   */
  public final int getChildrenCount(){
    return mySortedChildren.size() + myPairs.size();
  }
}
