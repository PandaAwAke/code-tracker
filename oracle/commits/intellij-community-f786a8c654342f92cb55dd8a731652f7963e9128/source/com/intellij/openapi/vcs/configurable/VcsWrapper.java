package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;

class VcsWrapper {
  private final AbstractVcs myOriginal;

  public VcsWrapper(final AbstractVcs original) {
    myOriginal = original;
  }

  public AbstractVcs getOriginal() {
    return myOriginal;
  }

  public String toString() {
    return getDisplayName(myOriginal);
  }

  protected String getDisplayName(final AbstractVcs vcs) {
    if (vcs == null) {
      return VcsBundle.message("none.vcs.presentation");
    }
    else {
      return vcs.getDisplayName();
    }
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VcsWrapper that = (VcsWrapper)o;

    return !(myOriginal != null ? !myOriginal.equals(that.myOriginal) : that.myOriginal != null);

  }

  public int hashCode() {
    return (myOriginal != null ? myOriginal.hashCode() : 0);
  }
}
