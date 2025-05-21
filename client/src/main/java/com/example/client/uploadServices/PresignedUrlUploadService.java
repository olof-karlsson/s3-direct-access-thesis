package com.example.client.uploadServices;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This class handles uploading files to S3 using a presigned URL.
 */

public class PresignedUrlUploadService {
  private static final Logger logger = LoggerFactory.getLogger(PresignedUrlUploadService.class);

  /**
   * Sends a GET request to the server to obtain a presigned URL for uploading a file.
   *
   * @param client   The HTTP client used to make the request
   * @param fileName The name of the file to upload
   * @return A presigned URL string to upload the file
   * @throws IOException              If there is an error in the request or response
   * @throws InterruptedException     If the request is interrupted
   */
  public static String getPresignedUrlFromServer(HttpClient client, String fileName) throws IOException, InterruptedException {
    String url = "http://localhost:3000/files/upload-presign/" + fileName;

    HttpRequest request = HttpRequest.newBuilder() //
      .uri(URI.create(url)) //
      .GET() //
      .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 200) {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.readTree(response.body()).get("url").asText();
    } else {
      throw new IOException("Failed to get presigned URL: " + response.body());
    }
  }

  /**
   * Uploads a file to the given presigned URL by sending a PUT request.
   *
   * @param client   The HTTP client used to send the upload request
   * @param fileName The name of the file being uploaded
   * @param filePath The local file path of the file to be uploaded
   * @throws IOException              If an I/O error occurs
   * @throws InterruptedException     If the operation is interrupted
   */
  public static void presignedUrlUpload(HttpClient client, String fileName, String filePath) throws IOException, InterruptedException {
    Path path = Paths.get(filePath);

    String presignedUrl = PresignedUrlUploadService.getPresignedUrlFromServer(client, fileName);

    HttpRequest request = HttpRequest.newBuilder() //
      .uri(URI.create(presignedUrl)) //
      .header("Content-Type", "application/octet-stream") //Not necessary but good coding standard
      .PUT(HttpRequest.BodyPublishers.ofFile(path)) //
      .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      logger.error("Upload failed. Status: {} | Response: {}", response.statusCode(), response.body());
      throw new IOException("Upload failed with status: " + response.statusCode());
    }
  }
}
