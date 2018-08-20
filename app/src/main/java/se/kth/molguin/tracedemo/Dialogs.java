package se.kth.molguin.tracedemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;

import java.util.Locale;

public class Dialogs {
    public static class ShutDown extends DialogFragment {

        private final static String ALERT_TEXT =
                "Experiment shutdown.\nSTATUS:%s\nRun count:%d\nMessage:%s";
        private final static String BTN_TEXT = "OK";

        private final static String STATUS_KEY = "status";
        private final static String RUN_COUNT_KEY = "run_count";
        private final static String MSG_KEY = "msg";

        public void setParams(boolean success, int run_count, @Nullable String message) {
            Bundle bundle = new Bundle();
            String status = success ? "Success" : "Error";
            String msg = (message != null) ? message : "";

            bundle.putString(STATUS_KEY, status);
            bundle.putInt(RUN_COUNT_KEY, run_count);
            bundle.putString(MSG_KEY, msg);
            this.setArguments(bundle);
        }


        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle data = getArguments();
            final String status = data.getString(STATUS_KEY);
            final int run_count = data.getInt(RUN_COUNT_KEY);
            final String msg = data.getString(MSG_KEY);
            final Activity mAct = this.getActivity();
            final AlertDialog.Builder builder = new AlertDialog.Builder(mAct);

            builder.setMessage(String.format(
                    Locale.ENGLISH,
                    ALERT_TEXT,
                    status,
                    run_count,
                    msg
            ));


            builder.setPositiveButton(BTN_TEXT, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    mAct.finishAndRemoveTask();
                }
            });

            Dialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            return dialog;
        }
    }
}
