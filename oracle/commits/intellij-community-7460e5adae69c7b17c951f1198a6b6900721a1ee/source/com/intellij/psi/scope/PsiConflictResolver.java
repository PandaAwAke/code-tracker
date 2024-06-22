package com.intellij.psi.scope;

import com.intellij.psi.infos.CandidateInfo;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 31.03.2003
 * Time: 13:20:20
 * To change this template use Options | File Templates.
 */
public interface PsiConflictResolver {
  CandidateInfo resolveConflict(List<CandidateInfo> conflicts);
  void handleProcessorEvent(PsiScopeProcessor.Event event, Object associatied);
}
