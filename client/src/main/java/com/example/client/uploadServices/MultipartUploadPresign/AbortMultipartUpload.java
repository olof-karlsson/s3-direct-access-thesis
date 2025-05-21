package com.example.client.uploadServices.MultipartUploadPresign;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.example.client.utils.TimeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AbortMultipartUpload {
//------------------------------------------

  /**
  * Aborts the multipart upload by first retrieving a presigned abort URL and then sending a DELETE request.
  *
  * @param client   - HttpClient to make requests
  * @param fileName - Name of the file (S3 key)
  * @param uploadId - ID of the multipart upload to abort
  */
  static void abortMultipartUpload(HttpClient client, String fileName, String uploadId) {
    try {
      // 1. Request the presigned abort URL from the backend
      String abortUrl = String.format("http://localhost:3000/files/multipart-abort-presign/%s/%s", fileName, uploadId);
      HttpRequest request = HttpRequest.newBuilder() //
        .uri(URI.create(abortUrl)) //
        .timeout(Duration.ofSeconds(10)) //
        .GET() //
        .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode responseJson = new ObjectMapper().readTree(response.body());
      String presignedUrl = responseJson.get("url").asText();

      // 2. Use the presigned URL to send a DELETE request to S3
      HttpRequest deleteRequest = HttpRequest.newBuilder() //
        .uri(URI.create(presignedUrl)) //
        .timeout(Duration.ofSeconds(10)) //
        .DELETE() //
        .build();

      HttpResponse<String> deleteResponse = HttpClient.newHttpClient() //
        .send(deleteRequest, HttpResponse.BodyHandlers.ofString());

      if (deleteResponse.statusCode() >= 200 && deleteResponse.statusCode() < 300) {
        System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [FileUpload] Upload aborted successfully.");

      } else {
        System.err.println("Failed to abort upload: " + deleteResponse.statusCode() + " " + deleteResponse.body());
      }
    } catch (Exception e) {
      System.err.println("Error aborting upload: " + e.getMessage());
    }
  }
}
