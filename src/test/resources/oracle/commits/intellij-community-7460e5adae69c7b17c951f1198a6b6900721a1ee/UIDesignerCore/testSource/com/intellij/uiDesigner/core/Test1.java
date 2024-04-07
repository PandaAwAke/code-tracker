package com.intellij.uiDesigner.core;

import junit.framework.TestCase;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class Test1 extends TestCase{
  public void test1() {
    // 11
    //  2
    test(
      new int[][]{
        {0, 2},
        {1, 1}
      },
      new int[][]{
        {0, 1},
        {0, 1}
      }
    );
  }

  public void test2(){
    // 111
    //  2
    test(
      new int[][]{
        {0, 3},
        {1, 1}
      },
      new int[][]{
        {0, 1},
        {0, 1}
      }
    );
  }

  public void test3(){
    // 11
    // 2
    test(
      new int[][]{
        {0,2},
        {0,1}
      },
      new int[][]{
        {0, 1},
        {0, 1}
      }
    );
  }

  public void test4(){
    // 12
    // 33
    test(
      new int[][]{
        {0, 1},
        {1, 1},
        {0, 2}
      },
      new int[][]{
        {0, 1},
        {1, 1},
        {0, 2}
      }
    );
  }

  public void test5(){
    // 112
    //  333
    test(
      new int[][]{
        {0, 2},
        {2, 1},
        {1, 3}
      },
      new int[][]{
        {0, 1},
        {1, 1},
        {0, 2}
      }
    );
  }

  public void test7(){
    // 11 222
    //  3333
    test(
      new int[][]{
        {0, 2},
        {3, 3},
        {1, 4}
      },
      new int[][]{
        {0, 1},
        {1, 1},
        {0, 2}
      }
    );
  }

  public void test8(){
    //   111
    // 222
    test(
      new int[][]{
        {2, 3},
        {0, 3}
      },
      new int[][]{
        {0, 1},
        {0, 1}
      }
    );
  }

  public void test9(){
    // 1 22
    // 333
    test(
      new int[][]{
        {0, 1},
        {2, 2},
        {0, 3}
      },
      new int[][]{
        {0, 1},
        {1, 1},
        {0, 2}
      }
    );
  }

  public void test9a(){
    // 122
    // 33
    test(
      new int[][]{
        {0, 1},
        {1, 2},
        {0, 2}
      },
      new int[][]{
        {0, 1},
        {1, 1},
        {0, 2}
      }
    );
  }

  public void test10(){
    // 1 2
    test(
      new int[][]{
        {0, 1},
        {2, 1},
      },
      new int[][]{
        {0, 1},
        {1, 1},
      }
    );
  }

  private static void test(final int[][] src, final int[][] expected) {
    final int[] cells = new int[src.length];
    final int[] spans = new int[src.length];
    for (int i = 0; i < cells.length; i++) {
       cells[i] = src[i][0];
       spans[i] = src[i][1];
    }

    Util.eliminate(cells, spans, null);

    for (int i = 0; i < spans.length; i++) {
      if (cells[i] != expected[i][0] ||
        spans[i] != expected[i][1]
      ){
        for (int j = 0; j < spans.length; j++) {
          System.out.println("i=" + j + " expected ("+expected[j][0] + "," + expected[j][1] +"), result (" + cells[j] + "," + spans[j]+")");
        }
        assertTrue(false);
      }
    }
  }
}
