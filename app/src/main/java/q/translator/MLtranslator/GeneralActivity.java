/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nie.translator.MLTranslator;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import javax.annotation.Nullable;


public abstract class GeneralActivity extends FragmentActivity {

    public void showMissingGoogleTTSDialog(@Nullable DialogInterface.OnClickListener continueListener) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.error_missing_tts);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts"));
                intent.setPackage("com.android.vending");
                try {
                    startActivity(intent);
                }catch (Exception e){
                    e.printStackTrace();
                    finish();
                }
            }
        });
        if(continueListener != null){
            builder.setNegativeButton(R.string.continue_without_tts, continueListener);
        }
        builder.create().show();
    }

    public void showGoogleTTSErrorDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.error_tts);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.create().show();
    }

    public void showGoogleTTSErrorDialog(DialogInterface.OnClickListener continueListener) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.error_tts);
        builder.setPositiveButton(R.string.continue_without_tts, continueListener);
        builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.create().show();
    }

    public void showInternetLackDialog(int message, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, listener);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void showApiKeyFileErrorDialog(int message, DialogInterface.OnClickListener confirmListener, DialogInterface.OnClickListener cancelListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, confirmListener);
        builder.setNegativeButton(R.string.exit, cancelListener);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void showConfirmDeleteDialog(DialogInterface.OnClickListener confirmListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(R.string.dialog_confirm_delete);
        builder.setPositiveButton(android.R.string.ok, confirmListener);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    protected void showConfirmExitDialog(DialogInterface.OnClickListener confirmListener) {
        //creazione del dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(R.string.dialog_confirm_exit);
        builder.setPositiveButton(android.R.string.ok, confirmListener);
        builder.setNegativeButton(android.R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void onError(int reason, long value) {
        /*switch (reason) {
            case ErrorCodes.MISSING_PLAY_SERVICES: {
                GoogleApiAvailability.getInstance().getErrorDialog(this, (int) value, 34).show();
                break;
            }
        }*/
    }
}
