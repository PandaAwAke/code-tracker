package org.codetracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.cli.*;
import org.codetracker.api.CodeTracker;
import org.codetracker.api.History;
import org.codetracker.api.MethodTracker;
import org.codetracker.element.Method;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitService;
import org.refactoringminer.util.GitServiceImpl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TrackMethodHistoryCommitsMain {

    public static void main(String[] args) throws Exception {
        // Options
        Options options = new Options();
        options.addOption("r", "repository", true, "Git repository directory path");
        options.addOption("c", "startCommitId", true, "Start commit ID");
        options.addOption("f", "filePath", true, "File path in the repository");
        options.addOption("m", "methodName", true, "Method name");
        options.addOption("l", "lineNumber", true, "Method declaration line number");

        // Parse the options
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);

            // Get values of the options
            String repositoryPath = cmd.getOptionValue("repository");
            String startCommitId = cmd.getOptionValue("startCommitId");
            String filePath = cmd.getOptionValue("filePath");
            String methodName = cmd.getOptionValue("methodName");
            int methodDeclarationLineNumber = Integer.parseInt(cmd.getOptionValue("lineNumber"));

            GitService gitService = new GitServiceImpl();
            Repository repository = gitService.openRepository(repositoryPath);


            MethodTracker methodTracker = CodeTracker.methodTracker()
                    .repository(repository)
                    .filePath(filePath)
                    .startCommitId(startCommitId)
                    .methodName(methodName)
                    .methodDeclarationLineNumber(methodDeclarationLineNumber)
                    .build();

            History<Method> methodHistory = methodTracker.track();

            // From earliest to latest
            List<History.HistoryInfo<Method>> historyInfoList = new ArrayList<>(methodHistory.getHistoryInfoList());
            Collections.reverse(historyInfoList);

            // Transfer to json
            ObjectMapper objectMapper = new ObjectMapper();
            ArrayNode commitArray = objectMapper.createArrayNode();
            for (History.HistoryInfo<Method> historyInfo : historyInfoList) {
                String commitId = historyInfo.getCommitId();
                String date = LocalDateTime.ofEpochSecond(historyInfo.getCommitTime(), 0, ZoneOffset.UTC).toString();
                String before = historyInfo.getElementBefore().getName();
                String after = historyInfo.getElementAfter().getName();

                List<String> changeTypes = historyInfo.getChangeList().stream()
                        .map(change -> change.getType().getTitle())
                        .collect(Collectors.toList());

                MethodChange methodChange = new MethodChange(commitId, date, before, after, changeTypes);
                ObjectNode changeJson = objectMapper.valueToTree(methodChange);
                commitArray.add(changeJson);
            }

            // Create json object
            ObjectNode resultJson = objectMapper.createObjectNode();
            resultJson.set("commits", commitArray);

            // Print json
            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultJson);
            System.out.println(jsonString);

//            /*
//             * Example:
//             *
//             * Commit ID: 87cb639629ddbe01cdda3ca90cf6cbffed641b88
//             * Date: 2023-03-12T11:09:14
//             * Before: src/main/java/pascal.taie.Main#processConfigs(Options)
//             * After: src/main/java/pascal.taie.Main#processConfigs(Options)
//             * body change: Body Change
//             */
//            for (History.HistoryInfo<Method> historyInfo : methodHistory.getHistoryInfoList()) {
//                System.out.println("======================================================");
//                System.out.println("Commit ID: " + historyInfo.getCommitId());
//                System.out.println("Date: " +
//                        LocalDateTime.ofEpochSecond(historyInfo.getCommitTime(), 0, ZoneOffset.UTC));
//                System.out.println("Before: " + historyInfo.getElementBefore().getName());
//                System.out.println("After: " + historyInfo.getElementAfter().getName());
//
//                for (Change change : historyInfo.getChangeList()) {
//                    System.out.println(change.getType().getTitle() + ": " + change);
//                }
//            }
        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Main", options);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    static class MethodChange {
        String commitId;
        String date;
        String before, after;
        List<String> changeTypes;

        public MethodChange(String commitId, String date, String before, String after, List<String> changeTypes) {
            this.commitId = commitId;
            this.date = date;
            this.before = before;
            this.after = after;
            this.changeTypes = changeTypes;
        }

        public String getCommitId() {
            return commitId;
        }

        public String getDate() {
            return date;
        }

        public String getBefore() {
            return before;
        }

        public String getAfter() {
            return after;
        }

        public List<String> getChangeTypes() {
            return changeTypes;
        }
    }

}
