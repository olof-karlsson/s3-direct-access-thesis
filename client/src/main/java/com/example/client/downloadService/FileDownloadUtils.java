package com.example.client.downloadService;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.client.ServerUtils;

public class FileDownloadUtils {
  private static final Logger logger = LoggerFactory.getLogger(FileDownloadUtils.class);
  private static final int PROGRESS_BAR_WIDTH = 50;
  private static final int BUFFER_SIZE = 4096; // Size of the buffer used during stream reading
  private static final boolean showProgress = false;

  /**
   * Initiates a file download using the provided HTTP client.
   *
   * @param client       the HttpClient instance to use
   * @param fileName     the name of the file to download
   * @param savePath     the path where the file will be saved
   * @param clientId     the prefix of the folder in the bucket
   * @param showProgress whether to display download progress
   */
  public static void downloadFile(HttpClient client, String fileName, String savePath, String clientId) {
    // Build the HTTP GET request for downloading the file
    String method = ServerUtils.getCurrentMethod(client);
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:3000/files/download/" + fileName)).build();

    try {
      //Checks and applies different methods for download

      if ("accesspoints".equals(method)) {

        HttpResponse<InputStream> response = null;
        response = AccessPointDownloadService.accessPointDownload(client, response, fileName, savePath, clientId);

      } else {

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        // Send the request and expect an InputStream as a response body - Necessary for other methods?

        if (response.statusCode() == 200) {

          if ("presign".equals(method)) {
            PresignUrlDownloadService.presignedUrlDownload(client, response, savePath);

          } else if ("streamS3ObjectViaServer".equals(method)) {
            StreamServerDownloadService.streamServerDownload(response, savePath);

          }
        } else {
          logger.error("Download failed. Status: {}", response.statusCode());
        }
      }
    } catch (Exception e) {
      logger.error("Error downloading file", e);
    }
  }
}
