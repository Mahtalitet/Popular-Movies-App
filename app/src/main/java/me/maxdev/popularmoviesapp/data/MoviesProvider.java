package me.maxdev.popularmoviesapp.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;

public class MoviesProvider extends ContentProvider {

    private static final UriMatcher URI_MATCHER = buildUriMatcher();
    private MoviesDbHelper dbHelper;

    static final int MOVIES = 100;
    static final int MOVIE_BY_ID = 101;

    // movies._id = ?
    private static final String MOVIE_ID_SELECTION =
            MoviesContract.MovieEntry.TABLE_NAME + "." + MoviesContract.MovieEntry._ID + " = ? ";

    static UriMatcher buildUriMatcher() {
        final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = MoviesContract.CONTENT_AUTHORITY;

        uriMatcher.addURI(authority, MoviesContract.PATH_MOVIES, MOVIES);
        uriMatcher.addURI(authority, MoviesContract.PATH_MOVIES + "/#", MOVIE_BY_ID);

        return uriMatcher;
    }

    @Override
    public boolean onCreate() {
        dbHelper = new MoviesDbHelper(getContext());
        return true;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        final int match = URI_MATCHER.match(uri);
        switch (match) {
            case MOVIES:
                return MoviesContract.MovieEntry.CONTENT_DIR_TYPE;
            case MOVIE_BY_ID:
                return MoviesContract.MovieEntry.CONTENT_ITEM_TYPE;
            default:
                return null;
        }
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        int match = URI_MATCHER.match(uri);
        Cursor cursor;
        checkColumns(projection);
        switch (match) {
            case MOVIES:
                cursor = dbHelper.getReadableDatabase().query(
                        MoviesContract.MovieEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case MOVIE_BY_ID:
                cursor = getMovieById(uri, projection, sortOrder);
                break;
            default:
                return null;
        }
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        final int match = URI_MATCHER.match(uri);
        Uri returnUri;
        switch (match) {
            case MOVIES:
                long id = db.insertWithOnConflict(MoviesContract.MovieEntry.TABLE_NAME, null,
                        values, SQLiteDatabase.CONFLICT_REPLACE);
                if (id > 0) {
                    returnUri = MoviesContract.MovieEntry.buildMovieUri(id);
                } else {
                    throw new android.database.SQLException("Failed to insert row into " + uri);
                }
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return returnUri;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        final int match = URI_MATCHER.match(uri);
        int rowsUpdated;
        switch (match) {
            case MOVIES:
                rowsUpdated = db.update(MoviesContract.MovieEntry.TABLE_NAME, values,
                        selection, selectionArgs);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return rowsUpdated;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        final int match = URI_MATCHER.match(uri);
        int rowsDeleted;
        switch (match) {
            case MOVIES:
                rowsDeleted = db.delete(MoviesContract.MovieEntry.TABLE_NAME,
                        selection, selectionArgs);
                break;
            case MOVIE_BY_ID:
                long id = MoviesContract.MovieEntry.getIdFromUri(uri);
                rowsDeleted = db.delete(MoviesContract.MovieEntry.TABLE_NAME,
                        MOVIE_ID_SELECTION, new String[]{Long.toString(id)});

                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        if (rowsDeleted != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return rowsDeleted;
    }

    @Override
    public void shutdown() {
        dbHelper.close();
        super.shutdown();
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        final int match = URI_MATCHER.match(uri);
        switch (match) {
            case MOVIES:
                db.beginTransaction();
                int returnCount = 0;
                try {
                    for (ContentValues value : values) {
                        long id = db.insertWithOnConflict(MoviesContract.MovieEntry.TABLE_NAME,
                                null, value, SQLiteDatabase.CONFLICT_REPLACE);
                        if (id != -1) {
                            returnCount++;
                        }
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                getContext().getContentResolver().notifyChange(uri, null);
                return returnCount;
            default:
                return super.bulkInsert(uri, values);
        }
    }

    private Cursor getMovieById(Uri uri, String[] projection, String sortOrder) {
        long id = MoviesContract.MovieEntry.getIdFromUri(uri);
        String selection = MOVIE_ID_SELECTION;
        String[] selectionArgs =  new String[]{Long.toString(id)};
        return dbHelper.getReadableDatabase().query(
                MoviesContract.MovieEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
        );
    }

    private void checkColumns(String[] projection) {
        if (projection != null) {
            HashSet<String> availableColumns = new HashSet<>(Arrays.asList(
                    MoviesContract.MovieEntry.COLUMNS));
            HashSet<String> requestedColumns = new HashSet<>(Arrays.asList(projection));
            if (!availableColumns.containsAll(requestedColumns)) {
                throw new  IllegalArgumentException("Unknown columns in projection.");
            }
        }
    }

}
