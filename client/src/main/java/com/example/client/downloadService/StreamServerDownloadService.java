package com.example.client.downloadService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.client.utils.TimeUtils;

public class StreamServerDownloadService {
  private static final Logger logger = LoggerFactory.getLogger(StreamServerDownloadService.class);
  private static final int BUFFER_SIZE = 4096;

  /**
   * Streams a file from S3 through the server to the client, enabling direct download.
   * The server retrieves the file from S3 without fully loading the file into memory
   * and streams it to the client while optionally showing download progress.
   *
   * @param response The HttpResponse containing the InputStream of the file to be streamed.
   * @param savePath The path where the file should be saved on the client's side.
   * @param showProgress A flag indicating whether to display download progress during the stream.
   * @throws IOException If an I/O error occurs during the download.
   */
  static void streamServerDownload(HttpResponse<InputStream> response, String savePath) throws IOException {

    downloadToFile(response.body(), Paths.get(savePath));
    logger.info("[" + TimeUtils.getCurrentTimestamp() + "] [FileDownload] Download complete  | Saved to: " + savePath);
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
