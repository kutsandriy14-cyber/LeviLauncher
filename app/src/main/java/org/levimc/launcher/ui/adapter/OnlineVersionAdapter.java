package org.levimc.launcher.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.levimc.launcher.R;

import java.util.List;

public class OnlineVersionAdapter extends RecyclerView.Adapter<OnlineVersionAdapter.ViewHolder> {

    public static class OnlineVersion {
        public String version;
        public String url;
        public boolean isBeta;

        public OnlineVersion(String version, String url, boolean isBeta) {
            this.version = version;
            this.url = url;
            this.isBeta = isBeta;
        }
    }

    private final List<OnlineVersion> versions;
    private final OnInstallClickListener listener;

    public interface OnInstallClickListener {
        void onInstallClick(OnlineVersion version);
    }

    public OnlineVersionAdapter(List<OnlineVersion> versions, OnInstallClickListener listener) {
        this.versions = versions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_online_version, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OnlineVersion item = versions.get(position);
        holder.versionCode.setText(item.version);
        holder.versionName.setText("Minecraft_" + item.version);
        holder.btnInstall.setOnClickListener(v -> {
            if (listener != null) listener.onInstallClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return versions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView versionCode, versionName, btnInstall;

        ViewHolder(View itemView) {
            super(itemView);
            versionCode = itemView.findViewById(R.id.version_code);
            versionName = itemView.findViewById(R.id.version_name);
            btnInstall = itemView.findViewById(R.id.btn_install);
        }
    }
}
