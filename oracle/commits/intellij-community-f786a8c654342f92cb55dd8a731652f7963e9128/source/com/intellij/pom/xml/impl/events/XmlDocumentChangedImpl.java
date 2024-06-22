package com.intellij.pom.xml.impl.events;

import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.XmlDocumentChanged;
import com.intellij.pom.xml.impl.XmlAspectChangeSetImpl;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.openapi.diagnostic.Logger;

public class XmlDocumentChangedImpl implements XmlDocumentChanged {
  private static final Logger LOG = Logger.getInstance("#com.intellij.pom.xml.impl.events.XmlDocumentChangedImpl");
  private XmlDocument myDocument;

  public XmlDocumentChangedImpl(XmlDocument document) {
    LOG.assertTrue(document != null);
    myDocument = document;
  }

  public XmlDocument getDocument() {
    return myDocument;
  }

  public static PomModelEvent createXmlDocumentChanged(PomModel source, XmlDocument document) {
    final PomModelEvent event = new PomModelEvent(source);
    final XmlAspectChangeSetImpl xmlAspectChangeSet = new XmlAspectChangeSetImpl(source, (XmlFile)document.getParent());
    xmlAspectChangeSet.add(new XmlDocumentChangedImpl(document));
    event.registerChangeSet(source.getModelAspect(XmlAspect.class), xmlAspectChangeSet);
    return event;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "Xml document changed";
  }

  public void accept(XmlChangeVisitor visitor) {
    visitor.visitDocumentChanged(this);
  }
}
