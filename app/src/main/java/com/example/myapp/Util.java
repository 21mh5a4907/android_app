package com.example.myapp;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Util {

    private static final String TAG = "Util";

    /**
     * Converts a URI to a byte array.
     * @param context The application context.
     * @param uri The URI of the image or file.
     * @return The byte array of the image/file content.
     * @throws IOException If an error occurs while reading the input stream.
     */
    public static byte[] getBytesFromUri(Context context, Uri uri) throws IOException {
        // Open the InputStream from the URI
        InputStream inputStream = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try {
            inputStream = context.getContentResolver().openInputStream(uri);

            if (inputStream == null) {
                throw new IOException("Failed to open input stream for URI: " + uri);
            }

            byte[] buffer = new byte[1024];
            int length;

            // Read the InputStream and write to ByteArrayOutputStream
            while ((length = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, length);
            }

            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "Error reading bytes from URI: " + uri, e);
            throw e;
        } finally {
            // Close the InputStream and ByteArrayOutputStream
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing InputStream", e);
                }
            }
            try {
                byteArrayOutputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing ByteArrayOutputStream", e);
            }
        }
    }

    /**
     * A helper method to check if the URI is a valid content URI.
     * @param context The application context.
     * @param uri The URI to check.
     * @return true if the URI is valid; false otherwise.
     */
    public static boolean isValidUri(Context context, Uri uri) {
        if (uri == null) {
            return false;
        }

        String scheme = uri.getScheme();
        return scheme != null && scheme.equals("content");
    }

    /**
     * Retrieves the absolute file path of an image URI for file system operations.
     * @param context The application context.
     * @param uri The image URI.
     * @return The absolute file path of the image, or null if not found.
     */
    public static String getFilePathFromUri(Context context, Uri uri) {
        String filePath = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                if (columnIndex != -1) {
                    filePath = cursor.getString(columnIndex);
                }
                cursor.close();
            }
        }
        return filePath;
    }
}
