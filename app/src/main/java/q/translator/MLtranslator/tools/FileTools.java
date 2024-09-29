package nie.translator.MLTranslator.tools;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

public class FileTools {

    public static void moveFile(File from, File to, MoveFileCallback callback) {
        boolean success = from.renameTo(to);
        if (!success) {
            //if the instant move of the file not work we do the real transfer (that can also be instant, but it is not guaranteed)
            try {
                FileChannel inChannel = new FileInputStream(from).getChannel();
                FileChannel outChannel = new FileOutputStream(to).getChannel();
                try {
                    //we start the copy
                    inChannel.transferTo(0, inChannel.size(), outChannel);
                    //we remove old copy
                    boolean deleteSuccess = from.delete();
                    //we notify the success of the transfer
                    callback.onSuccess();
                } finally {
                    if (inChannel != null)
                        inChannel.close();
                    if (outChannel != null)
                        outChannel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                //we notify the failure of the transfer
                callback.onFailure();
            }
        }else{
            //we notify the success of the transfer
            callback.onSuccess();
        }
    }

    public static abstract class MoveFileCallback{
        public abstract void onSuccess();
        public abstract void onFailure();
    }

    public static byte[] convertStreamToByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buff = new byte[10240];
        int i;
        while ((i = is.read(buff, 0, buff.length)) > 0) {
            byteArrayOutputStream.write(buff, 0, i);
        }

        return byteArrayOutputStream.toByteArray();
    }

    public static boolean copyAssetToInternalMemory(Context context, String assetFileName){
        try {
            File outFile = new File(context.getFilesDir(), assetFileName);
            InputStream in = context.getAssets().open(assetFileName);
            OutputStream out = new FileOutputStream(outFile);
            copyFile(in, out);
            in.close();
            out.flush();
            out.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
