package com.example.server.endpoints;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.server.Main;
import com.example.server.TimeUtils;
import com.example.server.STSutil.STSTokenCreator;
import com.example.server.uploadServices.PresignedUrlUploadService;
import com.example.server.uploadServices.StreamServerUploadService;

// Import JAX-RS annotations
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sts.model.Credentials;

/**
 * RESTful service for handling file uploads.
 */
@Path("/files")
public class UploadEndpoints {
  private static final Logger logger = LoggerFactory.getLogger(UploadEndpoints.class);

  /**
   * Generates a presigned S3 upload URL for a file.
   */
  @GET
  @Path("/upload-presign/{fileName}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getPresignedUrlUploadController(@PathParam("fileName") String fileName) {

    // Use "presign" mode only
    if ("presign".equals(Main.getCurrentMethod())) {
      try {
        String bucketName = Main.getBucketName();
        Region region = Main.getRegion();
        String presignedUrl = PresignedUrlUploadService.createPresignedUrlUpload(region, bucketName, fileName);

        // Return the presigned URL as a JSON response
        return Response.ok("{\"url\": \"" + presignedUrl + "\"}").build();
      } catch (S3Exception e) {
        // Handle S3 errors
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"error\": \"Error generating presigned URL: " + e.getMessage() + "\"}").build();
      }
    } else {
      // Reject unsupported methods
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"Method not allowed\"}").build();
    }
  }

  /**
   * Streams file upload data to S3 via server-side processing.
   */
  @POST
  @Path("/upload-s3stream/{fileName}")
  @Consumes(MediaType.APPLICATION_OCTET_STREAM)
  public void postStreamServerUploadController(@PathParam("fileName") String fileName, InputStream uploadedInputStream, @Suspended AsyncResponse asyncResponse, @Context HttpHeaders headers) {

    String lengthHeader = headers.getHeaderString("Content-Length");
    long contentLength = Long.parseLong(lengthHeader);

    // Allow only if server is in "streamS3ObjectViaServer" mode
    if (!"streamS3ObjectViaServer".equals(Main.getCurrentMethod())) {
      asyncResponse.resume(Response.status(Response.Status.BAD_REQUEST).entity("Invalid upload method").build());
      return;
    }

    String bucketName = Main.getBucketName();
    S3AsyncClient asyncClient = Main.getAsyncS3MultiClient();

    // Upload the stream to S3 asynchronously
    StreamServerUploadService.asyncMultipartUpload(asyncClient, bucketName, fileName, uploadedInputStream, contentLength, asyncResponse);
  }

  /**
   * Returns temporary credentials and access point info for uploading via S3 access point.
   */
  @GET
  @Path("/upload-accesspoint/{fileName}/{clientId}")
  @Produces(MediaType.APPLICATION_JSON)
  public void getAccessPointUploadController(@PathParam("fileName") String fileName, @PathParam("clientId") String clientId, @Suspended AsyncResponse asyncResponse, @Context HttpHeaders headers) {

    Map<String, String> response = new HashMap<>();
    String accesspointArn = Main.accesspointArn();

    try {
      // Generate temporary credentials using STS
      Credentials credential = STSTokenCreator.generateSTSToken(clientId);

      // Validate required parameters
      if (credential == null || accesspointArn == null || fileName == null || fileName.isBlank()) {
        throw new IllegalArgumentException("Missing required parameters or credentials.");
      }

      // Prepare response with credentials and access point info
      response.put("accesspointArn", accesspointArn);
      response.put("accessKeyId", credential.accessKeyId());
      response.put("secretAccessKey", credential.secretAccessKey());
      response.put("sessionToken", credential.sessionToken());

      asyncResponse.resume(Response.ok(response).build());

    } catch (Exception e) {
      e.printStackTrace();

      logger.info(String.format("[%s] [AccessPoint]   Failed to retrieve credentials or access point alias | Error: %s | File: %s", TimeUtils.getCurrentTimestamp(), e.getMessage(), fileName));

      // Return error response
      asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of("error", "Failed to retrieve credentials or access point")).type(MediaType.APPLICATION_JSON).build());
    }
  }

}
