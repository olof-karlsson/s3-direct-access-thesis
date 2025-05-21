package com.example.client.uploadServices.MultipartUploadPresign;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class InitiateMultipartUpload {
  /**
   * Initiates the multipart upload on the server, then POSTS the signed payload
   * to S3 to get back an uploadId.
   *
   * @return the S3 uploadId
   */
  static String initiateMultipartUpload(HttpClient client, String fileName) throws IOException, InterruptedException {
    // Step 1: Ask the local server to initiate the upload and return a presigned S3 URL and payload
    HttpResponse<String> response = client.send(HttpRequest.newBuilder() //
      .uri(URI.create("http://localhost:3000/files/multipart-initiate/" + fileName)) //
      .GET() //
      .build(), HttpResponse.BodyHandlers.ofString());

    // Parse the JSON response to extract the presigned S3 URL and the payload
    JsonNode responseJson = new ObjectMapper().readTree(response.body());
    String presignedUrl = responseJson.get("url").asText();
    //String payload = responseJson.get("payload").asText();

    // Step 2: Use the presigned URL and payload to start the multipart upload on S3
    HttpResponse<String> initiateResponse = HttpClient.newHttpClient() //
      .send(HttpRequest.newBuilder() //
        .uri(URI.create(presignedUrl)) //
        .timeout(Duration.ofSeconds(10)) //
        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());

    // Extract and return the uploadId from the S3 XML response
    return parseUploadIdFromXml(initiateResponse.body());
  }

  /**
   * Fetches a presigned PUT URL for the given part number.
   */
  private static String parseUploadIdFromXml(String xml) {
    int start = xml.indexOf("<UploadId>") + "<UploadId>".length();
    int end = xml.indexOf("</UploadId>", start);
    return xml.substring(start, end);
  }
}
