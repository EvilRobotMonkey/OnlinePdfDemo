package com.example.tao_oat.myapplication;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.text.TextUtils;
import android.widget.Toast;


import com.artifex.mupdfdemo.MuPDFActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by Tao_oaT on 2017/9/10.
 */

public class DownPdfFileUtil {


    public static void downPdf(final Context context, String address, final String name) {


        if (context == null || TextUtils.isEmpty(address)) {
            return;
        }
        String fileDir = Environment.getExternalStorageDirectory().getPath() + "/temp";
        String fileName = "product_pdf.pdf";
        FileDownLoadLister fileDownLoadLister = new FileDownLoadLister() {
            @Override
            public void callBack(File file, boolean flag) {
                if (flag) {
                    Intent intent = new Intent(context, MuPDFActivity.class);
                    intent.setData(Uri.parse(file.getAbsolutePath()));
                    intent.putExtra("title", name);
                    intent.putExtra("isShare", 0);
                    intent.setAction(Intent.ACTION_VIEW);
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, "文件加载失败",Toast.LENGTH_SHORT).show();
                }
            }
        };
        fileDownUtils(fileDownLoadLister, address, fileDir, fileName);
    }
    public static void fileDownUtils(FileDownLoadLister listener, String fileUrl, String fileDir, String fileName) {
        new FileDownLoadTask(listener,fileDir, fileName).execute(fileUrl);
    }

    static class FileDownLoadTask extends AsyncTask<String, Integer, Boolean> {
        private FileDownLoadLister listener;
        private String fileDir,fileName;

        public FileDownLoadTask(FileDownLoadLister listener,String fileDir, String fileName) {
            this.listener = listener;
            this.fileDir=fileDir;
            this.fileName=fileName;
        }

        @Override
        protected Boolean doInBackground(String... params) {

            return downFile(params[0], fileDir,fileName);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if(listener != null){
                listener.callBack(new File(fileDir,fileName).getAbsoluteFile(), result);
            }
        }
    }
    public static boolean downFile(String path, String pathDir, String fileName) {
        try {
            URL url = new URL(path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5 * 1000);
            conn.setRequestMethod("GET");
            conn.setReadTimeout(30000);
            InputStream inStream = conn.getInputStream();
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return FileWriter(inStream, pathDir, fileName);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;

    }

    public static boolean FileWriter(InputStream in, String path, String fileName) {
        FileOutputStream out = null;
        try {
            File fileDir = new File(path);
            if (!fileDir.exists())
                fileDir.mkdirs();
            File file = new File(path, fileName);
            if (!file.exists()) {
                file.createNewFile();
            } else
                file.delete();
            out = new FileOutputStream(file);
            int i;
            byte[] bt = new byte[1024];
            while ((i = in.read(bt)) != -1) {
                out.write(bt, 0, i);
                out.flush();
            }
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                in.close();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
