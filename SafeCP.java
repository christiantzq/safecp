import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SafeCP {
    static int existentFiles = 0;
    static int copiedFiles = 0;
    static int failedFiles = 0;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Please try again using: java SafeCP <sourcePath> <destinationPath>");
            return;
        }

        String sourcePath = args[0];
        String destinationPath = args[1];

        try {
            copyFilesWithRetry(sourcePath, destinationPath);
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        StringBuilder output = new StringBuilder();
        output.append("\n\n==================================================================");        
        output.append("\nAlready existent files: " + existentFiles);
        output.append("\nSuccessfully copied files: " + copiedFiles);
        output.append("\nFailed to copy files: " + failedFiles + "\n\n");
        System.out.println(output.toString());

        try {
            Files.write(Paths.get("Log.txt"),
                                (output.toString()).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("Cannot write in log.");
            e.printStackTrace();
        }           
    }

    private static void copyFilesWithRetry(String sourcePath, String destinationPath)
            throws IOException, NoSuchAlgorithmException {
        int retryAttempts = 2;
        

        Files.walkFileTree(Paths.get(sourcePath), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException {
                Path relativePath = Paths.get(sourcePath).relativize(sourceFile);
                Path destinationFile = Paths.get(destinationPath, relativePath.toString());

                // Check if destination file already exist
                if(Files.exists(destinationFile) && hashMatches(sourceFile, destinationFile)){
                    System.out.println("Already exist: [" + destinationFile.toAbsolutePath().toString() + "]");
                    existentFiles++;
                    // Skip file
                    return FileVisitResult.CONTINUE;
                }

                // Else copy it...
                int retryCount = 0;
                boolean isCopied = false;
                do {
                    // Copy file (and directories)
                    Files.createDirectories(destinationFile.getParent());
                    Files.copy(sourceFile, destinationFile, StandardCopyOption.REPLACE_EXISTING);

                    // Verify file by comparing hashes                    

                    if (hashMatches(sourceFile, destinationFile)) {
                        System.out
                                .println("Copied: [" + destinationFile.toAbsolutePath().toString() + "]");
                        retryCount = retryAttempts; // Break the loop if successful
                        isCopied = true;
                        copiedFiles++;
                    } else {
                        System.out.println("Hash mismatch. Retrying: [" + sourceFile.toAbsolutePath().toString() + "]");
                        retryCount++;
                    }
                } while (retryCount < retryAttempts);

                // Write to a log file if copy still fails after retry attempts
                if (!isCopied) {
                    failedFiles++;
                    Files.write(Paths.get("FailedCopyLog.txt"),
                            ("Failed: [" + sourceFile.toString() + "]" + System.lineSeparator()).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);           
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean hashMatches(Path sourceFile, Path destinationFile) throws IOException {
        try {
            String sourceHash;
            sourceHash = getFileHash(sourceFile.toAbsolutePath().toString());
            String destinationHash = getFileHash(destinationFile.toAbsolutePath().toString());        
            if (sourceHash.equals(destinationHash)) 
                return true;
        } catch (NoSuchAlgorithmException e) {            
            e.printStackTrace();
            System.exit(1);
        }
        return false;
    }

    private static String getFileHash(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        FileInputStream fis = new FileInputStream(filePath);
        byte[] dataBytes = new byte[1024];
        int bytesRead;
        while ((bytesRead = fis.read(dataBytes)) != -1) {
            md.update(dataBytes, 0, bytesRead);
        }
        byte[] hashBytes = md.digest();
        // Convert the byte array to a hexadecimal string
        StringBuilder hexStringBuilder = new StringBuilder();
        for (byte hashByte : hashBytes) {
            hexStringBuilder.append(String.format("%02x", hashByte));
        }
        fis.close();
        return hexStringBuilder.toString();
    }
}
