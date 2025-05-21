package com.example.server.uploadServices;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public class StreamServerUploadService {
  private static final Logger logger = LoggerFactory.getLogger(StreamServerUploadService.class);

  public static void asyncMultipartUpload(S3AsyncClient s3AsyncClient, String bucketName, String fileName, InputStream uploadedInputStream, long contentLength, AsyncResponse asyncResponse) {

    ExecutorService executor = Executors.newFixedThreadPool(10);

    PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(bucketName) //
      .key(fileName) //
      .build();

    InputStream buffered = new BufferedInputStream(uploadedInputStream);

    logger.info("Uploading file: {} (Size: {})", fileName, contentLength);

    CompletableFuture<PutObjectResponse> future = s3AsyncClient.putObject(putObjectRequest, AsyncRequestBody.fromInputStream(buffered, contentLength, executor));

    future.whenComplete((resp, err) -> {
      try {
        if (err != null) {
          logger.error("Upload failed", err);
          asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Upload failed: " + err.getMessage()).build());
        } else {
          logger.info("Upload successful: {}", resp);
          asyncResponse.resume(Response.ok("Upload successful").build());
        }
      } finally {
        executor.shutdown(); // Clean up the executor
      }
    });
  }
}
