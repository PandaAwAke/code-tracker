/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2000 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient.command;

import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.IOException;
import java.util.*;

/**
 * @author  Thomas Singer
 */
final class FileSystemScanner {

	// Fields =================================================================

	private final IClientEnvironment clientEnvironment;
	private final boolean recursive;

	// Setup ==================================================================

	public FileSystemScanner(IClientEnvironment clientEnvironment, boolean recursive) {
		BugLog.getInstance().assertNotNull(clientEnvironment);

		this.clientEnvironment = clientEnvironment;
		this.recursive = recursive;
	}

	// Actions ================================================================

	public void scan(List abstractFileObjects, CvsFiles cvsFiles) throws IOException {
		cvsFiles.clear();

		if (abstractFileObjects.size() == 0) {
			// if no arguments have been specified, then specify the
			// local directory - the "top level" for this command
			scanDirectories(DirectoryObject.getRoot(), cvsFiles);
		}
		else {
			for (Iterator it = abstractFileObjects.iterator(); it.hasNext();) {
				final AbstractFileObject fileOrDirectory = (AbstractFileObject)it.next();
				if (fileOrDirectory instanceof DirectoryObject) {
					final DirectoryObject directoryObject = (DirectoryObject)fileOrDirectory;
					scanDirectories(directoryObject, cvsFiles);
				}
				else if (fileOrDirectory instanceof FileObject) {
					final FileObject fileObject = (FileObject)fileOrDirectory;
					addRequestsForFile(fileObject, cvsFiles);
				}
			}
		}
	}

	// Utils ==================================================================

	private void scanDirectories(DirectoryObject rootDirectoryObject, CvsFiles cvsFiles) throws IOException {
		final List directories = new LinkedList();
		directories.add(rootDirectoryObject);
		while (directories.size() > 0) {
			final DirectoryObject directoryObject = (DirectoryObject)directories.remove(0);

			final List subDirectories = scanDirectory(directoryObject, cvsFiles);
			if (recursive) {
				directories.addAll(subDirectories);
			}
		}
	}

	private List scanDirectory(DirectoryObject directoryObject, CvsFiles cvsFiles) throws IOException {
		if (!clientEnvironment.getLocalFileReader().exists(directoryObject, clientEnvironment.getCvsFileSystem())) {
			return Collections.EMPTY_LIST;
		}

		cvsFiles.add(CvsFile.createCvsDirectory(directoryObject));

		final Set subDirectoryNames = new HashSet();
		final LocalFiles localFiles = new LocalFiles(directoryObject, clientEnvironment);

		// get all the entries we know about, and process them
		final Collection entries = clientEnvironment.getAdminReader().getEntries(directoryObject, clientEnvironment.getCvsFileSystem());
		for (Iterator it = entries.iterator(); it.hasNext();) {
			final Entry entry = (Entry)it.next();
			if (entry.isDirectory()) {
				subDirectoryNames.add(entry.getFileName());
			}
			else {
				final FileObject fileObject = FileObject.createInstance(directoryObject, entry.getFileName());
				final boolean fileExists = clientEnvironment.getLocalFileReader().exists(fileObject, clientEnvironment.getCvsFileSystem());

				cvsFiles.add(CvsFile.createCvsFileForEntry(fileObject, entry, fileExists));

				localFiles.removeFile(entry.getFileName());
			}
		}

		for (Iterator it = localFiles.getFileNames().iterator(); it.hasNext();) {
			final String fileName = (String)it.next();

			cvsFiles.add(CvsFile.createCvsFileForExistingFile(FileObject.createInstance(directoryObject, fileName)));
		}

		final List subDirectories = new ArrayList(subDirectoryNames.size());
		for (Iterator it = subDirectoryNames.iterator(); it.hasNext();) {
			final String directoryName = (String)it.next();
			subDirectories.add(DirectoryObject.createInstance(directoryObject, directoryName));
		}
		return subDirectories;
	}

	private void addRequestsForFile(FileObject fileObject, CvsFiles cvsFiles) {
		cvsFiles.add(CvsFile.createCvsDirectory(fileObject.getParent()));

		try {
			final Entry entry = clientEnvironment.getAdminReader().getEntry(fileObject, clientEnvironment.getCvsFileSystem());
			// a non-null entry means the file does exist in the
			// Entries file for this directory
			if (entry != null) {
				final boolean exists = clientEnvironment.getLocalFileReader().exists(fileObject, clientEnvironment.getCvsFileSystem());
				cvsFiles.add(CvsFile.createCvsFileForEntry(fileObject, entry, exists));
			}
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
	}
}
