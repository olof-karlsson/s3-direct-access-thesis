package com.example.client.uploadServices.MultipartUploadPresign;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.example.client.utils.TimeUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GetMultipartUrlResource {
  private static int partNumberInUploadPartProcess = 0; // Used in uploadPart for debugging or simulating an abort

  /**
   * Fetches a presigned PUT URL from the server for a specific part of a multipart upload.
   *
   * The server generates a temporary URL that allows the client to upload directly to S3
   * without handling the file contents itself.
   *
   * @param client      - the HTTP client used to make the request
   * @param fileName    - the name of the file being uploaded
   * @param uploadId    - the multipart upload session ID
   * @param partNumber  - the index of the part being uploaded
   * @return a presigned URL as a String, which can be used to PUT this specific part to S3
   * @throws IOException if the HTTP request fails
   * @throws InterruptedException if the thread is interrupted during the request
   */
  public static String getPresignedPartUrl(HttpClient client, String fileName, String uploadId, int partNumber) throws IOException, InterruptedException {
    // Construct the request URL to fetch the presigned PUT URL
    String url = String.format("http://localhost:3000/files/multipart-presign/%s/%s/%d", fileName, uploadId, partNumber);

    // Create an HTTP GET request with timeout
    HttpRequest request = HttpRequest.newBuilder() //
      .uri(URI.create(url)) //
      .timeout(Duration.ofSeconds(10)) //
      .GET().build();

    // Send the request and parse the JSON response to extract the URL
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    return new ObjectMapper().readTree(response.body()).get("url").asText();
  }

  /**
   * Uploads a specific part of the file to the given presigned URL.
   *
   * This method:
   * - Seeks to the specified position in the file
   * - Reads the required number of bytes
   * - Sends the data using a PUT request to the presigned URL
   * - Optionally simulates a network failure for testing
   * - Returns the ETag (part identifier) from the response headers
   *
   * @param file                - RandomAccessFile instance pointing to the source file
   * @param fileName            - The original file name (used only for logging)
   * @param position            - The byte offset in the file to start reading
   * @param length              - The number of bytes to read and upload
   * @param presignedUrl        - The URL to which the part should be uploaded
   * @param shouldSimulateAbort - Flag used to simulate an abort for testing (e.g. during part 2)
   * @param totalParts -  Used for debugging to display the total number of parts)
   * @return the ETag header from the upload response
   * @throws IOException if reading or uploading fails
   * @throws InterruptedException if the thread is interrupted during HTTP transmission
   */
  public static String uploadPart(RandomAccessFile file, //
    String fileName, //
    long position, //
    long length, //
    String presignedUrl, //
    Boolean shouldSimulateAbort, //
    int totalParts) //
    throws IOException, InterruptedException {

    long start = System.currentTimeMillis();

    // Move file pointer to start of current part
    file.seek(position);

    // Read exact part into buffer
    byte[] buffer = new byte[(int)length];
    file.readFully(buffer);

    // Increment part counter (used for logging and simulated error)
    partNumberInUploadPartProcess += 1;
    System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [FileUpload] Uploading part " + partNumberInUploadPartProcess + " of file: " + fileName);

    // Simulate an upload failure if configured (used for testing abort logic)
    if (partNumberInUploadPartProcess == 2 && true == shouldSimulateAbort) {
      throw new IOException("simulated network error");
    }

    // Create and send HTTP PUT request with file part
    HttpRequest request = HttpRequest.newBuilder() //
      .uri(URI.create(presignedUrl)) //
      .PUT(HttpRequest.BodyPublishers.ofByteArray(buffer)) //
      .build();

    HttpResponse<String> resp = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    long duration = System.currentTimeMillis() - start;
    System.out.printf("   Uploaded part %d of %d → Status: %d%n     Duration: %d ms%n", partNumberInUploadPartProcess, totalParts, resp.statusCode(), duration);

    // Log ETag (if available)
    resp.headers() //
      .firstValue("ETag") //
      .ifPresent(tag -> System.out.printf("     ETag: %s%n", tag));

    // Throw if upload failed
    if (resp.statusCode() != 200) {
      throw new IOException("Part upload failed: " + resp.statusCode() + " / " + resp.body());
    }

    // Return ETag, or fail if not found (ETag is required to complete upload)
    return resp.headers().firstValue("ETag") //
      .orElseThrow(() -> new IOException("No ETag on part-response"));
  }

}
