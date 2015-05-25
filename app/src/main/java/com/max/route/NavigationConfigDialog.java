package com.max.route;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.max.main.R;

public class NavigationConfigDialog extends DialogFragment {

    public interface NavigationConfigDialogListener {
        /** Arguments may be null if they should not be updated. */
        public void updateNavigationConfig(Integer lastWaypointIdx, Integer elapsedTimeMin);

        public String getNavigationStats();
    }

    /** Where action events are delivered. */
    NavigationConfigDialogListener listener;

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
                        Integer lastWaypointIdx = parseOrNull(R.id.lastWaypointIdx);
                        Integer elapsedTimeMin = parseOrNull(R.id.elapsedTime);
                        listener.updateNavigationConfig(lastWaypointIdx, elapsedTimeMin);
                    }

                    private Integer parseOrNull(int id) {
                        String txt = ((EditText)getDialog().findViewById(id)).getText().toString();
                        return txt.isEmpty() ? null : Integer.parseInt(txt);
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
