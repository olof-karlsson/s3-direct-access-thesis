package com.example.client.uploadServices.MultipartUploadPresign;

public class MultipartUploadDTO {
  /**
   * Simple DTO (Data Transfer Object) used to store metadata for each uploaded part
   * during a multipart upload. This is sent when completing the upload to inform
   * the backend/S3 which parts were uploaded.
   *
   * It carries:
   * - partNumber:  the index of the part (required by S3)
   * - eTag: the ETag returned by S3 after uploading each part
   */
  public static class CompletedPartDTO {
    private int partNumber; // Index of the uploaded part
    private String eTag; // ETag for the uploaded part, used to verify integrity

    // Default no-arg constructor required by Jackson for JSON deserialization
    public CompletedPartDTO() {
    }

    // Constructor to initialize all fields
    public CompletedPartDTO(int partNumber, String eTag) {
      this.partNumber = partNumber;
      this.eTag = eTag;
    }

    // Getters and setters
    public int getPartNumber() {
      return partNumber;
    }

    public void setPartNumber(int partNumber) {
      this.partNumber = partNumber;
    }

    public String getETag() {
      return eTag;
    }

    public void setETag(String eTag) {
      this.eTag = eTag;
    }
  }
}
