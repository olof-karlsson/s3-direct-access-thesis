package com.example.client.uploadServices;

import java.io.IOException;
import java.net.http.HttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.client.ServerUtils;
import com.example.client.utils.TimeUtils;

public class FileUploadUtils {
  private static final Logger logger = LoggerFactory.getLogger(FileUploadUtils.class);

  /**
   * Uploads a file to the server using HTTP POST request with a byte stream.
   *
   * @param client   the HttpClient instance used to send the request
   * @param fileName the name of the file to be uploaded
   * @param filePath the full path to the file on the local system
   * @param clientId for the folder prefix in the bucket
   */
  public static void uploadFile(HttpClient client, String fileName, String filePath, String clientId) {
    logger.info("Application starts");
    String method = ServerUtils.getCurrentMethod(client);

    logger.info("[" + TimeUtils.getCurrentTimestamp() + "] [FileUpload]   Upload initiated | File: " + fileName);

    try {
      //Checks and applies different methods for upload

      if ("presign".equals(method)) {

        // Upload file via presigned URL
        PresignedUrlUploadService.presignedUrlUpload(client, fileName, filePath);

        // Upload via Server
      } else if ("streamS3ObjectViaServer".equals(method)) {

        StreamServerUploadService.streamServerUpload(client, fileName, filePath);

        // Upload via Access Points
      } else if ("accesspoints".equals(method)) {

        AccessPointUploadService.accessPointUpload(client, fileName, filePath, clientId);
      }
    } catch (IOException | InterruptedException e) {
      // Handle IO and interruption exceptions, and restore the interrupt status
      logger.info("[" + TimeUtils.getCurrentTimestamp() + "] Upload failed" + e);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      logger.info("[" + TimeUtils.getCurrentTimestamp() + "] Unexpected error during file upload" + e);
    }
  }
}
