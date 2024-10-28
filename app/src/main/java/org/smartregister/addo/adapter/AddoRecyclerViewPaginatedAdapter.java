package org.smartregister.addo.adapter;

import android.database.Cursor;

import org.smartregister.commonregistry.CommonRepository;
import org.smartregister.cursoradapter.RecyclerViewPaginatedAdapter;
import org.smartregister.cursoradapter.RecyclerViewProvider;

public class AddoRecyclerViewPaginatedAdapter extends RecyclerViewPaginatedAdapter {
    public AddoRecyclerViewPaginatedAdapter(Cursor cursor, RecyclerViewProvider listItemProvider, CommonRepository commonRepository) {
        super(cursor, listItemProvider, commonRepository);
    }

    @Override
    public Cursor swapCursor(Cursor newCursor) {
        if (newCursor == null) {
            return null;
        } else {
            return super.swapCursor(newCursor);
        }
    }
}
