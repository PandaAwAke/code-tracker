package org.codetracker.blame;

import org.apache.commons.io.IOUtils;
import org.codetracker.FileTrackerImpl;
import org.codetracker.api.CodeElement;
import org.codetracker.api.History;
import org.codetracker.blame.impl.CodeTrackerBlame;
import org.codetracker.blame.impl.JGitBlame;
import org.codetracker.blame.model.LineBlameResult;
import org.codetracker.blame.util.BlameFormatter;
import org.codetracker.blame.util.TabularPrint;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.refactoringminer.api.GitService;
import org.refactoringminer.astDiff.utils.URLHelper;
import org.refactoringminer.util.GitServiceImpl;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.codetracker.blame.util.Utils.getBlameOutput;
import static org.codetracker.blame.util.Utils.getOwner;
import static org.codetracker.blame.util.Utils.getProject;

/* Created by pourya on 2024-06-26*/
@Disabled
public class CodeTrackerBlameTest {
    private static final GitService gitService = new GitServiceImpl();
    private final String REPOS_PATH = System.getProperty("user.dir") + "/tmp";

    @ParameterizedTest
    @MethodSource("testBlamerInputProvider")
    public void testBlameWithFormatting(String url, String filePath, IBlame blamer, String expectedPath) throws Exception {
        String actual = getBlameOutput(url, filePath, blamer, REPOS_PATH, gitService);
        assertEqualWithFile(expectedPath, actual);
    }

    private static Stream<Arguments> testBlamerInputProvider(){
        String url = "https://github.com/Alluxio/alluxio/commit/9aeefcd8120bb3b89cdb437d8c32d2ed84b8a825";
        String filePath = "servers/src/main/java/tachyon/worker/block/allocator/MaxFreeAllocator.java";
        return Stream.of(
                Arguments.of(url, filePath, new CodeTrackerBlame(), System.getProperty("user.dir") + "/src/test/resources/blame/formatting/codetracker.txt"),
                Arguments.of(url, filePath, new JGitBlame(), System.getProperty("user.dir") + "/src/test/resources/blame/formatting/git.txt")
        );
    }

    @ParameterizedTest
    @CsvSource({
        "https://github.com/Alluxio/alluxio/commit/9aeefcd8120bb3b89cdb437d8c32d2ed84b8a825, servers/src/main/java/tachyon/worker/block/allocator/MaxFreeAllocator.java, /src/test/resources/blame/blameTestWithLocalRepo1.txt",
        "https://github.com/pouryafard75/DiffBenchmark/commit/5b33dc6f8cfcf8c0e31966c035b0406eca97ec76, src/main/java/dat/MakeIntels.java, /src/test/resources/blame/blameTestWithLocalRepo2.txt",
        "https://github.com/checkstyle/checkstyle/commit/119fd4fb33bef9f5c66fc950396669af842c21a3, src/main/java/com/puppycrawl/tools/checkstyle/Checker.java, /src/test/resources/blame/blameTestWithLocalRepo3.txt",
        "https://github.com/javaparser/javaparser/commit/97555053af3025556efe1a168fd7943dac28a2a6, javaparser-core/src/main/java/com/github/javaparser/printer/lexicalpreservation/Difference.java, /src/test/resources/blame/blameTestWithLocalRepo4.txt",
        "https://github.com/javaparser/javaparser/commit/97555053af3025556efe1a168fd7943dac28a2a6, javaparser-symbol-solver-core/src/main/java/com/github/javaparser/symbolsolver/javaparsermodel/contexts/MethodCallExprContext.java, /src/test/resources/blame/blameTestWithLocalRepo5.txt",
        "https://github.com/spring-projects/spring-framework/commit/b325c74216fd9564a36602158fa1269e2e832874, spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/method/annotation/AbstractMessageConverterMethodProcessor.java, /src/test/resources/blame/blameTestWithLocalRepo6.txt",
        "https://github.com/junit-team/junit5/commit/77cfe71e7f787c59626198e25350545f41e968bd, junit-jupiter-engine/src/main/java/org/junit/jupiter/engine/descriptor/ClassTestDescriptor.java, /src/test/resources/blame/blameTestWithLocalRepo7.txt",
        "https://github.com/hibernate/hibernate-orm/commit/8bd79b29cfa7b2d539a746dc356d60b66e1e596b, hibernate-core/src/main/java/org/hibernate/cfg/AnnotationBinder.java, /src/test/resources/blame/blameTestWithLocalRepo8.txt",
        "https://github.com/apache/flink/commit/9e936a5f8198b0059e9b5fba33163c2bbe3efbdd, flink-streaming-java/src/main/java/org/apache/flink/streaming/api/datastream/DataStream.java, /src/test/resources/blame/blameTestWithLocalRepo9.txt",
        "https://github.com/checkstyle/checkstyle/commit/119fd4fb33bef9f5c66fc950396669af842c21a3, src/main/java/com/puppycrawl/tools/checkstyle/TreeWalker.java, /src/test/resources/blame/blameTestWithLocalRepo10.txt",
        "https://github.com/apache/commons-lang/commit/a36c903d4f1065bc59f5e6d2bb0f9d92a5e71d83, src/main/java/org/apache/commons/lang3/time/DateUtils.java, /src/test/resources/blame/blameTestWithLocalRepo11.txt",
        "https://github.com/apache/commons-lang/commit/a36c903d4f1065bc59f5e6d2bb0f9d92a5e71d83, src/main/java/org/apache/commons/lang3/time/DurationFormatUtils.java, /src/test/resources/blame/blameTestWithLocalRepo12.txt",
        "https://github.com/checkstyle/checkstyle/commit/119fd4fb33bef9f5c66fc950396669af842c21a3, src/main/java/com/puppycrawl/tools/checkstyle/checks/coding/FinalLocalVariableCheck.java, /src/test/resources/blame/blameTestWithLocalRepo13.txt",
        "https://github.com/apache/flink/commit/9e936a5f8198b0059e9b5fba33163c2bbe3efbdd, flink-runtime/src/main/java/org/apache/flink/runtime/dispatcher/DispatcherRestEndpoint.java, /src/test/resources/blame/blameTestWithLocalRepo14.txt",
        "https://github.com/junit-team/junit4/commit/02c328028b4d32c15bbf0becc9838e54ecbafcbf, src/main/java/org/junit/runners/BlockJUnit4ClassRunner.java, /src/test/resources/blame/blameTestWithLocalRepo15.txt",
        "https://github.com/square/okhttp/commit/5224f3045ba9b171fce521777edf389f9206173c, okhttp/src/main/java/okhttp3/internal/http2/Http2Connection.java, /src/test/resources/blame/blameTestWithLocalRepo16.txt",
        "https://github.com/apache/hadoop/commit/9c3fc3ef2865164aa5f121793ac914cfeb21a181, hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-resourcemanager/src/main/java/org/apache/hadoop/yarn/server/resourcemanager/scheduler/fifo/FifoScheduler.java, /src/test/resources/blame/blameTestWithLocalRepo17.txt",
        "https://github.com/mockito/mockito/commit/077562ea54f1fa87ff8dd233c3060ddbf0f1ce26, src/main/java/org/mockito/internal/invocation/MatchersBinder.java, /src/test/resources/blame/blameTestWithLocalRepo18.txt",
        "https://github.com/hibernate/hibernate-search/commit/5b778035965d7588ad1d1ae522c4bafebd3a0e16, engine/src/main/java/org/hibernate/search/backend/impl/StreamingOperationDispatcher.java, /src/test/resources/blame/blameTestWithLocalRepo19.txt",
        "https://github.com/eclipse/jetty.project/commit/fc5dd874f3deda71e6cd42af994a5af5cb6be4af, jetty-http2/http2-server/src/main/java/org/eclipse/jetty/http2/server/HttpTransportOverHTTP2.java, /src/test/resources/blame/blameTestWithLocalRepo20.txt",
        "https://github.com/pmd/pmd/commit/d528dcd5d45582229ab3410deb7c40b2143d015d, pmd-java/src/main/java/net/sourceforge/pmd/lang/java/typeresolution/ClassTypeResolver.java, /src/test/resources/blame/blameTestWithLocalRepo21.txt",
        "https://github.com/apache/tomcat/commit/dbc805e237b98834a7b7afb6da7be44da428c399, java/org/apache/coyote/http2/ConnectionSettings.java, /src/test/resources/blame/blameTestWithLocalRepo22.txt",
        "https://github.com/apache/ant/commit/2943e6c208b4152e8d3142168c67a3a23509ba2e, proposal/myrmidon/src/test/org/apache/myrmidon/components/property/test/DefaultPropertyResolverTestCase.java, /src/test/resources/blame/blameTestWithLocalRepo23.txt",
        "https://github.com/apache/ant/commit/f382fa32eec7260111db0d67ae9c90dee2d6de0b, proposal/myrmidon/src/java/org/apache/aut/vfs/impl/DefaultProviderContext.java, /src/test/resources/blame/blameTestWithLocalRepo24.txt",
        "https://github.com/apache/ant/commit/a5598c47d123f149d442fde20753321ab39658eb, proposal/myrmidon/src/main/org/apache/tools/todo/taskdefs/perforce/P4Submit.java, /src/test/resources/blame/blameTestWithLocalRepo25.txt",
        "https://github.com/apache/ant/commit/3244e244c928cd5372c33e30e0605ed69b35af49, proposal/myrmidon/src/todo/org/apache/tools/ant/util/regexp/JakartaOroRegexp.java, /src/test/resources/blame/blameTestWithLocalRepo26.txt",
        "https://github.com/apache/ant/commit/a5598c47d123f149d442fde20753321ab39658eb, proposal/myrmidon/src/main/org/apache/tools/todo/util/regexp/JakartaOroRegexp.java, /src/test/resources/blame/blameTestWithLocalRepo27.txt",
        "https://github.com/apache/ant/commit/a5598c47d123f149d442fde20753321ab39658eb, proposal/myrmidon/src/main/org/apache/tools/todo/util/regexp/Jdk14RegexpRegexp.java, /src/test/resources/blame/blameTestWithLocalRepo28.txt",
        "https://github.com/apache/ant/commit/a5598c47d123f149d442fde20753321ab39658eb, proposal/myrmidon/src/main/org/apache/tools/todo/util/regexp/JakartaRegexpRegexp.java, /src/test/resources/blame/blameTestWithLocalRepo29.txt",
        "https://github.com/eclipse/jgit/commit/bd1a82502680b5de5bf86f6c4470185fd1602386, org.eclipse.jgit/src/org/eclipse/jgit/internal/storage/pack/PackWriter.java, /src/test/resources/blame/blameTestUntilCommitZero.txt",
        "https://github.com/JetBrains/intellij-community/commit/ecb1bb9d4d484ae63ee77f8ad45bdce154db9356, java/compiler/impl/src/com/intellij/compiler/CompilerManagerImpl.java, /src/test/resources/blame/blameTestUntilCommitZero2.txt",
        "https://github.com/JetBrains/intellij-community/commit/ecb1bb9d4d484ae63ee77f8ad45bdce154db9356, java/compiler/impl/src/com/intellij/compiler/actions/CompileDirtyAction.java, /src/test/resources/blame/blameTestUntilCommitZero3.txt"
    })
    public void testBlameWithLocalRepoUsingFileTracker(String url, String filePath, String testResultFileName) throws Exception {
        String expectedFilePath = System.getProperty("user.dir") + testResultFileName;
        String commitId = URLHelper.getCommit(url);
        Repository repository = gitService.cloneIfNotExists(REPOS_PATH + "/" + getOwner(url) + "/" + getProject(url), URLHelper.getRepo(url));
        org.codetracker.util.Util.enableSHACache();
        FileTrackerImpl fileTracker = new FileTrackerImpl(repository, commitId, filePath);
        fileTracker.blame();
        BlameFormatter blameFormatter = new BlameFormatter(fileTracker.getLines());
        List<String[]> results = blameFormatter.make(fileTracker.getBlameInfo());
        String actual = TabularPrint.make(results);
        assertEqualWithFile(expectedFilePath, actual);
    }

    @Test
    public void blameTestAttributeAnnotation() throws Exception {
        String url = "https://github.com/eclipse/jgit/commit/bd1a82502680b5de5bf86f6c4470185fd1602386";
        String filePath = "org.eclipse.jgit/src/org/eclipse/jgit/internal/storage/pack/PackWriter.java";
        int lineNumber = 224;
        String expected = "LineBlameResult{commitId='1a6964c8274c50f0253db75f010d78ef0e739343', filePath='org.eclipse.jgit/src/org/eclipse/jgit/lib/PackWriter.java', shortCommitId='1a6964c82', beforeFilePath='org.eclipse.jgit/src/org/eclipse/jgit/lib/PackWriter.java', committer='Git Development Community', commitDate='1254268023', lineNumber=156}";
        String commitId = URLHelper.getCommit(url);
        Repository repository = gitService.cloneIfNotExists(REPOS_PATH + "/" + getOwner(url) + "/" + getProject(url), URLHelper.getRepo(url));
        History.HistoryInfo<? extends CodeElement> lineBlame =
                new CodeTrackerBlame().getLineBlame(repository, commitId, filePath, lineNumber);
        LineBlameResult lineBlameResult = LineBlameResult.of(lineBlame, lineNumber);
        Assertions.assertEquals(expected, lineBlameResult.toString());
    }

    @Test
    public void blameTestAttributeComment() throws Exception {
        String url = "https://github.com/checkstyle/checkstyle/commit/119fd4fb33bef9f5c66fc950396669af842c21a3";
        String filePath = "src/main/java/com/puppycrawl/tools/checkstyle/Checker.java";
        int lineNumber = 69;
        String expected = "LineBlameResult{commitId='b61daf7f44e5b3a817e712b6af84d6bca796fb28', filePath='src/main/java/com/puppycrawl/tools/checkstyle/Checker.java', shortCommitId='b61daf7f4', beforeFilePath='src/main/java/com/puppycrawl/tools/checkstyle/Checker.java', committer='rnveach', commitDate='1481152863', lineNumber=68}";
        String commitId = URLHelper.getCommit(url);
        Repository repository = gitService.cloneIfNotExists(REPOS_PATH + "/" + getOwner(url) + "/" + getProject(url), URLHelper.getRepo(url));
        History.HistoryInfo<? extends CodeElement> lineBlame =
                new CodeTrackerBlame().getLineBlame(repository, commitId, filePath, lineNumber);
        LineBlameResult lineBlameResult = LineBlameResult.of(lineBlame, lineNumber);
        Assertions.assertEquals(expected, lineBlameResult.toString());
    }

    @Test
    public void blameTestAttributeTDComment() throws Exception {
        String url = "https://github.com/apache/tomcat/commit/dbc805e237b98834a7b7afb6da7be44da428c399";
        String filePath = "java/org/apache/coyote/http2/ConnectionSettings.java";
        int lineNumber = 31;
        String expected = "LineBlameResult{commitId='a883a6b3dd2c096220e0e0ab2c0614773bb60787', filePath='java/org/apache/coyote/http2/ConnectionSettings.java', shortCommitId='a883a6b3d', beforeFilePath='java/org/apache/coyote/http2/ConnectionSettings.java', committer='Mark Thomas', commitDate='1432114403', lineNumber=31}";
        String commitId = URLHelper.getCommit(url);
        Repository repository = gitService.cloneIfNotExists(REPOS_PATH + "/" + getOwner(url) + "/" + getProject(url), URLHelper.getRepo(url));
        History.HistoryInfo<? extends CodeElement> lineBlame =
                new CodeTrackerBlame().getLineBlame(repository, commitId, filePath, lineNumber);
        LineBlameResult lineBlameResult = LineBlameResult.of(lineBlame, lineNumber);
        Assertions.assertEquals(expected, lineBlameResult.toString());
    }

    @Test
    public void testBlameLineRangeWithLocalRepo() throws Exception {
        String url = "https://github.com/checkstyle/checkstyle/commit/119fd4fb33bef9f5c66fc950396669af842c21a3";
        String filePath = "src/main/java/com/puppycrawl/tools/checkstyle/Checker.java";
        int fromLine = 443;
        int toLine = 487;
        String expectedFilePath = System.getProperty("user.dir") + "/src/test/resources/blame/blameLineRangeTestWithLocalRepo.txt";
        String actual = getBlameOutput(url, filePath, new CodeTrackerBlame(), REPOS_PATH, gitService, fromLine, toLine);
        assertEqualWithFile(expectedFilePath, actual);
    }

    public static void assertEqualWithFile(String expectedResultPath, String actual) throws IOException {
        String expected = IOUtils.toString(
                new FileInputStream(expectedResultPath),
                StandardCharsets.UTF_8
        );
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void blameTestSingleLine() throws Exception {
        String url = "https://github.com/pouryafard75/DiffBenchmark/commit/5b33dc6f8cfcf8c0e31966c035b0406eca97ec76";
        String filePath = "src/main/java/dat/MakeIntels.java";
        int lineNumber = 18;
        String expected = "LineBlameResult{commitId='ae3a4f8a50b1d25b2d3db50495a50feaa0b2b872', filePath='src/main/java/dat/MakeIntels.java', shortCommitId='ae3a4f8a5', beforeFilePath='src/main/java/dat/Make.java', committer='Pouryafard75', commitDate='1719280029', lineNumber=18}";

        String commitId = URLHelper.getCommit(url);
        Repository repository = gitService.cloneIfNotExists(REPOS_PATH + "/" + getOwner(url) + "/" + getProject(url), URLHelper.getRepo(url));
        History.HistoryInfo<? extends CodeElement> lineBlame =
                new CodeTrackerBlame().getLineBlame(repository, commitId, filePath, lineNumber);
        LineBlameResult lineBlameResult = LineBlameResult.of(lineBlame, lineNumber);
        Assertions.assertEquals(expected, lineBlameResult.toString());
    }

    @Test
    public void blameTestSingleLine2() throws Exception {
        String url = "https://github.com/pouryafard75/DiffBenchmark/commit/5b33dc6f8cfcf8c0e31966c035b0406eca97ec76";
        String filePath = "src/main/java/dat/MakeIntels.java";
        int lineNumber = 24;
        String expected = "LineBlameResult{commitId='e5e209d526b49544b2fb899bde8856290cf209a0', filePath='src/main/java/dat/Make.java', shortCommitId='e5e209d52', beforeFilePath='src/main/java/dat/Make.java', committer='Pouryafard75', commitDate='1709775227', lineNumber=20}";

        String commitId = URLHelper.getCommit(url);
        Repository repository = gitService.cloneIfNotExists(REPOS_PATH + "/" + getOwner(url) + "/" + getProject(url), URLHelper.getRepo(url));
        History.HistoryInfo<? extends CodeElement> lineBlame =
                new CodeTrackerBlame().getLineBlame(repository, commitId, filePath, lineNumber);
        LineBlameResult lineBlameResult = LineBlameResult.of(lineBlame, lineNumber);
        Assertions.assertEquals(expected, lineBlameResult.toString());
    }

    @Test
    public void blameTestSingleLine3() throws Exception {
        String url = "https://github.com/Alluxio/alluxio/commit/9aeefcd8120bb3b89cdb437d8c32d2ed84b8a825";
        String filePath = "servers/src/main/java/tachyon/worker/block/allocator/MaxFreeAllocator.java";
        int lineNumber = 78;
        String expected = "LineBlameResult{commitId='68514f3fe653a87899b0e0e7c9d2e67c85afefe0', filePath='servers/src/main/java/tachyon/worker/block/allocator/MaxFreeAllocator.java', shortCommitId='68514f3fe', beforeFilePath='servers/src/main/java/tachyon/worker/block/allocator/MaxFreeAllocator.java', committer='Carson Wang', commitDate='1435366348', lineNumber=49}";

        String commitId = URLHelper.getCommit(url);
        Repository repository = gitService.cloneIfNotExists(REPOS_PATH + "/" + getOwner(url) + "/" + getProject(url), URLHelper.getRepo(url));
        History.HistoryInfo<? extends CodeElement> lineBlame =
                new CodeTrackerBlame().getLineBlame(repository, commitId, filePath, lineNumber);
        LineBlameResult lineBlameResult = LineBlameResult.of(lineBlame, lineNumber);
        Assertions.assertEquals(expected, lineBlameResult.toString());
    }

    @Test
    public void blameTestPackageDeclarationJavadoc() throws Exception {
        String url = "https://github.com/structr/structr/commit/6c59050b8b03adf6d8043f3fb7add0496f447edf";
        String filePath = "structr-rest/src/main/java/org/structr/rest/resource/SchemaTypeResource.java";
        int lineNumber = 1;
        String expected = "LineBlameResult{commitId='2572fd72d96812328b1439434f8b42fccec694f8', filePath='structr-rest/src/main/java/org/structr/rest/resource/SchemaTypeResource.java', shortCommitId='2572fd72d', beforeFilePath='structr-rest/src/main/java/org/structr/rest/resource/SchemaTypeResource.java', committer='Axel Morgner', commitDate='1428756399', lineNumber=1}";

        String commitId = URLHelper.getCommit(url);
        Repository repository = gitService.cloneIfNotExists(REPOS_PATH + "/" + getOwner(url) + "/" + getProject(url), URLHelper.getRepo(url));
        History.HistoryInfo<? extends CodeElement> lineBlame =
                new CodeTrackerBlame().getLineBlame(repository, commitId, filePath, lineNumber);
        LineBlameResult lineBlameResult = LineBlameResult.of(lineBlame, lineNumber);
        Assertions.assertEquals(expected, lineBlameResult.toString());
    }

    @Test
    public void blameTestPackageDeclaration() throws Exception {
        String url = "https://github.com/structr/structr/commit/6c59050b8b03adf6d8043f3fb7add0496f447edf";
        String filePath = "structr-rest/src/main/java/org/structr/rest/resource/SchemaTypeResource.java";
        int lineNumber = 19;
        String expected = "LineBlameResult{commitId='4aefa2bab5f8cda021a05de29ccf046bbdc90b52', filePath='structr-rest/src/main/java/org/structr/rest/resource/SchemaTypeResource.java', shortCommitId='4aefa2bab', beforeFilePath='structr-rest/src/main/java/org/structr/rest/resource/SchemaTypeResource.java', committer='amorgner', commitDate='1356007184', lineNumber=48}";

        String commitId = URLHelper.getCommit(url);
        Repository repository = gitService.cloneIfNotExists(REPOS_PATH + "/" + getOwner(url) + "/" + getProject(url), URLHelper.getRepo(url));
        History.HistoryInfo<? extends CodeElement> lineBlame =
                new CodeTrackerBlame().getLineBlame(repository, commitId, filePath, lineNumber);
        LineBlameResult lineBlameResult = LineBlameResult.of(lineBlame, lineNumber);
        Assertions.assertEquals(expected, lineBlameResult.toString());
    }
}