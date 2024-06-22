package com.intellij.history.integration.ui.models;

import com.intellij.diff.Block;
import com.intellij.diff.FindBlock;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectionCalculator {
  private IdeaGateway myGateway;
  private List<Revision> myRevisions;
  private int myFromLine;
  private int myToLine;
  private Map<Integer, Block> myCache = new HashMap<Integer, Block>();

  public SelectionCalculator(IdeaGateway gw, List<Revision> rr, int fromLine, int toLine) {
    myGateway = gw;
    myRevisions = rr;
    myFromLine = fromLine;
    myToLine = toLine;
  }

  public boolean canCalculateFor(Revision r, Progress p) {
    try {
      doGetSelectionFor(r, p);
    }
    catch (ContentIsUnavailableException e) {
      return false;
    }
    return true;
  }

  public Block getSelectionFor(Revision r, Progress p) {
    return doGetSelectionFor(r, p);
  }

  private Block doGetSelectionFor(Revision r, Progress p) {
    int target = myRevisions.indexOf(r);
    return getSelectionFor(target, target + 1, p);
  }

  private Block getSelectionFor(int revisionIndex, int totalRevisions, Progress p) {
    Block cached = myCache.get(revisionIndex);
    if (cached != null) return cached;

    String content = getRevisionContent(myRevisions.get(revisionIndex));
    p.processed(((totalRevisions - revisionIndex) * 100) / totalRevisions);

    Block result;
    if (revisionIndex == 0) {
      result = new Block(content, myFromLine, myToLine);
    }
    else {
      Block prev = getSelectionFor(revisionIndex - 1, totalRevisions, p);
      result = new FindBlock(content, prev).getBlockInThePrevVersion();
    }

    myCache.put(revisionIndex, result);

    return result;
  }

  private String getRevisionContent(Revision r) {
    Entry e = r.getEntry();
    Content c = e.getContent();
    if (!c.isAvailable()) throw new ContentIsUnavailableException();
    return c.getString(e, myGateway);
  }

  private static class ContentIsUnavailableException extends RuntimeException {
  }
}
