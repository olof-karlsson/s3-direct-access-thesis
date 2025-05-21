package com.example.client.uploadServices;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.client.utils.TimeUtils;

/**
 * This class handles uploading files to S3 using a stream from server.
 */
public class StreamServerUploadService {

  private static final Logger logger = LoggerFactory.getLogger(StreamServerUploadService.class);

  public static void streamServerUpload(HttpClient client, String fileName, String filePath) throws IOException {
    Path path = Paths.get(filePath);
    long contentLength = Files.size(path);
    logger.info("Preparing to upload file: {} ({} bytes)", fileName, contentLength);

    HttpRequest request = HttpRequest.newBuilder() //
      .uri(URI.create("http://localhost:3000/files/upload-s3stream/" + fileName)) //
      .header("Content-Type", "application/octet-stream") //
      .POST(BodyPublishers.ofFile(path)) //
      .build();

    // capture the future so we can wait on it
    CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
      int statusCode = response.statusCode();
      if (statusCode == 200 || statusCode == 201) {
        logger.info("[{}] Upload successful | Server response: {}", TimeUtils.getCurrentTimestamp(), response.body());
      } else {
        logger.error("Upload failed. Status: {} | Response: {}", statusCode, response.body());
      }
    }).exceptionally(ex -> {
      logger.error("Async upload failed with exception: {}", ex.getMessage(), ex);
      return null;
    });
    future.join();
  }
}
