package com.example.client.uploadServices.MultipartUploadPresign;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CompleteMultipartUploadUrl {

  /**
   * Finalizes the multipart upload by completing the upload process.
   *
   * This function retrieves a presigned "complete" request from the server, which is then POSTed to S3
   * to finalize the multipart upload. The uploaded parts are combined into a single object in S3.
   *
   * @param client   {HttpClient} - The HTTP client used to make requests.
   * @param fileName {string}     - The name of the file being uploaded.
   * @param uploadId {string}     - The upload ID previously returned by S3.
   * @param parts    {Array}      - A list of uploaded parts and their corresponding ETags.
   *
   * @return {void} - This function does not return a value but performs the final upload step.
   */
  public static void completeMultipartUpload(HttpClient client, //
    String fileName, //
    String uploadId, //
    List<MultipartUploadDTO.CompletedPartDTO> parts) //
    throws IOException, InterruptedException {

    // Step 1: Ask the local server to return a presigned S3 "complete" URL and XML payload
    String url = String.format("http://localhost:3000/files/multipart-complete-presign/%s/%s", fileName, uploadId);
    String jsonBody = new ObjectMapper().writeValueAsString(parts); // Convert list of parts to JSON will use DTO

    HttpResponse<String> response = client.send(HttpRequest.newBuilder() //
      .uri(URI.create(url)) //
      .header("Content-Type", "application/json") //
      .POST(HttpRequest.BodyPublishers.ofString(jsonBody)) //
      .timeout(Duration.ofSeconds(10)) //
      .build(), HttpResponse.BodyHandlers.ofString());

    // Step 2: Parse the server response to extract presigned URL and payload
    JsonNode presignResponse = new ObjectMapper().readTree(response.body());
    String presignedUrl = presignResponse.get("url").asText(); // S3 endpoint to finalize the upload
    String payload = presignResponse.get("payload").asText(); // XML payload describing parts

    System.out.println("PresignedURL");
    System.out.println(presignedUrl);
    System.out.println("Payload");
    System.out.println(payload);

    // Step 3: Send the complete-multipart-upload request directly to S3
    HttpRequest completeRequest = HttpRequest.newBuilder() //
      .uri(URI.create(presignedUrl)) //
      .header("Content-Type", "application/xml") //
      .timeout(Duration.ofSeconds(10)) //
      //.POST(HttpRequest.BodyPublishers.noBody()) //
      .POST(HttpRequest.BodyPublishers.ofString(payload)).build();

    HttpResponse<String> completeResponse = HttpClient.newHttpClient().send(completeRequest, HttpResponse.BodyHandlers.ofString());

    //System.out.println("Response body " + completeResponse.statusCode() + " / " + completeResponse.body());

    // Step 4: Check S3's response to confirm the upload was completed
    if (completeResponse.statusCode() != 200) {
      throw new IOException("Upload completion failed: " + completeResponse.statusCode() + " / " + completeResponse.body());
    }
  }
}
