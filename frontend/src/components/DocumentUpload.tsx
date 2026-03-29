"use client";

import { useState, useRef, type DragEvent, type ChangeEvent } from "react";
import { useApiClient } from "@/hooks/useApiClient";
import { useAuthStore } from "@/hooks/useAuth";

interface UploadedDocument {
  id: string;
  originalFilename: string;
  status: "PROCESSING" | "READY" | "FAILED";
  chunkCount?: number;
  errorMessage?: string;
}

interface DocumentUploadProps {
  projectId: string;
  onUploadComplete?: (document: UploadedDocument) => void;
}

export default function DocumentUpload({
  projectId,
  onUploadComplete,
}: DocumentUploadProps) {
  const apiClient = useApiClient();
  const accessToken = useAuthStore((s) => s.accessToken);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [currentFile, setCurrentFile] = useState<File | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploadedDocument, setUploadedDocument] =
    useState<UploadedDocument | null>(null);

  const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
  const ALLOWED_TYPES = ["application/pdf", "text/plain"];

  const validateFile = (file: File): string | null => {
    if (file.size > MAX_FILE_SIZE) {
      return `File size exceeds 10MB limit (${(file.size / 1024 / 1024).toFixed(2)}MB)`;
    }

    if (!ALLOWED_TYPES.includes(file.type)) {
      return `File type not supported. Please upload PDF or TXT files only.`;
    }

    return null;
  };

  const handleDragOver = (e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);

    const files = e.dataTransfer.files;
    if (files.length > 0) {
      handleFileSelection(files[0]);
    }
  };

  const handleFileInputChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      handleFileSelection(e.target.files[0]);
    }
  };

  const handleFileSelection = (file: File) => {
    setUploadError(null);
    setUploadedDocument(null);

    const error = validateFile(file);
    if (error) {
      setUploadError(error);
      return;
    }

    setCurrentFile(file);
  };

  const handleUpload = async () => {
    if (!currentFile) {
      setUploadError("No file selected");
      return;
    }

    setIsUploading(true);
    setUploadError(null);
    setUploadProgress(0);

    try {
      // Simulate progress while uploading
      const progressInterval = setInterval(() => {
        setUploadProgress((prev) => Math.min(prev + Math.random() * 30, 90));
      }, 200);

      // Create FormData for file upload
      const formData = new FormData();
      formData.append("file", currentFile);
      formData.append("projectId", projectId);

      // Upload via REST API (using fetch since this is file upload)
      const response = await fetch("/api/documents/upload", {
        method: "POST",
        body: formData,
        headers: {
          Authorization: `Bearer ${accessToken || ""}`,
        },
      });

      clearInterval(progressInterval);
      setUploadProgress(100);

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || "Upload failed");
      }

      const result = await response.json();
      if (result.id) {
        const newDocument: UploadedDocument = {
          id: result.id,
          originalFilename: result.originalFilename,
          status: "PROCESSING",
          chunkCount: result.chunkCount,
        };
        setUploadedDocument(newDocument);

        // Poll for document status
        pollDocumentStatus(result.id);

        // Callback
        if (onUploadComplete) {
          onUploadComplete(newDocument);
        }
      }
    } catch (error: any) {
      console.error("Upload error:", error);
      setUploadError(error.message || "Failed to upload document");
    } finally {
      setIsUploading(false);
      setCurrentFile(null);
      setUploadProgress(0);
    }
  };

  const pollDocumentStatus = async (documentId: string, maxAttempts = 30) => {
    let attempts = 0;

    const checkStatus = async () => {
      if (attempts >= maxAttempts) {
        setUploadError("Document processing timeout");
        return;
      }

      try {
        const response = await apiClient.post("/graphql", {
          query: `
              query GetDocument($id: ID!) {
                document(id: $id) {
                  id
                  status
                  chunkCount
                  errorMessage
                }
              }
            `,
          variables: { id: documentId },
        });

        if (response.data?.document) {
          const doc = response.data.document;
          setUploadedDocument((prev) =>
            prev
              ? {
                  ...prev,
                  status: doc.status,
                  chunkCount: doc.chunkCount,
                  errorMessage: doc.errorMessage,
                }
              : null,
          );

          if (doc.status === "READY" || doc.status === "FAILED") {
            // Status resolved
            return;
          }
        }
      } catch (error) {
        console.error("Error checking document status:", error);
      }

      // Poll again after 2 seconds
      attempts++;
      setTimeout(checkStatus, 2000);
    };

    checkStatus();
  };

  const getFileTypeIcon = (filename: string): string => {
    if (filename.endsWith(".pdf")) return "📄";
    if (filename.endsWith(".txt")) return "📝";
    return "📋";
  };

  return (
    <div className="space-y-4">
      {/* File Input Area */}
      {!uploadedDocument && (
        <div
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          className={`border-2 border-dashed rounded-lg p-8 text-center transition ${
            isDragging
              ? "border-blue-400 bg-blue-900/20"
              : "border-slate-600 bg-slate-900/50 hover:border-slate-500"
          }`}
        >
          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf,.txt"
            onChange={handleFileInputChange}
            className="hidden"
          />

          {currentFile ? (
            <div className="space-y-4">
              <div className="text-3xl mb-2">
                {getFileTypeIcon(currentFile.name)}
              </div>
              <p className="text-white font-medium">{currentFile.name}</p>
              <p className="text-slate-400 text-sm">
                {(currentFile.size / 1024 / 1024).toFixed(2)} MB
              </p>
              <div className="flex gap-2 justify-center pt-2">
                <button
                  onClick={handleUpload}
                  disabled={isUploading}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-slate-700 text-white rounded-lg transition"
                >
                  {isUploading ? "Uploading..." : "Upload Document"}
                </button>
                <button
                  onClick={() => {
                    setCurrentFile(null);
                    if (fileInputRef.current) fileInputRef.current.value = "";
                  }}
                  className="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white rounded-lg transition"
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            <div
              onClick={() => fileInputRef.current?.click()}
              className="cursor-pointer"
            >
              <div className="text-4xl mb-4">📤</div>
              <p className="text-white font-medium mb-2">
                Drag and drop your files here
              </p>
              <p className="text-slate-400 text-sm mb-4">
                or click to select files
              </p>
              <p className="text-slate-500 text-xs">
                Supported: PDF, TXT (max 10 MB)
              </p>
            </div>
          )}
        </div>
      )}

      {/* Upload Progress */}
      {isUploading && (
        <div className="bg-slate-900 border border-slate-700 rounded-lg p-4">
          <div className="flex justify-between items-center mb-2">
            <p className="text-white text-sm font-medium">Uploading...</p>
            <p className="text-slate-400 text-sm">
              {Math.round(uploadProgress)}%
            </p>
          </div>
          <div className="w-full h-2 bg-slate-700 rounded-full overflow-hidden">
            <div
              className="h-full bg-gradient-to-r from-blue-500 to-purple-500 transition-all"
              style={{ width: `${uploadProgress}%` }}
            />
          </div>
        </div>
      )}

      {/* Error Message */}
      {uploadError && (
        <div className="bg-red-900/20 border border-red-700 rounded-lg p-4">
          <p className="text-red-300 text-sm">{uploadError}</p>
        </div>
      )}

      {/* Uploaded Document Status */}
      {uploadedDocument && (
        <div className="bg-slate-900 border border-slate-700 rounded-lg p-4">
          <div className="flex items-center gap-3 mb-3">
            <span className="text-2xl">
              {getFileTypeIcon(uploadedDocument.originalFilename)}
            </span>
            <div className="flex-1">
              <p className="text-white font-medium truncate">
                {uploadedDocument.originalFilename}
              </p>
              <div className="flex items-center gap-2 mt-1">
                <span
                  className={`inline-block w-2 h-2 rounded-full ${
                    uploadedDocument.status === "READY"
                      ? "bg-green-500"
                      : uploadedDocument.status === "PROCESSING"
                        ? "bg-blue-500 animate-pulse"
                        : "bg-red-500"
                  }`}
                />
                <span className="text-xs text-slate-400 capitalize">
                  {uploadedDocument.status === "PROCESSING"
                    ? "Processing..."
                    : uploadedDocument.status}
                </span>
              </div>
            </div>
          </div>

          {/* Processing Progress */}
          {uploadedDocument.status === "PROCESSING" && (
            <div className="space-y-2">
              <div className="w-full h-1 bg-slate-700 rounded-full overflow-hidden">
                <div
                  className="h-full bg-blue-500 rounded-full animate-pulse"
                  style={{ width: "60%" }}
                />
              </div>
              <p className="text-xs text-slate-400">
                Extracting and embedding document...
              </p>
            </div>
          )}

          {/* Completed Status */}
          {uploadedDocument.status === "READY" &&
            uploadedDocument.chunkCount && (
              <div className="text-xs text-slate-400 mt-2">
                ✓ Successfully processed into {uploadedDocument.chunkCount}{" "}
                chunks
              </div>
            )}

          {/* Error Status */}
          {uploadedDocument.status === "FAILED" &&
            uploadedDocument.errorMessage && (
              <div className="text-xs text-red-400 mt-2">
                ✗ {uploadedDocument.errorMessage}
              </div>
            )}

          {/* Upload Another */}
          {uploadedDocument.status === "READY" && (
            <button
              onClick={() => {
                setUploadedDocument(null);
                setUploadError(null);
                if (fileInputRef.current) fileInputRef.current.value = "";
              }}
              className="mt-4 w-full text-sm text-blue-400 hover:text-blue-300 transition"
            >
              + Upload Another Document
            </button>
          )}
        </div>
      )}
    </div>
  );
}
