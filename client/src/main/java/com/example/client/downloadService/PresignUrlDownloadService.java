package com.example.client.downloadService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.client.utils.TimeUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service to handle file download using a presigned S3 URL.
 */
public class PresignUrlDownloadService {

  private static final Logger logger = LoggerFactory.getLogger(PresignUrlDownloadService.class);
  private static final int BUFFER_SIZE = 4096;

  /**
   * Downloads a file using a presigned URL extracted from a JSON response and saves it locally.
   *
   * @param client          the HTTP client to use
   * @param initialResponse the initial response containing the presigned URL in JSON
   * @param savePath        local path to save the downloaded file
   * @throws IOException          if an I/O error occurs
   * @throws InterruptedException if the HTTP request is interrupted
   */
  public static void presignedUrlDownload(HttpClient client, HttpResponse<InputStream> initialResponse, String savePath) throws IOException, InterruptedException {

    final ObjectMapper mapper = new ObjectMapper();
    final String json = new String(initialResponse.body().readAllBytes());

    // Extract the presigned URL from the JSON response
    final String presignedUrl = mapper.readTree(json).get("url").asText();

    final HttpRequest presignedRequest = HttpRequest.newBuilder().uri(URI.create(presignedUrl)).GET().build();

    final HttpResponse<InputStream> presignedResponse = client.send(presignedRequest, HttpResponse.BodyHandlers.ofInputStream());

    if (presignedResponse.statusCode() == 200) {
      downloadToFile(presignedResponse.body(), Paths.get(savePath));
      logger.info("[{}] [FileDownload] Download complete | Saved to: {}", TimeUtils.getCurrentTimestamp(), savePath);
    } else {
      logger.error("Download failed. Status: {}", presignedResponse.statusCode());
    }
  }

  /**
   * Writes data from an InputStream to a specified file.
   *
   * @param inputStream the InputStream containing file data
   * @param savePath    the local file path to write to
   * @throws IOException if an I/O error occurs
   */
  private static void downloadToFile(InputStream inputStream, Path savePath) throws IOException {
    Path parentDir = savePath.getParent();
    if (parentDir != null) {
      Files.createDirectories(parentDir);
    }

    try (OutputStream outputStream = Files.newOutputStream(savePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); InputStream in = inputStream) {

      byte[] buffer = new byte[BUFFER_SIZE];
      int bytesRead;

      while ((bytesRead = in.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
      }
    }
  }
}
