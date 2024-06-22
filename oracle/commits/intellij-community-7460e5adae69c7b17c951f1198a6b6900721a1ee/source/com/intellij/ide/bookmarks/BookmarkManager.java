package com.intellij.ide.bookmarks;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import org.jdom.Element;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class BookmarkManager implements JDOMExternalizable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.bookmarks.BookmarkManager");
  private Project myProject;
  private BookmarksCollection.ForEditors myEditorBookmarks = new BookmarksCollection.ForEditors();
  private BookmarksCollection.ForPsiElements myCommanderBookmarks = new BookmarksCollection.ForPsiElements();
  private final MyEditorMouseListener myEditorMouseListener = new MyEditorMouseListener();

  public static BookmarkManager getInstance(Project project) {
    return project.getComponent(BookmarkManager.class);
  }

  BookmarkManager(Project project) {
    myProject = project;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectOpened() {
    EditorEventMulticasterEx eventMulticaster = (EditorEventMulticasterEx)EditorFactory.getInstance()
      .getEventMulticaster();
    eventMulticaster.addEditorMouseListener(myEditorMouseListener);
  }

  public void projectClosed() {
    EditorEventMulticasterEx eventMulticaster = (EditorEventMulticasterEx)EditorFactory.getInstance()
      .getEventMulticaster();
    eventMulticaster.removeEditorMouseListener(myEditorMouseListener);
  }

  public Project getProject() {
    return myProject;
  }

  public void addEditorBookmark(Document document, int lineIndex, int number) {
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (psiFile == null) return;
    String name = psiFile.getVirtualFile().getPath();
    if (name == null) return;
    RangeHighlighter lineMarker = ((MarkupModelEx)document.getMarkupModel(myProject)).addPersistentLineHighlighter(
      lineIndex, HighlighterLayer.ADDITIONAL_SYNTAX - 1, null);
    if (lineMarker == null) return;
    EditorBookmark bookmark = new EditorBookmark(document, myProject, lineMarker, "", number);
    myEditorBookmarks.addBookmark(bookmark);
  }

  public void addCommanderBookmark(PsiElement element) {
    if (element == null) return;
    if (myCommanderBookmarks.findByPsiElement(element) != null) return;

    if (element instanceof PsiClass || element instanceof PsiDirectory) {
      myCommanderBookmarks.addBookmark(new CommanderBookmark(myProject, element, ""));
    }
  }

  public List<EditorBookmark> getValidEditorBookmarks() {
    return myEditorBookmarks.getValidBookmarks();
  }

  public List<CommanderBookmark> getValidCommanderBookmarks() {
    return myCommanderBookmarks.getValidBookmarks();
  }

  public EditorBookmark findEditorBookmark(Document document, int lineIndex) {
    myEditorBookmarks.removeInvalidBookmarks();
    return myEditorBookmarks.findByDocumnetLine(document, lineIndex);
  }

  public EditorBookmark findNumberedEditorBookmark(int number) {
    myEditorBookmarks.removeInvalidBookmarks();
    return myEditorBookmarks.findByNumber(number);
  }

  public CommanderBookmark findCommanderBookmark(PsiElement element) {
    myCommanderBookmarks.removeInvalidBookmarks();
    return myCommanderBookmarks.findByPsiElement(element);
  }

  public void removeBookmark(Bookmark bookmark) {
    if (bookmark == null) return;
    selectBookmarksOfSameType(bookmark).removeBookmark(bookmark);
  }

  public void readExternal(Element element) throws InvalidDataException {
    for (Iterator iterator = element.getChildren().iterator(); iterator.hasNext();) {
      Element bookmarkElement = (Element)iterator.next();

      if (BookmarksCollection.ForEditors.ELEMENT_NAME.equals(bookmarkElement.getName())) {
        myEditorBookmarks.readBookmark(bookmarkElement, myProject);
      }
      else if (BookmarksCollection.ForPsiElements.ELEMENT_NAME.equals(bookmarkElement.getName())) {
        myCommanderBookmarks.readBookmark(bookmarkElement, myProject);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myEditorBookmarks.writeBookmarks(element);
    myCommanderBookmarks.writeBookmarks(element);
  }

  /**
   * Try to move bookmark one position up in the list
   * 
   * @return bookmark list after moving
   */
  public List<Bookmark> moveBookmarkUp(Bookmark bookmark) {
    BookmarksCollection bookmarks = selectBookmarksOfSameType(bookmark);
    return bookmarks.moveUp(bookmark);
  }

  private BookmarksCollection selectBookmarksOfSameType(Bookmark bookmark) {
    if (bookmark instanceof EditorBookmark) return myEditorBookmarks;
    if (bookmark instanceof CommanderBookmark) return myCommanderBookmarks;
    if (bookmark == null) {
      LOG.error("null");
    }
    else {
      LOG.error(bookmark.getClass().getName());
    }
    return null;
  }

  /**
   * Try to move bookmark one position down in the list
   * 
   * @return bookmark list after moving
   */
  public List<Bookmark> moveBookmarkDown(Bookmark bookmark) {
    BookmarksCollection bookmarks = selectBookmarksOfSameType(bookmark);
    return bookmarks.moveDown(bookmark);
  }

  public EditorBookmark getNextBookmark(Editor editor, boolean isWrapped) {
    EditorBookmark[] bookmarksForDocument = getBookmarksForDocument(editor.getDocument());
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    for (int i = 0; i < bookmarksForDocument.length; i++) {
      EditorBookmark bookmark = bookmarksForDocument[i];
      if (bookmark.getLineIndex() > lineNumber) return bookmark;
    }
    if (isWrapped && bookmarksForDocument.length > 0) {
      return bookmarksForDocument[0];
    }
    return null;
  }

  public EditorBookmark getPreviousBookmark(Editor editor, boolean isWrapped) {
    EditorBookmark[] bookmarksForDocument = getBookmarksForDocument(editor.getDocument());
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    for (int i = bookmarksForDocument.length - 1; i >= 0; i--) {
      EditorBookmark bookmark = bookmarksForDocument[i];
      if (bookmark.getLineIndex() < lineNumber) return bookmark;
    }
    if (isWrapped && bookmarksForDocument.length > 0) {
      return bookmarksForDocument[bookmarksForDocument.length - 1];
    }
    return null;
  }

  private EditorBookmark[] getBookmarksForDocument(Document document) {
    ArrayList<EditorBookmark> bookmarksVector = new ArrayList<EditorBookmark>();
    List<EditorBookmark> validEditorBookmarks = getValidEditorBookmarks();
    for (Iterator<EditorBookmark> iterator = validEditorBookmarks.iterator(); iterator.hasNext();) {
      EditorBookmark bookmark = iterator.next();
      if (document.equals(bookmark.getDocument())) {
        bookmarksVector.add(bookmark);
      }
    }
    EditorBookmark[] bookmarks = bookmarksVector.toArray(new EditorBookmark[bookmarksVector.size()]);
    Arrays.sort(bookmarks, new Comparator() {
      public int compare(Object o1, Object o2) {
        EditorBookmark bookmark1 = (EditorBookmark)o1;
        EditorBookmark bookmark2 = (EditorBookmark)o2;
        return bookmark1.getLineIndex() - bookmark2.getLineIndex();
      }
    });
    return bookmarks;
  }

  private class MyEditorMouseListener extends EditorMouseAdapter {
    public void mouseClicked(final EditorMouseEvent e) {
      if (e.getArea() != EditorMouseEventArea.LINE_MARKERS_AREA) return;
      if (e.getMouseEvent().isPopupTrigger()) return;
      if ((e.getMouseEvent().getModifiers() & MouseEvent.CTRL_MASK) == 0) return;

      Editor editor = e.getEditor();
      int line = editor.xyToLogicalPosition(new Point(e.getMouseEvent().getX(), e.getMouseEvent().getY())).line;
      if (line < 0) return;

      Document document = editor.getDocument();

      EditorBookmark bookmark = findEditorBookmark(document, line);
      if (bookmark == null) {
        addEditorBookmark(document, line, EditorBookmark.NOT_NUMBERED);
      }
      else {
        removeBookmark(bookmark);
      }
      e.consume();
    }
  }

  public String getComponentName() {
    return "BookmarkManager";
  }
}

