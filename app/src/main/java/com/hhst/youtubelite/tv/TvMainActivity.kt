package com.hhst.youtubelite.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hhst.youtubelite.R
import androidx.tv.material3.Text
import com.hhst.youtubelite.tv.design.TvType

/** LEANBACK_LAUNCHER entry of MinTube TV. */
class TvMainActivity : TvActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTvContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.tv_app_name), style = TvType.Display)
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, TvMainActivity::class.java)
    }
}
