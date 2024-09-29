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

package nie.translator.MLTranslator.access;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import nie.translator.MLTranslator.Global;
import nie.translator.MLTranslator.R;

public class NoticeFragment extends Fragment {
    private TextView noticeDescription;
    private Button buttonConfirm;
    private AccessActivity activity;
    private TextView ramErrorText;
    private Global global;


    public NoticeFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_notice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        noticeDescription = view.findViewById(R.id.noticeDescription);
        buttonConfirm = view.findViewById(R.id.buttonConfirm);
        ramErrorText = view.findViewById(R.id.ramErrorText);
        noticeDescription.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = (AccessActivity) requireActivity();
        global = (Global) activity.getApplication();
        if(global.getTotalRamSize() <= 5000) {
            ramErrorText.setVisibility(View.VISIBLE);
        }
        buttonConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.startFragment(AccessActivity.USER_DATA_FRAGMENT,null);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if(getContext() != null && !NotificationManagerCompat.from(getContext()).areNotificationsEnabled()){
            //Toast.makeText(getContext(), "Notification permission granted", Toast.LENGTH_SHORT).show();
        }
        //we create the readme file in the externalFilesDir
        if(getContext() != null) {
            File readmeFile = new File(getContext().getExternalFilesDir(null) + "/Readme.txt");
            if(!readmeFile.exists()){
                try {
                    FileWriter writer = new FileWriter(readmeFile);
                    writer.append("Insert the models in this folder, for more info read the tutorial for sideloading in the GitHub page of MLTranslator");
                    writer.flush();
                    writer.close();
                    android.util.Log.i("files", "Readme created");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
