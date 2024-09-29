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

package nie.translator.MLTranslator.tools.gui.messages;

import android.os.Parcel;
import android.os.Parcelable;

import nie.translator.MLTranslator.bluetooth.Message;

public class GuiMessage implements Parcelable {
    private Message message;
    private boolean isMine;
    private boolean isFinal;
    private long messageID = -1;


    public GuiMessage(Message message, boolean isMine, boolean isFinal) {
        this.message=message;
        this.isMine = isMine;
        this.isFinal = isFinal;
    }

    public GuiMessage(Message message, long messageID, boolean isMine, boolean isFinal) {
        this.message=message;
        this.messageID = messageID;
        this.isMine = isMine;
        this.isFinal = isFinal;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public boolean isMine() {
        return isMine;
    }

    public void setMine(boolean mine) {
        isMine = mine;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }

    /*@Override
    public boolean equals(@Nullable Object obj) {
        if(obj instanceof GuiMessage && messageID != -1 && ((GuiMessage) obj).messageID != -1){
            return messageID == ((GuiMessage) obj).messageID;
        }
        return super.equals(obj);
    }*/

    //parcel implementation
    public static final Creator<GuiMessage> CREATOR = new Creator<GuiMessage>() {   // this attribute may create problems with the one of ConversationMessage
        @Override
        public GuiMessage createFromParcel(Parcel in) {
            return new GuiMessage(in);
        }

        @Override
        public GuiMessage[] newArray(int size) {
            return new GuiMessage[size];
        }
    };

    private GuiMessage(Parcel in) {
        message = in.readParcelable(Message.class.getClassLoader());
        messageID = in.readLong();
        isMine = in.readByte() != 0;
        isFinal = in.readByte() != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(message, i);
        parcel.writeLong(messageID);
        parcel.writeByte((byte) (isMine ? 1 : 0));
        parcel.writeByte((byte) (isFinal ? 1 : 0));
    }

    public long getMessageID() {
        return messageID;
    }

    public void setMessageID(long messageID) {
        this.messageID = messageID;
    }
}
