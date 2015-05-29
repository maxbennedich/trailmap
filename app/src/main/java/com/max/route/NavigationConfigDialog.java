package com.max.route;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.max.main.R;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class NavigationConfigDialog extends DialogFragment {

    public interface NavigationConfigDialogListener {
        /** Arguments may be null if they should not be updated. */
        public void updateNavigationConfig(Integer lastWaypointIdx, Date startTime);

        public String getNavigationStats();
    }

    /** Where action events are delivered. */
    NavigationConfigDialogListener listener;

    private static final DateFormat HR_MIN_FORMAT = new SimpleDateFormat("HH:mm");
    private static final DateFormat HR_MIN_SEC_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final DateFormat YEAR_MONTH_DAY_HR_MIN_SEC_FORMAT = new SimpleDateFormat("yy:MM:dd:HH:mm:ss");

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (NavigationConfigDialogListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement NavigationConfigDialogListener");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();

        // Pass null as the parent view because its going in the dialog layout
        View dialogView = inflater.inflate(R.layout.dialog_navigation_config, null);

        builder.setView(dialogView)
                .setPositiveButton("Update", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface di, int id) {
                        Integer lastWaypointIdx = parseIntOrNull(R.id.lastWaypointIdx);
                        Date startTime = parseDateOrNull(R.id.startTime);
                        listener.updateNavigationConfig(lastWaypointIdx, startTime);
                    }

                    private Integer parseIntOrNull(int id) {
                        String txt = ((EditText) getDialog().findViewById(id)).getText().toString();
                        return txt.isEmpty() ? null : Integer.parseInt(txt);
                    }

                    private Date parseDateOrNull(int id) {
                        String txt = ((EditText) getDialog().findViewById(id)).getText().toString();
                        int parts = txt.split(":").length;
                        try {
                            Date time = null;
                            if (parts == 2) {
                                time = HR_MIN_FORMAT.parse(txt);
                                Date today = new Date();
                                time.setYear(today.getYear());
                                time.setMonth(today.getMonth());
                                time.setDate(today.getDate());
                            } if (parts == 3) {
                                time = HR_MIN_SEC_FORMAT.parse(txt);
                                Date today = new Date();
                                time.setYear(today.getYear());
                                time.setMonth(today.getMonth());
                                time.setDate(today.getDate());
                            } if (parts == 6) {
                                time = YEAR_MONTH_DAY_HR_MIN_SEC_FORMAT.parse(txt);
                            }
                            return time;
                        } catch (ParseException e) {
                            Log.d(getClass().getSimpleName(), "Failed to parse <" + txt + ">");
                            return null;
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int id) {
                        getDialog().cancel();
                    }
                });

        ((TextView)dialogView.findViewById(R.id.stats)).setText(listener.getNavigationStats());

        return builder.create();
    }
}
