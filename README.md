<h1>Code Tracker</h1>

This project aims to introduce CodeTracker, a refactoring-aware tool that can generate the commit change history for method and variable declarations in a Java project with a very high accuracy.

# Table of Contents

  * [How to Build](#how-to-build)
  * [How to add as a Maven dependency](#how-to-add-as-a-maven-dependency)
  * [How to Track Methods](#how-to-track-methods)
  * [How to Track Variables](#how-to-track-variables)
  * [How to Track Attributes](#how-to-track-attributes)
  * [Oracle](#oracle)
  * [Experiments](#experiments)


# How to Build
To build this project you need to have Maven.

Run `mvn install` in the root folder of CodeTracker.

# How to add as a Maven dependency

To add code-tracker as a maven dependency in your project, add the following dependency:

    <dependency>
      <groupId>org.codetracker</groupId>
      <artifactId>code-tracker</artifactId>
      <version>1.1-SNAPSHOT</version>
    </dependency>

# How to Track Methods
CodeTracker can track the history of methods in git repositories.

In the code snippet below we demonstrate how to print all changes performed in the history of [`public void fireErrors(String fileName, SortedSet<LocalizedMessage> errors)`](https://github.com/checkstyle/checkstyle/blob/119fd4fb33bef9f5c66fc950396669af842c21a3/src/main/java/com/puppycrawl/tools/checkstyle/Checker.java#L384).

```java
    GitService gitService = new GitServiceImpl();
    try (Repository repository = gitService.cloneIfNotExists("tmp/checkstyle",
            "https://github.com/checkstyle/checkstyle.git")){

        MethodTracker methodTracker = CodeTracker.methodTracker()
            .repository(repository)
            .filePath("src/main/java/com/puppycrawl/tools/checkstyle/Checker.java")
            .startCommitId("119fd4fb33bef9f5c66fc950396669af842c21a3")
            .methodName("fireErrors")
            .methodDeclarationLineNumber(384)
            .build();
     
        History<Method> methodHistory = methodTracker.track();

        for (History.HistoryInfo<Method> historyInfo : methodHistory.getHistoryInfoList()) {
            System.out.println("======================================================");
            System.out.println("Commit ID: " + historyInfo.getCommitId());
            System.out.println("Date: " + 
                LocalDateTime.ofEpochSecond(historyInfo.getCommitTime(), 0, ZoneOffset.UTC));
            System.out.println("Before: " + historyInfo.getElementBefore().getName());
            System.out.println("After: " + historyInfo.getElementAfter().getName());
            
            for (Change change : historyInfo.getChangeList()) {
                System.out.println(change.getType().getTitle() + ": " + change);
            }
        }
        System.out.println("======================================================");
    }
```

# How to Track Variables
CodeTracker can track the history of variables in git repositories.

In the code snippet below we demonstrate how to print all changes performed in the history of [`final String stripped`](https://github.com/checkstyle/checkstyle/blob/119fd4fb33bef9f5c66fc950396669af842c21a3/src/main/java/com/puppycrawl/tools/checkstyle/Checker.java#L385).

```java
    GitService gitService = new GitServiceImpl();
    try (Repository repository = gitService.cloneIfNotExists("tmp/checkstyle",
            "https://github.com/checkstyle/checkstyle.git")){

        VariableTracker variableTracker = CodeTracker.variableTracker()
            .repository(repository)
            .filePath("src/main/java/com/puppycrawl/tools/checkstyle/Checker.java")
            .startCommitId("119fd4fb33bef9f5c66fc950396669af842c21a3")
            .methodName("fireErrors")
            .methodDeclarationLineNumber(384)
            .variableName("stripped")
            .variableDeclarationLineNumber(385)
            .build();

        History<Variable> variableHistory = variableTracker.track();

        for (History.HistoryInfo<Variable> historyInfo : variableHistory.getHistoryInfoList()) {
            System.out.println("======================================================");
            System.out.println("Commit ID: " + historyInfo.getCommitId());
            System.out.println("Date: " + 
                LocalDateTime.ofEpochSecond(historyInfo.getCommitTime(), 0, ZoneOffset.UTC));
            System.out.println("Before: " + historyInfo.getElementBefore().getName());
            System.out.println("After: " + historyInfo.getElementAfter().getName());
            
            for (Change change : historyInfo.getChangeList()) {
                System.out.println(change.getType().getTitle() + ": " + change);
            }
        }
        System.out.println("======================================================");
    }
```
# How to Track Attributes
CodeTracker can track the history of attributes in git repositories.

In the code snippet below we demonstrate how to print all changes performed in the history of [`private PropertyCacheFile cacheFile`](https://github.com/checkstyle/checkstyle/blob/119fd4fb33bef9f5c66fc950396669af842c21a3/src/main/java/com/puppycrawl/tools/checkstyle/Checker.java#L132).

```java
    GitService gitService = new GitServiceImpl();
    try (Repository repository = gitService.cloneIfNotExists("tmp/checkstyle",
            "https://github.com/checkstyle/checkstyle.git")) {

        AttributeTracker attributeTracker = CodeTracker.attributeTracker()
                .repository(repository)
                .filePath("src/main/java/com/puppycrawl/tools/checkstyle/Checker.java")
                .startCommitId("119fd4fb33bef9f5c66fc950396669af842c21a3")
                .attributeName("cacheFile")
                .attributeDeclarationLineNumber(132)
                .build();

        History<Attribute> attributeHistory = attributeTracker.track();

        for (History.HistoryInfo<Attribute> historyInfo : attributeHistory.getHistoryInfoList()) {
            System.out.println("======================================================");
            System.out.println("Commit ID: " + historyInfo.getCommitId());
            System.out.println("Date: " + 
                LocalDateTime.ofEpochSecond(historyInfo.getCommitTime(), 0, ZoneOffset.UTC));
            System.out.println("Before: " + historyInfo.getElementBefore().getName());
            System.out.println("After: " + historyInfo.getElementAfter().getName());

            for (Change change : historyInfo.getChangeList()) {
                System.out.println(change.getType().getTitle() + ": " + change);
            }
        }
        System.out.println("======================================================");
    }
```

# Oracle
The oracle we used to evaluate CodeTracker is an extension of [CodeShovel oracle](https://github.com/ataraxie/codeshovel/tree/master/src/test/resources/oracles/java), including the evolution history of 200 methods and the evolution history of 1346 variables declared in these methods, is available in the following links:
* [Method](src/main/resources/oracle/method)
  * [Training](src/main/resources/oracle/method/training)
  * [Test](src/main/resources/oracle/method/test)
* [Variable](src/main/resources/oracle/variable)
  * [Training](src/main/resources/oracle/variable/training)
  * [Test](src/main/resources/oracle/variable/test)

### Some Samples of CodeShovel's false cases
In the extended oracle we fixed all inaccuracies that we found in the original oracle. For example, the following methods in the original oracle are erroneously matched with another method which is extracted from their body. In fact, these methods are *introduced* as a result of an Extract Method refactoring.
* Training
  * [checkstyle-CommonUtils-createPattern](src/main/resources/oracle/method/training/checkstyle-CommonUtils-createPattern.json)
  * [checkstyle-WhitespaceAroundCheck-shouldCheckSeparationFromNextToken](src/main/resources/oracle/method/training/checkstyle-WhitespaceAroundCheck-shouldCheckSeparationFromNextToken.json)
  * [checkstyle-WhitespaceAroundCheck-isNotRelevantSituation](src/main/resources/oracle/method/training/checkstyle-WhitespaceAroundCheck-isNotRelevantSituation.json)
  * [commons-lang-EqualsBuilder-reflectionAppend](src/main/resources/oracle/method/training/commons-lang-EqualsBuilder-reflectionAppend.json)
  * [commons-lang-RandomStringUtils-random](src/main/resources/oracle/method/training/commons-lang-RandomStringUtils-random.json)
  * [commons-lang-NumberUtils-isCreatable](src/main/resources/oracle/method/training/commons-lang-NumberUtils-isCreatable.json)
  * [flink-FileSystem-getUnguardedFileSystem](src/main/resources/oracle/method/training/flink-FileSystem-getUnguardedFileSystem.json)
  * [flink-RemoteStreamEnvironment-executeRemotely](src/main/resources/oracle/method/training/flink-RemoteStreamEnvironment-executeRemotely.json)
  * [hibernate-orm-SimpleValue-buildAttributeConverterTypeAdapter](src/main/resources/oracle/method/training/hibernate-orm-SimpleValue-buildAttributeConverterTypeAdapter.json)
  * [javaparser-MethodResolutionLogic-isApplicable](src/main/resources/oracle/method/training/javaparser-MethodResolutionLogic-isApplicable.json)
  * [javaparser-Difference-applyRemovedDiffElement](src/main/resources/oracle/method/training/javaparser-Difference-applyRemovedDiffElement.json)
  * [javaparser-JavaParserFacade-getTypeConcrete](src/main/resources/oracle/method/training/javaparser-JavaParserFacade-getTypeConcrete.json)
  * [jgit-IndexDiff-diff](src/main/resources/oracle/method/training/jgit-IndexDiff-diff.json)
  * [jgit-UploadPack-sendPack](src/main/resources/oracle/method/training/jgit-UploadPack-sendPack.json)
  * [junit4-ParentRunner-applyValidators](src/main/resources/oracle/method/training/junit4-ParentRunner-applyValidators.json)
  * [junit5-TestMethodTestDescriptor-invokeTestMethod](src/main/resources/oracle/method/training/junit5-TestMethodTestDescriptor-invokeTestMethod.json)
  * [junit5-DefaultLauncher-discoverRoot](src/main/resources/oracle/method/training/junit5-DefaultLauncher-discoverRoot.json)
  * [okhttp-Http2Connection-newStream](src/main/resources/oracle/method/training/okhttp-Http2Connection-newStream.json)
* Test
  * [commons-io-IOUtils-toInputStream](src/main/resources/oracle/method/test/commons-io-IOUtils-toInputStream.json)
  * [commons-io-FilenameUtils-wildcardMatch](src/main/resources/oracle/method/test/commons-io-FilenameUtils-wildcardMatch.json)
  * [hadoop-SchedulerApplicationAttempt-resetSchedulingOpportunities](src/main/resources/oracle/method/test/hadoop-SchedulerApplicationAttempt-resetSchedulingOpportunities.json)
  * [hibernate-search-ClassLoaderHelper-instanceFromName](src/main/resources/oracle/method/test/hibernate-search-ClassLoaderHelper-instanceFromName.json)
  * [spring-boot-DefaultErrorAttributes-addErrorMessage](src/main/resources/oracle/method/test/spring-boot-DefaultErrorAttributes-addErrorMessage.json)
  * [lucene-solr-QueryParserBase-addClause](src/main/resources/oracle/method/test/lucene-solr-QueryParserBase-addClause.json)
  * [intellij-community-ModuleCompileScope-isUrlUnderRoot](src/main/resources/oracle/method/test/intellij-community-ModuleCompileScope-isUrlUnderRoot.json)
  * [intellij-community-TranslatingCompilerFilesMonitor-isInContentOfOpenedProject](src/main/resources/oracle/method/test/intellij-community-TranslatingCompilerFilesMonitor-isInContentOfOpenedProject.json)
  * [mockito-MatchersBinder-bindMatchers](src/main/resources/oracle/method/test/mockito-MatchersBinder-bindMatchers.json)

### CodeTracker's misreporting samples
To avoid unnecessary processing and speed up the tracking process, CodeTracker excludes some files from the source code model. The excluding action may cause misreporting of change type in some special scenarios. Although CodeTracker supports three scenarios in which additional files need to be included in the source code model, it may misreport MoveMethod changes as FileMove because the child commit model did not include the origin file of the method. In the test oracle, there are three such cases: [case 1](https://github.com/jodavimehran/code-tracker/blob/master/src/main/resources/oracle/method/test/hadoop-SchedulerApplicationAttempt-resetSchedulingOpportunities.json), [case 2](https://github.com/jodavimehran/code-tracker/blob/master/src/main/resources/oracle/method/test/mockito-AdditionalMatchers-geq.json) and [case 3](https://github.com/jodavimehran/code-tracker/blob/master/src/main/resources/oracle/method/test/mockito-AdditionalMatchers-gt.json). 

# Experiments
### Execution Time:
  As part of our experiments, we measured the execution time of CodeTracker and CodeShovel to track each method's change history in the training and testing sets. All data we recorded for this experiment and the script for generating the execution time plots are available [here](experiments/execution-time).
### Tracking Accuracy
  All data we collect to compute the precision and recall of CodeTracker and CodeShovel at commit level and change level are available in the following links:
* [Method](experiments/tracking-accuracy/method)
* [Variable](experiments/tracking-accuracy/variable)
  
