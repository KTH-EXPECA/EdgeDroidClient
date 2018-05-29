package se.kth.molguin.tracedemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import java.util.Locale;

public class FinishedDialog extends DialogFragment {

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
