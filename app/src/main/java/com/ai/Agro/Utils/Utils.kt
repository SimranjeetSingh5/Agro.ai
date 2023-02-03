package com.ai.Agro.Utils

import android.R
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.SnackbarLayout


class Utils {
    fun showDialog(
        context: Context?, Title: String?, Message: String?,
        PositiveButton: String?, NegativeButton: String?, listner: DialogInterface.OnClickListener?
    ): AlertDialog? {
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(context)
        alertDialogBuilder.setTitle(Title)
        alertDialogBuilder.setMessage(Message)
            .setCancelable(false)
            .setPositiveButton(PositiveButton, listner)
            .setNegativeButton(NegativeButton, null)
        alertDialogBuilder.setCancelable(false)
        alertDialogBuilder.show();
        return alertDialogBuilder.create()
    }

//    fun showDialog(
//        context: Context, Title: String?, Message: String?,
//        PositiveButton: String?, NegativeButton: String?,
//        listner: DialogInterface.OnClickListener?, playSuccessbeep: Boolean, playErrorBeep: Boolean
//    ) {
//        val rootView: View = (context as Activity).window.decorView.findViewById(R.id.content)
//            ?: return
//        val snackbar = Snackbar.make(rootView, Message!!, Snackbar.LENGTH_LONG)
//            .setDuration(2000)
//        val layout = snackbar.view as SnackbarLayout
//        val snackTextView = layout.findViewById<TextView>(R.id.snackbar_text)
//        if (playErrorBeep) {
//            AppUtils.beepForError(context)
//            snackTextView.setTextColor(Color.RED)
//            layout.setBackgroundColor(Color.BLACK)
//        } else if (playSuccessbeep) {
//            AppUtils.beepForSuccessful(context)
//            snackTextView.setTextColor(Color.WHITE)
//            layout.setBackgroundColor(Color.parseColor("#145A32"))
//        }
//        snackbar.show()
//    }
}