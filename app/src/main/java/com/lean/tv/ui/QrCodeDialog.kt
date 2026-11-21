package com.lean.tv.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.lean.tv.R
import com.lean.tv.util.QrCodes

/**
 * 电视端展示配置页二维码，手机扫码即可打开浏览器配置。
 */
class QrCodeDialog(
    activity: Activity,
    private val url: String
) : Dialog(activity) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_qr)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.72f)
        }

        val urlView = findViewById<TextView>(R.id.qr_url)
        val image = findViewById<ImageView>(R.id.qr_image)
        urlView.text = url

        val bitmap = QrCodes.encode(url)
        if (bitmap != null) {
            image.setImageBitmap(bitmap)
        } else {
            image.visibility = View.GONE
            urlView.text = context.getString(R.string.qr_unavailable, url)
        }
        setOnDismissListener { image.setImageDrawable(null) }
    }
}
