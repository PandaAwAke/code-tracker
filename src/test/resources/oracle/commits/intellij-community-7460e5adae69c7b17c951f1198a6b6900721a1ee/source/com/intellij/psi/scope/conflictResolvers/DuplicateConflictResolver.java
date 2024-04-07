package com.intellij.psi.scope.conflictResolvers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.HashMap;

import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 31.03.2003
 * Time: 17:21:42
 * To change this template use Options | File Templates.
 */
public class DuplicateConflictResolver implements PsiConflictResolver{
  public CandidateInfo resolveConflict(List<CandidateInfo> conflicts){
    final Map uniqueItems = new HashMap();

    Object[] elements = conflicts.toArray();
    for(int i = 0; i < elements.length; i++){
      final CandidateInfo info = ((CandidateInfo)elements[i]);
      final PsiElement element = info.getElement();
      Object key;
      if(element instanceof PsiMethod){
        key = ((PsiMethod)element).getSignature(info.getSubstitutor());
      }
      else {
        key = PsiUtil.getName(element);
      }

      if(!uniqueItems.containsKey(key)){
        uniqueItems.put(key, element);
      }
      else{
        conflicts.remove(elements[i]);
      }
    }
    if(uniqueItems.size() == 1) return conflicts.get(0);
    return null;
  }

  public void handleProcessorEvent(PsiScopeProcessor.Event event, Object associatied){}
}
