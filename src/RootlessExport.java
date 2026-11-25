package com.codex.rootlessmacros;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public final class RootlessExport extends ContentProvider {
    static final String FILE = "social-macros-rootless.mdr";
    static final Uri URI = Uri.parse("content://com.codex.rootlessmacros.export/" + FILE);

    @Override public boolean onCreate() { return true; }

    private File file(Uri uri) {
        if (!URI.equals(uri)) throw new IllegalArgumentException("Unknown export");
        return new File(getContext().getCacheDir(), FILE);
    }

    @Override public String getType(Uri uri) {
        file(uri);
        return "application/mdr";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File export = file(uri);
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = FILE;
            if (OpenableColumns.SIZE.equals(columns[i])) values[i] = export.length();
        }
        MatrixCursor result = new MatrixCursor(columns, 1);
        result.addRow(values);
        return result;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only export");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
