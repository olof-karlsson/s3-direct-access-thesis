package com.example.server.endpoints.MultipartUploadPresign;

import java.time.Duration;

import com.example.server.Main;
import com.example.server.TimeUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.AbortMultipartUploadPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedAbortMultipartUploadRequest;

@Path("/files")
public class AbortMultipartUploadResource {
  /**
   * Returns a presigned AbortMultipartUploadRequest.
   *
   * This allows clients to abort an S3 presign multipart upload
   *
   * @param fileName the object key associated with the multipart upload
   * @param uploadId the multipart upload ID to abort
   * @return HTTP 200 with JSON { "url": "..." } or 500 on error
   */
  @GET
  @Path("/multipart-abort-presign/{fileName}/{uploadId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response abortMultipartUploadPresign(@PathParam("fileName") String fileName, @PathParam("uploadId") String uploadId) {

    try {
      System.out.println(String.format("[%s] [PresignedURL] Generating abort presigned URL for Upload ID: %s", TimeUtils.getCurrentTimestamp(), uploadId));

      // Generate the presigned URL for aborting the multipart upload
      PresignedAbortMultipartUploadRequest presignedRequest = //
        generateAbortMultipartUploadPresignedUrl(Main.getRegion(), Main.getBucketName(), fileName, uploadId);

      String url = presignedRequest.url().toString();

      // Build the JSON response containing the URL
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode responseJson = mapper.createObjectNode();
      responseJson.put("url", url);

      return Response.ok(mapper.writeValueAsString(responseJson)).build();
    } catch (S3Exception | JsonProcessingException e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"error\": \"Failed to generate abort presigned URL: " + e.getMessage() + "\"}").build();
    }
  }

  /**
   * Builds and presigns an AbortMultipartUploadRequest (valid for 10 minutes).
   *
   * @param region     AWS region of the target bucket
   * @param bucketName target S3 bucket
   * @param keyName    object key (i.e., file name) associated with the upload
   * @param uploadId   multipart upload ID to abort
   * @return presigned request that can be executed by a client to abort the upload
   */
  public static PresignedAbortMultipartUploadRequest generateAbortMultipartUploadPresignedUrl(Region region, String bucketName, String keyName, String uploadId) {

    try (S3Presigner s3presigner = S3Presigner.builder() //
      .region(region) //
      .credentialsProvider(DefaultCredentialsProvider.create()) //
      .build()) {

      // Build the original abort request
      AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder() //
        .bucket(bucketName) //
        .key(keyName) //
        .uploadId(uploadId) //
        .build();

      // Wrap it in a presign request with a short expiration
      AbortMultipartUploadPresignRequest presignRequest = AbortMultipartUploadPresignRequest.builder() //
        .signatureDuration(Duration.ofMinutes(10)) //
        .abortMultipartUploadRequest(abortRequest) //
        .build();

      return s3presigner.presignAbortMultipartUpload(presignRequest);
    }
  }
}
