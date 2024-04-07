package com.intellij.cvsSupport2.cvsoperations.cvsContent;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.RepositoryPathProvider;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.AdminReaderOnStoredRepositoryPath;
import com.intellij.cvsSupport2.cvsoperations.common.RepositoryPathProvider;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContent;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContentListener;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContentProvider;
import com.intellij.openapi.util.text.StringUtil;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.update.UpdateCommand;

public class GetDirectoriesListViaUpdateOperation extends LocalPathIndifferentOperation implements DirectoryContentProvider {
  private final DirectoryContentListener myDirectoryContentListener = new DirectoryContentListener();

  public GetDirectoriesListViaUpdateOperation(CvsEnvironment env, final String parentDirectoryName) {
    super(new AdminReaderOnStoredRepositoryPath(createRepositoryPathProvider(parentDirectoryName)), env);
  }

  public static RepositoryPathProvider createRepositoryPathProvider(final String parentDirName) {
    return new RepositoryPathProvider() {
      public String getRepositoryPath(String repository) {
        String result = repository;
        if (!StringUtil.endsWithChar(result, '/')) result += "/";
        return result + parentDirName;
      }
    };

  }

  public void modifyOptions(GlobalOptions options) {
    super.modifyOptions(options);
    options.setDoNoChanges(true);
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    UpdateCommand command = new UpdateCommand();
    command.setBuildDirectories(true);

    root.getRevisionOrDate().setForCommand(command);
    command.setRecursive(true);

    return command;
  }

  public void messageSent(String message, boolean error, boolean tagged) {
    myDirectoryContentListener.messageSent(message);
  }

  public DirectoryContent getDirectoryContent() {
    return myDirectoryContentListener.getDirectoryContent();
  }

  protected String getOperationName() {
    return "update";
  }
}
