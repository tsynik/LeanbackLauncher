package com.amazon.tv.leanbacklauncher.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import com.amazon.tv.leanbacklauncher.BuildConfig;
import com.amazon.tv.leanbacklauncher.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.HttpsURLConnection;

public class LegacyUpdatePreferenceFragment extends GuidedStepSupportFragment {

    private String RELEASES_LINK = "https://api.github.com/repos/Dustmix/LeanbackLauncher/releases/latest";
    private String DOWNLOAD_LINK = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new GetInfo(RELEASES_LINK);
    }

    @Override
    public GuidanceStylist onCreateGuidanceStylist() {
        return super.onCreateGuidanceStylist();
    }

    @NonNull
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(getString(R.string.update), getString(R.string.update_desc), null, ResourcesCompat.getDrawable(getResources(), R.drawable.ic_settings_home, null));
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        super.onCreateActions(actions, savedInstanceState);
        GuidedAction info = new GuidedAction.Builder(getActivity())
                .id(1)
                .title(R.string.update_in_progress)
                .description(null)
                .infoOnly(true)
                .build();
        actions.add(info);
    }

    public void updateAction(@NonNull JSONObject info) {
        String lastVersion = info.optString("tag_name", BuildConfig.VERSION_NAME).replace("v", "");
        JSONArray assets = info.optJSONArray("assets");
        if (assets != null) {
            JSONObject firstAssets = assets.optJSONObject(0);
            if (firstAssets.optString("content_type").equals("application/vnd.android.package-archive")) {
                DOWNLOAD_LINK = firstAssets.optString("browser_download_url");
            }
        }
        Double lastVersionDouble;
        try { lastVersionDouble = Double.parseDouble(lastVersion); } catch (NumberFormatException npe) { lastVersionDouble = 0d; }
        Double currentVersionDouble;
        try { currentVersionDouble = Double.parseDouble(BuildConfig.VERSION_NAME); } catch (NumberFormatException npe) { currentVersionDouble = 0d; }

        GuidedAction actionInfo = findActionById(1);
        actionInfo.setTitle(String.format("%s %s", getString(R.string.app_name), info.optString("name")));
        actionInfo.setDescription(getString(R.string.update_no_updates));
        int position = findActionPositionById(actionInfo.getId());
        notifyActionChanged(position);

        if (lastVersionDouble.compareTo(currentVersionDouble) > 0) {
            actionInfo.setDescription(getString(R.string.update_new_version));
            List<GuidedAction> listActions = new ArrayList<>();
            listActions.add(new GuidedAction.Builder(requireActivity())
                    .id(2)
                    .title(R.string.update_changes)
                    .description(info.optString("body", getString(R.string.update_no_info)))
                    .multilineDescription(true)
                    .focusable(false)
                    .infoOnly(true)
                    .build()
            );
            listActions.add(actionInfo);
            listActions.add(new GuidedAction.Builder(requireActivity())
                    .id(3)
                    .title(R.string.update_install)
                    .build()
            );
            setActions(listActions);
        }
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        super.onGuidedActionClicked(action);
        if (action.getId() == 3L && !DOWNLOAD_LINK.isEmpty()) {
            new Download(DOWNLOAD_LINK, Objects.requireNonNull(Objects.requireNonNull(getContext()).getExternalCacheDir()).toString());
        }
    }

    private class GetInfo extends AsyncTask<String, Void, String> {
        public GetInfo(String url) {
            this.execute(url);
        }

        @Override
        protected String doInBackground(String... strings) {
            HttpsURLConnection urlConnection = null;

            try {
                URL url = new URL(strings[0]);
                urlConnection = (HttpsURLConnection) url.openConnection();
                urlConnection.connect();

                int responseCode = urlConnection.getResponseCode();
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    return readStream(urlConnection.getInputStream());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            try {
                JSONObject info = new JSONObject(s);
                updateAction(info);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private String readStream(InputStream in) throws IOException {
            if (in == null) {
                return null;
            }
            InputStreamReader inputStreamReader = new InputStreamReader(in);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                buffer.append(line).append("\n");
            }

            if (buffer.length() == 0) {
                return null;
            }
            return buffer.toString();
        }
    }

    private class Download extends AsyncTask<String, Void, File> {
        private static final int BUFFER_SIZE = 4096;

        public Download(String url, String filePath) {
            System.out.println("Start download to " + filePath);
            this.execute(url, filePath);
        }

        @Override
        protected File doInBackground(String... strings) {
            String fileURL = strings[0];
            String saveDir = strings[1];
            HttpsURLConnection urlConnection = null;

            try {
                URL url = new URL(fileURL);
                urlConnection = (HttpsURLConnection) url.openConnection();
                urlConnection.connect();

                int responseCode = urlConnection.getResponseCode();
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    String fileName = "";
                    String disposition = urlConnection.getHeaderField("Content-Disposition");
                    String contentType = urlConnection.getContentType();
                    int contentLength = urlConnection.getContentLength();
                    if (disposition != null) {
                        // extracts file name from header field
                        int index = disposition.indexOf("filename=");
                        if (index > 0) {
                            fileName = disposition.substring(index + 9);
                        }
                    } else {
                        fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1);
                    }
                    System.out.println("Content-Type = " + contentType);
                    System.out.println("Content-Length = " + contentLength);
                    System.out.println("fileName = " + fileName);
                    System.out.println("Content-Disposition = " + disposition);

                    // opens input stream from the HTTP connection
                    InputStream inputStream = urlConnection.getInputStream();
                    String saveFilePath = saveDir + File.separator + fileName;

                    // opens an output stream to save into file
                    FileOutputStream outputStream = new FileOutputStream(saveFilePath);

                    int bytesRead = -1;
                    byte[] buffer = new byte[BUFFER_SIZE];
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.close();
                    inputStream.close();
                    return new File(saveFilePath);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(File f) {
            super.onPostExecute(f);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT < 23) {
                intent.setDataAndType(Uri.fromFile(f), "application/vnd.android.package-archive");
            } else {
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(FileProvider.getUriForFile(requireContext(),
                        String.format("%s.fileProvider", BuildConfig.APPLICATION_ID),
                        f
                ), "application/vnd.android.package-archive");
            }
            requireContext().startActivity(intent);
            finishGuidedStepSupportFragments();
        }
    }

}
