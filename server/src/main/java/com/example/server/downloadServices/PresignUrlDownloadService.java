package com.example.server.downloadServices;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.server.Main;
import com.example.server.TimeUtils;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

public class PresignUrlDownloadService {
  private static final Logger logger = LoggerFactory.getLogger(PresignUrlDownloadService.class);
  private static final S3Presigner s3Presigner = Main.getS3Presigner(); // AWS S3 presigner used to generate presigned URLs

  // Dedicated thread pool for running blocking S3 operations asynchronously
  private static final ExecutorService executorService = Executors.newFixedThreadPool(3); // Adjust pool size as needed

  /**
   * Handles an incoming request to generate a presigned URL asynchronously.
   * @param fileName the key (object name) of the file in the S3 bucket
   * @return a CompletableFuture that resolves to a JAX-RS Response containing the presigned URL
   */
  public static CompletableFuture<Response> handlePresignedMethod(String fileName) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        String bucketName = Main.getBucketName();
        logger.info(String.format("Running in thread: %s", Thread.currentThread().getName()));
        System.out.println(String.format("[%s] [PresignedURL]  Generating presigned download URL | Bucket: %s | Key: %s", TimeUtils.getCurrentTimestamp(), bucketName, fileName));

        // Generate the presigned URL for downloading a file from the specified bucket and key
        String presignedUrl = generatePresignedGetURLForDirectoryBucket(bucketName, fileName);

        System.out.println(presignedUrl);
        System.out.println(String.format("[%s] [PresignedURL]  Completed generation ✔", TimeUtils.getCurrentTimestamp()));

        // Return the presigned URL in a JSON response format with an HTTP 200 status (OK)
        return Response.ok("{\"url\": \"" + presignedUrl + "\"}", MediaType.APPLICATION_JSON).build();

      } catch (Exception e) {
        // Handle any exceptions and return a meaningful error response
        return handleException(e);
      }
    }, executorService);
  }

  /**
   * Generates a presigned GET URL for downloading a specific object from an S3 bucket.
   * @param bucketName the name of the S3 bucket
   * @param objectKey the key (object name) of the file
   * @return a presigned URL string
   */
  private static String generatePresignedGetURLForDirectoryBucket(String bucketName, String objectKey) {
    try {
      // Create a GetObjectRequest
      GetObjectRequest getObjectRequest = GetObjectRequest.builder() //
        .bucket(bucketName) //
        .key(objectKey) //
        .build();

      // Create the presign request with a signature valid for 10 minutes
      GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder() //
        .signatureDuration(Duration.ofMinutes(10)) //
        .getObjectRequest(getObjectRequest).build();

      // Generate the presigned URL
      PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
      return presignedRequest.url().toString();

    } catch (S3Exception e) {
      logger.error("Failed to generate presigned URL: {} - Error code: {}", e.awsErrorDetails() //
        .errorMessage(), e.awsErrorDetails() //
          .errorCode(), e);
      throw e;
    }
  }

  /**
   * Converts exceptions into an appropriate HTTP response.
   *
   * @param cause The exception that occurred.
   * @return A Response with the relevant status and error message.
   */
  private static Response handleException(Throwable cause) {
    if (cause instanceof S3Exception) {
      S3Exception s3Ex = (S3Exception)cause;
      return Response.status(s3Ex.statusCode()).entity(s3Ex.getMessage()).build();
    }
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error retrieving file: " + cause.getMessage()).build();
  }

  // Currently unused, but may be useful for production
  // Handles cleanup during application shutdown
  //  public static void shutdown() {
  //      s3Presigner.close();
  //      executorService.shutdown();
  //  }
}
