package org.codetracker.blame.impl;

import org.codetracker.blame.IBlame;
import org.codetracker.blame.model.LineBlameResult;
import org.eclipse.jgit.lib.Repository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* Created by pourya on 2024-08-22*/
public class CliGitBlame implements IBlame {

    @Override
    public List<LineBlameResult> blameFile(Repository repository, String commitId, String filePath) throws Exception {
        List<LineBlameResult> blameResults = new ArrayList<>();
        Process process = null;

        try {
            // Construct the git blame command with the commit and file path
            String[] command = {"git", "blame", "-w", "--follow", commitId, "--", filePath};
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(repository.getDirectory());

            // Start the process and get the input stream
            process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                // Example output line (simplified for parsing):
                // CommitID (<Author> <Date>) <FilePath> <Code>
                // e.g., abc12345 (John Doe 2022-07-01 12:34:56 +0000) <old/file/path> line of code

                // Extract commitId, committer, commitDate, filePath, and beforeFilePath from the blame line
                String[] parts = line.split("\\s+", 3);
                String blameCommitId = parts[0].trim();  // Extract the commit ID
                String prevFilePath = parts[1].trim();

                String[] meta = parts[2].split("\\s{2,}"); // Split on at least 2 spaces
                String commiter = "";
                long commitTime = 0;

                // Regex to capture the timestamp
                Pattern pattern = Pattern.compile("(.*?)(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} [+-]\\d{4})");
                Matcher matcher = pattern.matcher(parts[2]);

                if (matcher.find()) {
                    // Extract the committer and timestamp
                    commiter = matcher.group(1).trim(); // Everything before the timestamp
                    commiter = commiter.substring(1); // Remove the parentheses
                    String timestamp = matcher.group(2).trim(); // Timestamp


                    // Define the formatter for the timestamp
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");

                    // Parse the timestamp string
                    OffsetDateTime offsetDateTime = OffsetDateTime.parse(timestamp, formatter);

                    // Convert to Instant
                    Instant instant = offsetDateTime.toInstant();
                    commitTime = instant.toEpochMilli() / 1000;
                }
                LineBlameResult result = new LineBlameResult(blameCommitId, filePath, prevFilePath, commiter, commitTime, lineNumber);
                blameResults.add(result);


                lineNumber++;
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new Exception("Error executing git blame command");
            }
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        return blameResults;
    }

    @Override
    public List<LineBlameResult> blameFile(Repository repository, String commitId, String filePath, int fromLine, int toLine) throws Exception {
        // Implement logic to blame a specific range of lines in a file
        // This method is optional and can be implemented based on the requirements
        throw new UnsupportedOperationException("Not implemented");
    }

    private String extractOriginalFilePath(String metadata) {
        // Implement logic to parse and extract the original file path if the file was renamed
        // This logic will vary based on how the metadata is structured in your git blame output
        return metadata.contains("<") && metadata.contains(">") ? metadata.substring(metadata.indexOf("<") + 1, metadata.indexOf(">")) : null;
    }

}
