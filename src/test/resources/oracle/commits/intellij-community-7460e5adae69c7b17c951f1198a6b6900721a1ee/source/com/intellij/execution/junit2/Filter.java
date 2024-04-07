package com.intellij.execution.junit2;

import com.intellij.rt.execution.junit2.states.PoolOfTestStates;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public abstract class Filter {
  /**
   * All instances (and subclasses's instances) should be singletons.
   * @see TestProxy#selectChildren
   */
  private Filter() {
  }

  public abstract boolean shouldAccept(TestProxy test);

  public List select(final List tests) {
    final ArrayList result = new ArrayList();
    for (Iterator iterator = tests.iterator(); iterator.hasNext();) {
      final TestProxy test = (TestProxy) iterator.next();
      if (shouldAccept(test))
        result.add(test);
    }
    return result;
  }

  public TestProxy detectIn(final Collection collection) {
    for (Iterator iterator = collection.iterator(); iterator.hasNext();) {
      final TestProxy test = (TestProxy) iterator.next();
      if (shouldAccept(test))
        return test;
    }
    return null;
  }

  private Filter not() {
    return new NotFilter(this);
  }

  private Filter and(final Filter filter) {
    return new AndFilter(this, filter);
  }

  public static final Filter NO_FILTER = new Filter() {
    public boolean shouldAccept(final TestProxy test) {
      return true;
    }
  };

  public static final Filter DEFECT = new Filter() {
    public boolean shouldAccept(final TestProxy test) {
      return test.getState().isDefect();
    }
  };

  public static final Filter NOT_PASSED = new Filter() {
    public boolean shouldAccept(final TestProxy test) {
      return test.getState().getMagnitude() > PoolOfTestStates.PASSED_INDEX;
    }
  };

  public static final Filter TEST_CASE = new Filter() {
    public boolean shouldAccept(final TestProxy test) {
      return test.getInfo().shouldRun();
    }
  };

  public static final Filter IN_PROGRESS = new Filter() {
    public boolean shouldAccept(final TestProxy test) {
      return test.getState().isInProgress();
    }
  };

  public static final Filter LEAF = new Filter() {
    public boolean shouldAccept(final TestProxy test) {
      return test.isLeaf();
    }
  };

  public static final Filter RUNNING = new Filter() {
    public boolean shouldAccept(final TestProxy test) {
      return test.getState().getMagnitude() == PoolOfTestStates.RUNNING_INDEX;
    }
  };

  public static final Filter NOT_LEAF = LEAF.not();
  public static final Filter RUNNING_LEAF = RUNNING.and(LEAF);

  public static final Filter DEFECTIVE_LEAF = DEFECT.and(LEAF);

  private static class AndFilter extends Filter {
    private final Filter myFilter1;
    private final Filter myFilter2;

    public AndFilter(final Filter filter1, final Filter filter2) {
      myFilter1 = filter1;
      myFilter2 = filter2;
    }

    public boolean shouldAccept(final TestProxy test) {
      return myFilter1.shouldAccept(test) && myFilter2.shouldAccept(test);
    }
  }

  private static class NotFilter extends Filter {
    private final Filter myFilter;

    public NotFilter(final Filter filter) {
      myFilter = filter;
    }

    public boolean shouldAccept(final TestProxy test) {
      return !myFilter.shouldAccept(test);
    }
  }
}
