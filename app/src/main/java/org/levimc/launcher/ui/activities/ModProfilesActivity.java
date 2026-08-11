package org.levimc.launcher.ui.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.levimc.launcher.R;
import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.core.versions.VersionManager;
import org.levimc.launcher.ui.animation.DynamicAnim;
import org.levimc.launcher.util.LauncherStorage;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Stores named snapshots of the selected instance’s mod files and reports duplicate candidates. */
public class ModProfilesActivity extends BaseActivity {
    private static final String PREFS = "mod_profiles";
    private static final String KEY_NAMES = "names";
    private EditText profileName;
    private LinearLayout profileList;
    private TextView conflictResult;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mod_profiles);
        profileName = findViewById(R.id.mod_profile_name);
        profileList = findViewById(R.id.mod_profiles_list);
        conflictResult = findViewById(R.id.mod_conflict_result);
        Button save = findViewById(R.id.mod_profile_save);
        Button check = findViewById(R.id.mod_conflict_check);
        save.setOnClickListener(v -> saveCurrentProfile());
        check.setOnClickListener(v -> checkConflicts());
        DynamicAnim.applyPressScale(save);
        DynamicAnim.applyPressScale(check);
        renderProfiles();
        checkConflicts();
    }

    private void saveCurrentProfile() {
        String name = profileName.getText().toString().trim();
        if (name.isEmpty()) {
            profileName.setError("Enter a profile name");
            return;
        }
        List<String> files = getCurrentModFiles();
        SharedPreferences prefs = prefs();
        Set<String> names = new LinkedHashSet<>(prefs.getStringSet(KEY_NAMES, Collections.emptySet()));
        names.add(name);
        prefs.edit().putStringSet(KEY_NAMES, names)
                .putString(profileKey(name), TextUtils.join("\n", files))
                .apply();
        profileName.setText("");
        Toast.makeText(this, "Saved " + files.size() + " mod files", Toast.LENGTH_SHORT).show();
        renderProfiles();
    }

    private void renderProfiles() {
        profileList.removeAllViews();
        Set<String> names = new LinkedHashSet<>(prefs().getStringSet(KEY_NAMES, Collections.emptySet()));
        if (names.isEmpty()) {
            profileList.addView(createText("No saved profiles. Save the current mod set to create one.", 13, getColor(R.color.text_secondary)));
            return;
        }
        for (String name : names) {
            String saved = prefs().getString(profileKey(name), "");
            int count = saved.isEmpty() ? 0 : saved.split("\\n").length;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(6), dp(10), dp(6), dp(10));
            TextView label = createText(name + "\n" + count + " files", 13, getColor(R.color.on_surface));
            label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Button apply = new Button(this);
            apply.setText("Apply");
            apply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            apply.setMinWidth(0);
            apply.setMinHeight(0);
            apply.setOnClickListener(v -> applyProfile(name));
            Button remove = new Button(this);
            remove.setText("Delete");
            remove.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            remove.setMinWidth(0);
            remove.setMinHeight(0);
            remove.setOnClickListener(v -> deleteProfile(name));
            row.addView(label);
            row.addView(apply);
            row.addView(remove);
            profileList.addView(row);
        }
    }

    private void applyProfile(String name) {
        String saved = prefs().getString(profileKey(name), "");
        Set<String> enabled = new LinkedHashSet<>();
        if (!saved.isEmpty()) enabled.addAll(Arrays.asList(saved.split("\\n")));
        GameVersion version;
        try { version = VersionManager.get(this).getSelectedVersion(); }
        catch (Exception ignored) { version = null; }
        if (version == null || version.directoryName == null) {
            Toast.makeText(this, "Select a Minecraft instance first", Toast.LENGTH_LONG).show();
            return;
        }
        File dir = LauncherStorage.getProfileModsDir(this, version.directoryName);
        File[] files = dir.listFiles(File::isFile);
        if (files == null) files = new File[0];
        int changed = 0;
        for (File file : files) {
            String fileName = file.getName();
            if (fileName.endsWith(".disabled")) {
                String original = fileName.substring(0, fileName.length() - ".disabled".length());
                File target = new File(dir, original);
                if (enabled.contains(original) && !target.exists() && file.renameTo(target)) changed++;
            } else if (!enabled.contains(fileName)) {
                File target = new File(dir, fileName + ".disabled");
                if (!target.exists() && file.renameTo(target)) changed++;
            }
        }
        prefs().edit().putString("active_profile", name).apply();
        Toast.makeText(this, "Applied profile " + name + "; changed " + changed + " files", Toast.LENGTH_LONG).show();
        checkConflicts();
    }

    private void deleteProfile(String name) {
        Set<String> names = new LinkedHashSet<>(prefs().getStringSet(KEY_NAMES, Collections.emptySet()));
        names.remove(name);
        prefs().edit().putStringSet(KEY_NAMES, names).remove(profileKey(name)).apply();
        renderProfiles();
    }

    private void checkConflicts() {
        List<String> files = getCurrentModFiles();
        if (files.isEmpty()) {
            conflictResult.setText("No mod files found for the selected instance.");
            return;
        }
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String file : files) {
            String normalized = normalize(file);
            grouped.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(file);
        }
        StringBuilder conflicts = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            if (entry.getValue().size() > 1) {
                if (conflicts.length() > 0) conflicts.append("\n\n");
                conflicts.append("Possible duplicate: ").append(entry.getKey()).append("\n")
                        .append(TextUtils.join("\n", entry.getValue()));
            }
        }
        conflictResult.setText(conflicts.length() == 0
                ? "No duplicate file-name conflicts found across " + files.size() + " mod files."
                : conflicts.toString());
    }

    private List<String> getCurrentModFiles() {
        GameVersion version;
        try { version = VersionManager.get(this).getSelectedVersion(); }
        catch (Exception ignored) { version = null; }
        if (version == null || version.directoryName == null) return Collections.emptyList();
        File modDir = LauncherStorage.getProfileModsDir(this, version.directoryName);
        File[] files = modDir.listFiles(File::isFile);
        if (files == null) return Collections.emptyList();
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        ArrayList<String> result = new ArrayList<>();
        for (File file : files) {
            if (!file.getName().endsWith(".disabled")) result.add(file.getName());
        }
        return result;
    }

    private String normalize(String fileName) {
        String value = fileName.toLowerCase(Locale.ROOT).replaceAll("\\.(so|jar|zip|js)$", "");
        return value.replaceAll("[-_](v?\\d+[\\d._-]*)$", "");
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private String profileKey(String value) { return "profile_" + Integer.toHexString(value.hashCode()); }
    private TextView createText(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        return view;
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
}
