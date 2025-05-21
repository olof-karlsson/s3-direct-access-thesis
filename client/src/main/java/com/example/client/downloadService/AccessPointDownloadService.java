package com.example.client.downloadService;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.client.utils.TimeUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

/**
 * Class to download with accesspoint.
 */
public class AccessPointDownloadService {

  private static final Logger logger = LoggerFactory.getLogger(AccessPointDownloadService.class);
  private static final Region REGION = Region.EU_NORTH_1;
  private static final String BASE_URL = "http://localhost:3000/files/download-accesspoint/";
  private static final ObjectMapper mapper = new ObjectMapper();
  private static String accesspointArn = null;
  private static String accessKeyId = null;
  private static String secretAccessKey = null;
  private static String sessionToken = null;
  private static DownloadResponse downloadResponse;

  public static HttpResponse<InputStream> accessPointDownload(HttpClient client, HttpResponse<InputStream> response, String fileName, String targetPath, String clientId) throws IOException {
    Path path = Paths.get(targetPath);

    // Checks if there is cached valid credentials, if yes, use it directly to perform the download
    if (accesspointArn != null && accessKeyId != null && secretAccessKey != null && sessionToken != null) {
      downloadFromS3(accesspointArn, accessKeyId, secretAccessKey, sessionToken, fileName, path, clientId).join();
      return response;
    }

    // Constructs a HTTP request
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(BASE_URL + fileName + "/" + clientId)).GET().build();

    // Requesting credentials from the server.
    CompletableFuture<Void> downloadChain = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenCompose(asyncResponse -> {

      if (asyncResponse.statusCode() != 200) {
        logger.error("Non-success response: {}", asyncResponse.body());
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Server returned status " + asyncResponse.statusCode()));
        return failed;
      }

      try {
        downloadResponse = mapper.readValue(asyncResponse.body(), DownloadResponse.class);
      } catch (IOException e) {
        logger.error("Failed to parse JSON response", e);
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(e);
        return failed;
      }

      // Extracts all the important details to construct the credentials.
      accesspointArn = downloadResponse.accesspointArn;
      accessKeyId = downloadResponse.accessKeyId;
      secretAccessKey = downloadResponse.secretAccessKey;
      sessionToken = downloadResponse.sessionToken;
      return

      downloadFromS3(accesspointArn, accessKeyId, secretAccessKey, sessionToken, fileName, path, clientId)//
        .thenAccept(r -> logger.info("[" + TimeUtils.getCurrentTimestamp() + "] [FileDownload]   S3 download completed successfully")).exceptionally(e -> {
          logger.error("S3 download failed", e);
          return null;
        });
    });

    downloadChain.join();
    return response;

  }

  private static CompletableFuture<Void> downloadFromS3(String accesspointAlias, String accessKeyId, String secretAccessKey, String sessionToken, String fileName, Path filePath, String clientId) {

    // Constructing a getObjectRequest to AWS S3
    GetObjectRequest getReq = GetObjectRequest.builder().bucket(accesspointAlias).key(clientId + "/" + fileName).build();

    // Constructing the credentials associated with the objectrequest
    AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
    StaticCredentialsProvider sessionCredentialsProvider = StaticCredentialsProvider.create(sessionCredentials);
    S3AsyncClient s3Client = S3AsyncClient.builder() //
      .region(REGION) //
      .credentialsProvider(sessionCredentialsProvider) //
      .multipartEnabled(true) //
      .multipartConfiguration(b -> b //
        .thresholdInBytes(8 * 1024 * 1024L)//
        .minimumPartSizeInBytes(8 * 1024 * 1024L))//
      .serviceConfiguration(S3Configuration.builder().useArnRegionEnabled(true) //
        .build()) //
      .build();

    return s3Client.getObject(getReq, AsyncResponseTransformer.toFile(filePath)) //
      .thenAccept(response -> System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [FileDownload]   S3 GetObject completed")).thenApply(resp -> null);
  }

  /**
   * DTO for server's download response.
   * Expected format: { "bucketName": "...", "accesspointAlias": "..." }
   */
  private static class DownloadResponse {
    public String accesspointArn;
    public String accessKeyId;
    public String secretAccessKey;
    public String sessionToken;
  }
}
