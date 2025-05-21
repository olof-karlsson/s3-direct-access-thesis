package com.example.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testng.Assert.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.example.client.downloadService.FileDownloadUtils;
import com.example.client.uploadServices.FileUploadUtils;
import com.example.client.uploadServices.MultipartUploadPresign.PresignedUploadSelector;

public class TestFileRestService {
  private static final Logger logger = LoggerFactory.getLogger(TestFileRestService.class);
  private static final String TEST_FILE_NAME = "file";
  private static final String SAVE_PATH = "testfiles/testfile.txt";
  private static final int FILECOUNT = 100;
  private static final String clientId = "client1";
  HttpClient client = HttpClient.newHttpClient();
  Boolean showProgress = false;
  //Methods
  //presign
  //accesspoints
  //streamS3ObjectViaServer
  String currentMethod = "accesspoints";

  @Test
  public void testUpload() throws Exception {
    testMethod(currentMethod);
    logger.info("START TEST UPLOAD FILES USING: " + currentMethod);

    long startTime = System.currentTimeMillis();

    for(int i = 1; i <= FILECOUNT; i++) {
      String filename = TEST_FILE_NAME + i;
      Path uploadPath = Paths.get(System.getProperty("user.home"), "Downloads", "TESTFILESUPLOAD", filename);

      try {
        FileUploadUtils.uploadFile(client, filename, uploadPath.toString(), clientId);
      } catch (Exception e) {
        logger.error("Failed to upload file: " + filename, e);
      }
    }

    long endTime = System.currentTimeMillis();
    logger.info("END TEST UPLOAD FILES USING: " + currentMethod);
    logger.info("TEST UPLOAD FILES DURATION: " + (endTime - startTime) + " ms");

  }

  @Test
  public void testPresignMultipartUpload() throws Exception {
    testMethod(currentMethod);
    logger.info("START TEST UPLOAD FILES USING: " + currentMethod);

    HttpClient client = HttpClient.newHttpClient();
    long startTime = System.currentTimeMillis();

    for(int i = 1; i <= FILECOUNT; i++) {
      String filename = TEST_FILE_NAME + i;
      Path uploadPath = Paths.get(System.getProperty("user.home"), "Downloads", "TESTFILESUPLOAD", filename);

      try {
        PresignedUploadSelector.selectAndUploadPresigned(client, filename, uploadPath.toString(), false);

      } catch (Exception e) {
        logger.error("Failed to upload file: " + filename, e);
      }
    }

    long endTime = System.currentTimeMillis();
    logger.info("END TEST UPLOAD FILES USING: " + currentMethod);
    logger.info("TEST UPLOAD FILES DURATION: " + (endTime - startTime) + " ms");
  }

  @Test
  public void testDownload() throws Exception {
    testMethod(currentMethod);
    logger.info("START TEST DOWNLOAD FILES USING: " + currentMethod);
    long startTime = System.currentTimeMillis();

    // Define the download directory
    Path downloadDir = Paths.get(System.getProperty("user.home"), "Downloads", "TESTFILESDOWNLOAD");

    // Create directory if it doesn't exist
    if (!Files.exists(downloadDir)) {
      Files.createDirectories(downloadDir);
    }

    for(int i = 1; i < FILECOUNT + 1; i++) {
      Path downloadPath = downloadDir.resolve(TEST_FILE_NAME + i);
      FileDownloadUtils.downloadFile(client, TEST_FILE_NAME + i, downloadPath.toString(), clientId);
    }
    long endTime = System.currentTimeMillis();
    logger.info("END TEST DOWNLOAD FILES USING: " + currentMethod);
    logger.info("TEST UPLOAD FILES DURATION: " + (endTime - startTime) + " ms");
  }

//  @Test
//  public void testDownloadFail() throws Exception {
//    String fileName = TEST_FILE_NAME + "fail";
//    String serverUrl = "http://localhost:3000/files/download/" + fileName;
//
//    HttpClient client = HttpClient.newHttpClient();
//
//    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(serverUrl)).GET().build();
//
//    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//    System.out.println(response);
//
//    assertEquals(404, response.statusCode(), "Server should return HTTP 404 Error");
//  }

  @Test
  public void testRequestMethod() throws Exception {
    String serverUrl = "http://localhost:3000/files/request-method";

    HttpClient client = HttpClient.newHttpClient();

    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(serverUrl)).GET().build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode(), "Server should return HTTP 200 OK");

  }

  @Test
  public void testHealth() throws Exception {
    String serverUrl = "http://localhost:3000/files/health";

    HttpClient client = HttpClient.newHttpClient();

    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(serverUrl)).GET().build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode(), "Server should return HTTP 200 OK");

    String body = response.body();
    assertTrue(body.contains("Server is healthy"), "Response body should contain expected text");

  }

  @Test
  public void testMethod(String method) throws Exception {
    String serverUrl = "http://localhost:3000/files/method/" + method;

    HttpClient client = HttpClient.newHttpClient();

    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(serverUrl)).POST(HttpRequest.BodyPublishers.noBody()).build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    System.out.println(response);

    assertEquals(200, response.statusCode(), "Server should return HTTP 200 OK");

    String body = response.body();
    assertTrue(body.contains("Changed to method: " + method), "Response body should contain expected text");

  }

}
