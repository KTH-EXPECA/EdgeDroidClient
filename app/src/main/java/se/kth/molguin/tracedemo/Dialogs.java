package se.kth.molguin.tracedemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import java.util.Locale;

public class Dialogs {
    public static class Finished extends DialogFragment {

        private final static String ALERT_TEXT = "Finished experiment!\nTotal runs: %d";
        private final static String BTN_TEXT = "OK";

        private int run_count;

        @Override
        public void setArguments(Bundle a) {
            this.run_count = a.getInt("run_count");
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity mAct = this.getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(mAct);
            builder.setMessage(String.format(Locale.ENGLISH, ALERT_TEXT, this.run_count))
                    .setPositiveButton(BTN_TEXT, new DialogInterface.OnClickListener() {
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

    public static class Error extends DialogFragment {

        private static final String ALERT_TEXT = "Error on step %d:\n%s";
        private final static String BTN_TEXT = "Exit";

        private String error_txt;

        @Override
        public void setArguments(Bundle a) {
            int step = a.getInt("step");
            String error = a.getString("error");
            this.error_txt = String.format(Locale.ENGLISH, ALERT_TEXT, step, error);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity mAct = this.getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(mAct);
            builder.setMessage(this.error_txt)
                    .setPositiveButton(BTN_TEXT, new DialogInterface.OnClickListener() {
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
