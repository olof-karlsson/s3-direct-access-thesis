package com.example.client;

import java.net.http.HttpClient; // Need Java 11+
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.client.uploadServices.FileUploadUtils;

public class App {
  private static final Logger logger = LoggerFactory.getLogger(App.class);

  public static void main(String... args) {
    logger.info("Application starts");
    Boolean showProgress = true;

    // Important download and upload paramters
    String fileName = "FileName";
    String clientId = "client2";

    // Have to manually select to test a download/upload method
    // Methods
    //presign
    //accesspoints
    //streamS3ObjectViaServer
    String method = "accesspoints";
    //___________________________________________________________________________

    HttpClient client = HttpClient.newHttpClient();

    // Check if server is reachable
    if (!ServerUtils.isServerUp(client)) {
      logger.error("Server is unavailable. Exiting application.");
      return;
    }

    // 1.1 Change which method is currently used
    String changeMethod = ServerUtils.changeCurrentMethod(client, method);
    logger.info("Current method: {}", changeMethod);

    // 1.2 Check which method is currently used
    String currentMethod = ServerUtils.getCurrentMethod(client);
    logger.info("Current method: {}", currentMethod);

    // 2. Download a file from the server to the local "Downloads" folder
    logger.info("_____________DOWNLOAD START_____________");
    Path downloadPath = Paths.get(System.getProperty("user.home"), "Downloads", fileName);
    FileDownloadUtils.downloadFile(client, fileName, downloadPath.toString(), clientId);
    logger.info("_____________DOWNLOAD   END_____________");

    // 3. Upload a file to the server from the local "Donwloads" folder
    logger.info("_______________UPLOAD START_____________");
    Path uploadPath = Paths.get(System.getProperty("user.home"), "Downloads", fileName);
    FileUploadUtils.uploadFile(client, fileName, uploadPath.toString(), clientId);
    logger.info("_______________UPLOAD   END_____________");

    // Upload a large file to the server with presign
    Path uploadPath2 = Paths.get(System.getProperty("user.home"), "Downloads", fileName);
    //PresignedMultipartUploadService.uploadLargeFile(client, fileName, uploadPath2.toString(), false);
    PresignedMultipartUploadService.selectAndUploadPresigned(client, fileName3, uploadPath.toString(), false);

    logger.info("Application ends");

  }
}
