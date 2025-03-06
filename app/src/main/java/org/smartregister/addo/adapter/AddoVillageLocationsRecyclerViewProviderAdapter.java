package org.smartregister.addo.adapter;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.smartregister.addo.R;
import org.smartregister.domain.db.Address;
import org.smartregister.domain.jsonmapping.Location;
import org.smartregister.view.activity.BaseRegisterActivity;

import java.util.List;

public class AddoVillageLocationsRecyclerViewProviderAdapter extends RecyclerView.Adapter<AddoVillageLocationsRecyclerViewProviderAdapter.MyViewHolder> {
    private List<Location> addoLocation;

    private View.OnClickListener onClickListener;

    private AddoVillageLocationsRecyclerViewProviderAdapter.OnItemClickListener onItemClickListener;

    public AddoVillageLocationsRecyclerViewProviderAdapter(List<Location> addoLocation,  Activity activity) {
        this.addoLocation = addoLocation;
        this.onClickListener = new AddoLocationAdapterListener(activity);
    }

    @NonNull
    @Override
    public AddoVillageLocationsRecyclerViewProviderAdapter.MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.addo_villages_list_row, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        Location item = null;
        if (!addoLocation.isEmpty()) {
            if (position != (getItemCount() - 1)) {
                item = this.addoLocation.get(position);
                holder.tvVillageName.setText(item.getName());
            } else {
                item = new  Location("node.getLocationId()", String.valueOf(R.string.addo_other_village),new Address(),new Location());
                holder.tvVillageName.setText(R.string.addo_other_village);
                holder.ivLocationIcon.setVisibility(View.INVISIBLE);
            }
            View location = holder.myView;
            if (onClickListener != null) {
                attachLocationOnclickListener(location, item);
            }
        }
    }

    @Override
    public int getItemCount() {
        if (!this.addoLocation.isEmpty()) {
            return this.addoLocation.size() + 1;
        }
        return 0;
    }
    public void setOnItemClickListener(AddoVillageLocationsRecyclerViewProviderAdapter.OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    private void attachLocationOnclickListener(View view, Location locationItem) {
        //view.setOnClickListener(onClickListener);
        view.setTag(locationItem);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onItemClickListener.onClick(locationItem);
            }
        });
    }

    public interface OnItemClickListener {
        void onClick(Location location);
    }


    public class MyViewHolder extends RecyclerView.ViewHolder {

        private TextView tvVillageName;
        private ImageView ivLocationIcon;
        private View myView;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            tvVillageName = itemView.findViewById(R.id.village_name);
            ivLocationIcon = itemView.findViewById(R.id.locationImageView);

            myView = itemView;
        }

        public  View getView() {
            return myView;
        }
    }

    public class AddoLocationAdapterListener implements View.OnClickListener {

        private Activity activity;

        public AddoLocationAdapterListener(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void onClick(View v) {
            // Here is where we start the activity with fingerprint search functionality
            // Also pass the location selected
            ((BaseRegisterActivity) activity).switchToFragment(3);
        }

    }
}
