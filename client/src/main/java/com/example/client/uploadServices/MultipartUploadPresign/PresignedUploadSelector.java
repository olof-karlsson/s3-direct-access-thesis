package com.example.client.uploadServices.MultipartUploadPresign;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.example.client.uploadServices.FileUploadUtils;

/**
 * Determines the upload strategy based on file size and delegates the task.
 *
 * @param client              - the HttpClient instance
 * @param fileName            - the name of the file to be uploaded
 * @param filePath            - the path to the file
 * @param shouldSimulateAbort - whether to simulate an abort during multipart upload
 * @throws IOException if file reading fails
 */
public class PresignedUploadSelector {
  private static final long MULTIPART_UPLOAD_THRESHOLD_BYTES = 5 * 1024 * 1024; // 5 MB

  public static void selectAndUploadPresigned(HttpClient client, String fileName, String filePath, Boolean shouldSimulateAbort) throws IOException {
    Path path = Paths.get(filePath);
    long fileSize = Files.size(path);

    if (fileSize > MULTIPART_UPLOAD_THRESHOLD_BYTES) {
      System.out.printf("[PresignedUploadSelector] Preparing to upload file '%s' (%d bytes)%n", fileName, fileSize);
      PresignedMultipartUploadService.uploadLargeFile(client, fileName, filePath, shouldSimulateAbort);

    } else {
      System.out.println("[PresignedUploadSelector] File under threshold — using single-part presigned upload.");
      FileUploadUtils.uploadFile(client, fileName, filePath, "clientId");
    }
  }
}
