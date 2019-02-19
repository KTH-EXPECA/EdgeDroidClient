/**
 * Copyright 2019 Manuel Olguín
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.kth.molguin.edgedroid;

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
