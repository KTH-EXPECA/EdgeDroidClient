package se.kth.molguin.tracedemo;

import android.content.Context;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimestampLogTextView extends android.support.v7.widget.AppCompatTextView {

    private static final String TIME_COLOR = "red";
    private static final String LOG_FMT = "<font color='%s'><b>%s</b></font> - %s<br>";

    public TimestampLogTextView(Context context) {
        super(context);
        super.setMovementMethod(new ScrollingMovementMethod());
    }


    public TimestampLogTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        super.setMovementMethod(new ScrollingMovementMethod());
    }

    public void log(CharSequence text) {
        String timestamp_msg = String.format(Locale.ENGLISH, LOG_FMT,
                TIME_COLOR,
                SimpleDateFormat.getTimeInstance().format(new Date()),
                text);

        super.append(Html.fromHtml(timestamp_msg));
    }
}
