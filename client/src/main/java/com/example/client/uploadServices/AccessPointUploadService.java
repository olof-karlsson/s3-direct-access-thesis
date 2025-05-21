package com.example.client.uploadServices;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
//

/**
 * This class handles uploading files to S3 using a access points.
 */
public class AccessPointUploadService {

  private static final Logger logger = LoggerFactory.getLogger(AccessPointUploadService.class);
  private static final Region REGION = Region.EU_NORTH_1;
  private static final String BASE_URL = "http://localhost:3000/files/upload-accesspoint/";
  private static final ObjectMapper mapper = new ObjectMapper();
  private static String accesspointArn = null;
  private static String accessKeyId = null;
  private static String secretAccessKey = null;
  private static String sessionToken = null;
  private static UploadResponse uploadResponse;

  /**
   * Main method to upload a file using an S3 Access Point.
   * 1. Requests access-point alias from server.
   * 2. Uploads file asynchronously to S3 using the alias.
   *
   * @param httpClient - the HTTP client used for the server request
   * @param fileName   - the name of the file
   * @param filePath   - the path to the local file
   */
  public static void accessPointUpload(HttpClient httpClient, String fileName, String filePath, String clientId) throws IOException {
    Path path = Paths.get(filePath);

    if (accesspointArn != null && accessKeyId != null && secretAccessKey != null && sessionToken != null) {
      uploadToS3(accesspointArn, accessKeyId, secretAccessKey, sessionToken, fileName, path, clientId).join();
      return;
    }

    // Step 1: Fetch the access-point alias from the server
    HttpRequest request = HttpRequest.newBuilder() //
      .uri(URI.create(BASE_URL + fileName + "/" + clientId)) //
      .GET() //
      .build();

    // Step 2: Send the GET request, process the server response, and chain it into the S3 PUT for the upload.

    CompletableFuture<Void> uploadChain = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()) //
      .thenCompose(asyncResponse -> {

        if (asyncResponse.statusCode() != 200) {

          logger.error("Non-success response: {}", asyncResponse.body());
          CompletableFuture<Void> failed = new CompletableFuture<>();
          failed.completeExceptionally(new RuntimeException("Server returned status " + asyncResponse.statusCode()));
          return failed;
        }

        // Deserialize the server response and parse the bucket/alias JSON data.

        try {
          uploadResponse = mapper.readValue(asyncResponse.body(), UploadResponse.class);

        } catch (IOException e) {
          logger.error("Failed to parse JSON response", e);
          CompletableFuture<Void> failed = new CompletableFuture<>();
          failed.completeExceptionally(e);
          return failed;
        }

        accesspointArn = uploadResponse.accesspointArn;
        accessKeyId = uploadResponse.accessKeyId;
        secretAccessKey = uploadResponse.secretAccessKey;
        sessionToken = uploadResponse.sessionToken;

        // Step 3: Return the S3 upload future, allowing thenCompose to wait on it while uploading the file using the alias.
        return uploadToS3(accesspointArn, accessKeyId, secretAccessKey, sessionToken, fileName, path, clientId) //
          .thenAccept(r -> { //
//            System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [FileUpload]   S3 upload completed successfully"); //
          }) //
          .exceptionally(e -> {
            logger.error("S3 upload failed", e);
            return null;
          });
      });

    // Block and wait for the completion of the entire chain, including both the GET request and S3 PUT upload.
    uploadChain.join();
//    System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [FileUpload]   Upload chain completed ✔");
  }

  /**
   * Initiates the asynchronous upload of a file to S3 using a provided access-point alias.
   *
   * @param accesspointArn - the access point arn
   * @param fileName         - the file name as S3 object key
   * @param filePath         - the path to the local file
   * @return CompletableFuture that completes when upload finishes
   */
  private static CompletableFuture<Void> uploadToS3(String accesspointArn, String accessKeyId, String secretAccessKey, String sessionToken, String fileName, Path filePath, String clientId) {

    PutObjectRequest putReq = PutObjectRequest.builder().bucket(accesspointArn).key(clientId + "/" + fileName).build();

    AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
    StaticCredentialsProvider sessionCredentialsProvider = StaticCredentialsProvider.create(sessionCredentials);
    S3AsyncClient s3Client = S3AsyncClient.builder() //
      .region(REGION) //
      .credentialsProvider(sessionCredentialsProvider) //
      .multipartEnabled(true) //
      .multipartConfiguration(b -> b //
        .thresholdInBytes(8 * 1024 * 1024L)//
        .minimumPartSizeInBytes(8 * 1024 * 1024L) //
      ).build();

    return s3Client.putObject(putReq, AsyncRequestBody.fromFile(filePath)) //
      .thenAccept(resp -> { //

      }) //
      .thenApply(resp -> null);
  }

  /**
   * Simple DTO representing the server's response.
   * Expected format: { "bucketName": "...", "accesspointAlias": "..." }
   */
  private static class UploadResponse {
    public String accesspointArn;
    public String accessKeyId;
    public String secretAccessKey;
    public String sessionToken;

  }

}
